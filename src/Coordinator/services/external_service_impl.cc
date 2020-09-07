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

#include <map>
#include <thread>
#include <unordered_map>
#include <unordered_set>
#include "src/core/lib/json/json.h"
#include "services/invoke_module.h"
#include "services/check_app_status.h"
#include "services/external_service_impl.h"
#include "common/fl_gflags.h"
#include "common/util.h"
#include "google/protobuf/util/json_util.h"

namespace fl {

grpc::Status SchedulerServiceImpl::SubmitTrain(
    grpc::ServerContext* context,
    const ::fedlearner::common::TrainRequest* request,
    ::fedlearner::common::Status* response) {
  const std::string &app_id = request->app_id();
  const std::string &model_uri = request->model_train_mata().model_uri();
  const std::string &version = request->model_train_mata().version();
  std::string err;
  do {
    // get model from redis
    ::jdfl::AppInfo app_info;
    if (!GetJsonConfFromRedis(model_uri, version, &app_info)) {
      err.assign("parse model conf fail! model uri ").append(model_uri);
      err.assign(" version: ").append(version);
      break;
    }
    // check model info
    if (!CheckAppInfo(app_info)) {
      err.assign("check model info fail!");
      break;
    }
    // check trainer num
    if (request->model_train_mata().worker_num() !=
        app_info.conf_info().worker_num()) {
      std::string local_num = std::to_string(
        app_info.conf_info().worker_num());
      std::string remote_num = std::to_string(
        request->model_train_mata().worker_num());
      err.assign("check trainer number fail! local number: ").append(
          local_num).append(" ,remote number: ").append(remote_num);
      break;
    }
    // set app_id
    app_info.mutable_conf_info()->set_app_id(app_id);
    if (!SetAppInfoToRedis(app_info)) {
      err.assign("set model conf info fail! app_id is : ").append(app_id);
      break;
    }
    // start k8s
    if (!StartK8SFromAppInfo(app_info)) {
      err.assign("Start k8s fail! app id is : ").append(app_id);
      break;
    }
    jdfl::CheckAppStatus::Instance()->AddAppId(app_id);
  } while (false);
  // build err response
  response->set_app_id(app_id);
  if (!err.empty()) {
    response->set_status(1);
    response->set_err_msg(err);
    return common::ReturnErrorStatus(err);
  }
  // build succ response
  response->set_status(0);
  return grpc::Status::OK;
}

// make pair
grpc::Status StateSynServiceImpl::Syn(
    grpc::ServerContext* context,
    const ::fedlearner::common::AppSynRequest* request,
    ::fedlearner::common::Status* response) {
  // judge client or server
  const auto &app_id = request->app_id();
  // judge if stop this train
  const auto &ctrl_flag = request->ctrl_flag();
  std::string err;
  ::jdfl::AppInfo app_info;
  if (!GetAppInfoFromRedis(app_id, &app_info)) {
    err.assign("get redis fail, app_id is : ").append(app_id);
    response->set_status(1);
    response->set_err_msg(err);
    return common::ReturnErrorStatus(err);
  }
  if (ctrl_flag == ::fedlearner::common::SHUTDOWN) {
    // kill all trainer info
    // stop k8s
    app_info.mutable_conf_info()->set_remote_status(::jdfl::SHUTDOWN);
    SetAppInfoToRedis(app_info);
    if (!jdfl::StopK8S(app_id)) {
      LOG(ERROR) << "stop k8s fail! app_id : " << app_id;
    }
    response->set_status(0);
    return grpc::Status::OK;
  }
  if (ctrl_flag == ::fedlearner::common::FINISH) {
    app_info.mutable_conf_info()->set_remote_status(::jdfl::FINISH);
    SetAppInfoToRedis(app_info);
    response->set_status(0);
    return grpc::Status::OK;
  }
  // judge client or server
  const auto &role = app_info.conf_info().role();
  // follower or server
  if (role == ::jdfl::ConfInfo::FOLLOWER) {
    // start thread to wait for register
    TaskInfo *info = new TaskInfo();
    info->request.CopyFrom(*request);
    info->app_info.Swap(&app_info);
    std::thread th(WaitForServiceRegistered, info);
    th.detach();
  } else {  // leader or client
    int32_t total_num = 0;
    for (int32_t i = 0; i < app_info.pair_infos_size(); i++) {
      const jdfl::PairInfo& pair_info = app_info.pair_infos(i);
      total_num += pair_info.service_pair_size();
    }
    if (request->service_pair_size() != total_num) {
      std::string err = "uuid num not equal. app_id: " + app_id;
      response->set_status(1);
      response->set_err_msg(err);
      return common::ReturnErrorStatus(err);
    }
    std::unordered_map<std::string, std::string> uuid_pair_map;
    for (const auto &service : request->service_pair()) {
      // leader is local for client
      uuid_pair_map.insert({service.leader_uuid(), service.follower_uuid()});
    }
    for (int32_t j = 0; j < app_info.pair_infos_size(); j++) {
      ::jdfl::PairInfo* ptr_pair_info = app_info.mutable_pair_infos(j);
      for (int32_t k = 0; k < ptr_pair_info->service_pair_size(); k++) {
        ::jdfl::ServicePair* ptr_service_pair =
                                 ptr_pair_info->mutable_service_pair(k);
        const std::string& local_uuid = ptr_service_pair->local_uuid();
        auto iter = uuid_pair_map.find(local_uuid);
        if (iter == uuid_pair_map.end()) {
          std::string err = "fail to find leader_uuid '" + iter->first +
                            "' in AppInfo.";
          response->set_status(1);
          response->set_err_msg(err);
          return common::ReturnErrorStatus(err);
        }
        ptr_service_pair->set_remote_uuid(iter->second);
      }
    }
    // save AppInfo to redis
    int index = 0;
    auto redis_ptr = resource::Resource::Instance()->redis_resource();
    while (index++ < FLAGS_lock_times) {
      if (!redis_ptr->Lock(app_id, FLAGS_lock_timeout_s)) {
        std::this_thread::sleep_for(std::chrono::milliseconds(10 * 1000));
        continue;
      }
      break;
    }
    if (index >= FLAGS_lock_times) {
      std::string err = "client, lock redis fail! app_id : " + app_id;
      return common::ReturnErrorStatus(err);
    }
    if (!SetAppInfoToRedis(app_info)) {
      std::string err = "client, set redis fail! app_id : " + app_id;
      return common::ReturnErrorStatus(err);
    }
    // unlock
    redis_ptr->Unlock(app_id);
  }
  // build success response
  response->set_status(0);
  return grpc::Status::OK;
}

bool MakeServicePairInfo(TaskInfo *task_ptr) {
  assert(task_ptr);
  // build request map
  LOG(INFO) << "make pair, remote req: " << task_ptr->request.Utf8DebugString();
  ::fedlearner::common::AppSynRequest new_request;
  for (int i = 0; i < task_ptr->app_info.pair_infos_size(); ++i) {
    auto pair_info = task_ptr->app_info.mutable_pair_infos(i);
    for (int j = 0; j < pair_info->service_pair_size(); ++j) {
      auto service = pair_info->mutable_service_pair(j);
      service->set_remote_uuid(service->local_uuid());
      auto new_req_service = new_request.add_service_pair();
      new_req_service->set_leader_uuid(service->remote_uuid());
      new_req_service->set_follower_uuid(service->local_uuid());
      new_req_service->set_ctrl_flag(::fedlearner::common::SYN);
    }
  }
  task_ptr->request.mutable_service_pair()->
      Swap(new_request.mutable_service_pair());
  // make pair done
  LOG(INFO) << "thread, make pair done!";
  return true;
}

void StateSynServiceImpl::WaitForServiceRegistered(TaskInfo *task) {
  assert(task);
  std::unique_ptr<TaskInfo> task_ptr(task);
  const auto &app_id = task_ptr->request.app_id();
  while (true) {
    int status = CheckRegisterNum(app_id, &(task->app_info));
    if (status == -2) {
      return;
    }
    if (-1 == status) {
      std::this_thread::sleep_for(
        std::chrono::milliseconds(FLAGS_wait_registered_time_ms));
      continue;
    }
    if (!MakeServicePairInfo(task_ptr.get())) {
      LOG(ERROR) << "thread, check pair info fail!";
      return;
    }
    // done
    break;
  }
  // set redis
  auto redis_ptr = resource::Resource::Instance()->redis_resource();
  int index = 0;
  while (index++ < FLAGS_lock_times) {
    if (!redis_ptr->Lock(app_id, FLAGS_lock_timeout_s)) {
      std::this_thread::sleep_for(std::chrono::milliseconds(10 * 1000));
      continue;
    }
    break;
  }
  if (index >= FLAGS_lock_times) {
    LOG(ERROR) << "thread, lock redis fail! app_id : " << app_id;
    return;
  }
  if (!SetAppInfoToRedis(task_ptr->app_info)) {
    LOG(ERROR) << "thread, set redis fail! app_id : " << app_id;
    return;
  }
  // unlock
  redis_ptr->Unlock(app_id);

  // build request
  auto channel = resource::Resource::Instance()->coordinator_channel();
  std::unique_ptr<::fedlearner::common::StateSynService::Stub> stub =
      ::fedlearner::common::StateSynService::NewStub(channel);
  grpc::ClientContext context;
  ::fedlearner::common::Status response;
  task_ptr->request.set_ctrl_flag(::fedlearner::common::CREATE);
  // set header uuid
  context.AddMetadata("uuid", "StateSynService");
  grpc::Status status = stub->Syn(&context, task_ptr->request, &response);
  if (!status.ok()) {
    LOG(ERROR) << "thread, get syn response fail! err: " << response.err_msg();
    return;
  }
  LOG(INFO) << "thread, pair successful! app_id : " << app_id;
  return;
}

}  // namespace fl
