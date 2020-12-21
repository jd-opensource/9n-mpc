// Copyright 2020 The 9nFL Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <unistd.h>
#include <sys/time.h>
#include <vector>
#include <thread>
#include "services/invoke_module.h"
#include "services/check_app_status.h"
#include "services/internal_service_impl.h"
#include "common/fl_gflags.h"
#include "common/util.h"

namespace jdfl {

void SetSynRequest(const ::jdfl::AppInfo& app_info,
                   ::fedlearner::common::AppSynRequest* request) {
  request->set_app_id(app_info.conf_info().app_id());
  request->set_ctrl_flag(::fedlearner::common::CREATE);
  for (int32_t i = 0; i < app_info.pair_infos_size(); i++) {
    const ::jdfl::PairInfo& pair_info = app_info.pair_infos(i);
    for (int32_t j = 0; j < pair_info.service_pair_size(); j++) {
      const ::jdfl::ServicePair& service_pair = pair_info.service_pair(j);
      auto ptr_service_pair = request->add_service_pair();
      ptr_service_pair->set_leader_uuid(service_pair.local_uuid());
      ptr_service_pair->set_ctrl_flag(
        ::fedlearner::common::ServiceCtrlFlag::SYN);
    }
  }
}

bool SendUUIDToFollower(const ::jdfl::AppInfo& app_info,
                        ::jdfl::InvokeModule* invoke_module) {
  ::fedlearner::common::Status reply;
  grpc::ClientContext context;
  ::fedlearner::common::AppSynRequest request;
  // construct request
  SetSynRequest(app_info, &request);
  grpc::Status status = invoke_module->InvokeSyn(&context, request, &reply);
  if (!status.ok()) {
    LOG(ERROR) << status.error_message();
    return false;
  }
  if (0 != reply.status()) {
    LOG(ERROR) << "fail to send local uuid to remote." << reply.err_msg();
    return false;
  }
  return true;
}

grpc::Status StartApplicationImpl::StartApplication(
                                   grpc::ServerContext* context,
                                   const ::jdfl::ModelURI* request,
                                   ::jdfl::Status* response) {
  const std::string& model_uri = request->model_uri();
  const std::string& version = request->version();
  ::jdfl::AppInfo app_info;
  if (!GetJsonConfFromRedis(model_uri, version, &app_info)) {
    std::string err = "model_uri conf error. model_uri: " +
                      model_uri + " version: " + version;
    return common::ReturnErrorStatus(err);
  }
  // Generate app_id and model version
  struct timeval tv;
  gettimeofday(&tv, NULL);
  std::string app_id("flapp-jd-");
  app_id = app_id + model_uri + "-"+ version +
           "-" + std::to_string(tv.tv_sec);
  // std::string app_id("jdfl");
  ::jdfl::ConfInfo* conf_info = app_info.mutable_conf_info();
  conf_info->set_app_id(app_id);
  conf_info->set_role(jdfl::ConfInfo::LEADER);
  // schedule remote application.
  jdfl::InvokeModule invoke_module(app_info);
  grpc::Status status = invoke_module.InvokeSubmitTrain();
  if (status.ok()) {
    LOG(INFO) << "Both parties have completed verification.";
  } else {
    LOG(ERROR) << "fail to start Application.";
    return status;
  }
  // pull up datawork and trainer.
  if (!StartK8S(app_id)) {
    std::string err = "fail to start k8s. app_id: " + app_id +
                      " model_uri: " + model_uri;
    return common::ReturnErrorStatus(err);
  }
  jdfl::CheckAppStatus::Instance()->AddAppId(app_id);
  // polling check register info.
  do {
    int status = CheckRegisterNum(app_id, &app_info);
    if (status == -2) {
      std::string err =
        "fail to get app_id from redis or finish or shutdown. app_id: "
        + app_id;
      return common::ReturnErrorStatus(err);
    }
    if (-1 == status) {
      std::this_thread::sleep_for(
          std::chrono::milliseconds(FLAGS_wait_registered_time_ms));
      continue;
    }
    if (!SendUUIDToFollower(app_info, &invoke_module)) {
      std::string err = "fail to send UUID. app_id: " + app_id;
      return common::ReturnErrorStatus(err);
    }
    break;
  } while (true);
  return grpc::Status::OK;
}

grpc::Status InternalServiceImpl::RegisterUUID(
                                  grpc::ServerContext* context,
                                  const ::jdfl::Request *request,
                                  ::jdfl::Status* response) {
  if (request->uuid_size() == 0) {
    return common::ReturnErrorStatus("uuid is empty");
  }
  const std::string &app_id = common::GetAppIdFromUUID(request->uuid(0));
  auto redis_ptr = resource::Resource::Instance()->redis_resource();
  // use distribute lock
  std::string err;
  bool has_locked = false;
  do {
    // first , key value
    std::vector<std::string> keys;
    std::vector<std::string> values(request->uuid_size(), request->ip_port());
    for (const auto &key : request->uuid()) {
      keys.push_back(key);
    }
    if (!redis_ptr->Mset(keys, values)) {
      err.assign("set uuid fail! ip : ").append(request->ip_port());
      break;
    }
    // second, internal pb info
    int index = 0;
    while (index++ < FLAGS_lock_times) {
      if (!redis_ptr->Lock(app_id, FLAGS_lock_timeout_s)) {
        usleep(100 * 1000);
        continue;
      }
      break;
    }
    if (index >= FLAGS_lock_times) {
      err.assign("lock fail for many times! app_id : ").append(app_id);
      break;
    }
    has_locked = true;
    ::jdfl::AppInfo app_info;
    if (!GetAppInfoFromRedis(app_id, &app_info)) {
      err.assign("get app fail! app_id: ").append(app_id);
      break;
    }
    // check service, if has this uuid, replace (for restart ip)
    if (!ReplaceUUIDForNewIp(*request, &app_info)) {
      // not in redis
      auto pair_info = app_info.add_pair_infos();
      pair_info->set_ip_port(request->ip_port());
      for (const auto &key : request->uuid()) {
        pair_info->add_service_pair()->set_local_uuid(key);
      }
    }
    // set redis
    if (!SetAppInfoToRedis(app_info)) {
      err.assign("set app info fail! app_id : ").append(app_id);
      break;
    }
  } while (false);
  // unlock
  if (has_locked) {
    redis_ptr->Unlock(app_id);
  }
  // return response
  if (!err.empty()) {
    response->set_status(1);
    response->set_err_msg(err);
    return common::ReturnErrorStatus(err);
  }
  response->set_status(0);
  return grpc::Status::OK;
}

// return true means : redis has same uuid for different ip, need to replace it
bool InternalServiceImpl::ReplaceUUIDForNewIp(
    const ::jdfl::Request &request, ::jdfl::AppInfo *app_info) {
  assert(app_info);
  for (int i = 0; i < app_info->pair_infos_size(); ++i) {
    auto pair_info = app_info->mutable_pair_infos(i);
    for (int j = 0; j < pair_info->service_pair_size(); ++j) {
      const auto &service_info = pair_info->service_pair(j);
      const std::string &local_uuid = service_info.local_uuid();
      for (const auto &uuid : request.uuid()) {
        if (uuid == local_uuid) {
          LOG(INFO) << "replace uuid, uuid is : " << uuid
                    << " old ip : " << pair_info->ip_port()
                    << " new ip : " << request.ip_port();
          pair_info->set_ip_port(request.ip_port());
          return true;
        }
      }
    }
  }
  return false;
}

grpc::Status InternalServiceImpl::GetPairInfo(
                                  grpc::ServerContext* context,
                                  const ::jdfl::Request* request,
                                  ::jdfl::PairInfoResponse* response) {
  if (request->uuid_size() == 0) {
    return common::ReturnErrorStatus("uuid is empty");
  }
  const std::string &app_id = common::GetAppIdFromUUID(request->uuid(0));
  std::string err;
  do {
    ::jdfl::AppInfo app_info;
    if (!GetAppInfoFromRedis(app_id, &app_info)) {
      err.assign("get app fail! app_id: ").append(app_id);
      break;
    }
    const std::string &ip_port = request->ip_port();
    for (const auto & pair_info : app_info.pair_infos()) {
      if (ip_port == pair_info.ip_port()) {
        response->mutable_service_pair()->MergeFrom(pair_info.service_pair());
        break;
      }
    }
  } while (false);
  if (response->service_pair_size() == 0) {
    err.assign("lack of pair uuid info, app_id : ").append(app_id);
  } else {
    for (const auto &service_pair : response->service_pair()) {
      if (service_pair.remote_uuid().empty()) {
        err.assign("lack of remote uuid pair, app_id : ").append(app_id);
        break;
      }
    }
  }
  // return response
  if (!err.empty()) {
    response->mutable_status()->set_status(1);
    response->mutable_status()->set_err_msg(err);
    return common::ReturnErrorStatus(err);
  }
  response->mutable_status()->set_status(0);
  return grpc::Status::OK;
}
}  // namespace jdfl
