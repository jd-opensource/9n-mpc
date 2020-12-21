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

#include "services/invoke_module.h"

namespace jdfl {

bool SetTrainRequest(const AppInfo& app_info,
                ::fedlearner::common::TrainRequest* request) {
  if (!CheckAppInfo(app_info)) {
    LOG(ERROR) << "app_info conf error.";
    return false;
  }
  const ::jdfl::ConfInfo& conf_info = app_info.conf_info();
  request->set_app_id(conf_info.app_id());
  ::fedlearner::common::ModelTrainMeta* model_train_meta =
      request->mutable_model_train_mata();
  model_train_meta->set_model_uri(conf_info.model_uri());
  model_train_meta->set_version(conf_info.version());
  model_train_meta->set_worker_num(conf_info.worker_num());
  // model_train_mata->set_checkpoint_interval();
  ::fedlearner::common::DataMeta* data_meta = request->mutable_data_meta();
  data_meta->set_data_source_name(conf_info.data_source_name());
  data_meta->set_train_data_start(conf_info.train_data_start());
  data_meta->set_train_data_end(conf_info.train_data_end());
  data_meta->set_data_num_epoch(conf_info.data_num_epoch());
  return true;
}

grpc::Status InvokeModule::InvokeSubmitTrain() {
  ::fedlearner::common::Status reply;
  grpc::ClientContext context;
  // set request.
  ::fedlearner::common::TrainRequest request;
  const std::string& app_id = app_info_.conf_info().app_id();
  if (!SetTrainRequest(app_info_, &request)) {
    std::string err =
         "set SubmitTrain request error.";
    return common::ReturnErrorStatus(err + log_common_);
  }
  // set header uuid
  context.AddMetadata("uuid", "Scheduler");
  // remote procedure call.
  grpc::Status status =
        submit_train_stub_->SubmitTrain(&context, request, &reply);
  // process return value
  if (status.ok()) {
    LOG(INFO) << "Invole SubmitTrain successfully." << log_common_;
    if (0 != reply.status() || 0 != app_id.compare(reply.app_id())) {
      std::string err = "fail to verify ModelTrainMeta.";
      return common::ReturnErrorStatus(err + log_common_ + reply.err_msg());
    }
    // write AppInfo to redis.
    if (!SetAppInfoToRedis(app_info_)) {
      std::string err = "fail to save TrainMeta to redis.";
      return common::ReturnErrorStatus(err + log_common_);
    }
  } else {
    std::string err = "fail to invoke SubmitTrain.";
    return common::ReturnErrorStatus(
        err + log_common_ + status.error_message());
  }
  return grpc::Status::OK;
}

grpc::Status InvokeModule::InvokeSyn(grpc::ClientContext* context,
    const ::fedlearner::common::AppSynRequest& request,
    ::fedlearner::common::Status* reply) {
  // set header uuid
  context->AddMetadata("uuid", "StateSynService");
  // remote procedure call.
  grpc::Status status = syn_stub_->Syn(context, request, reply);
  if (status.ok()) {
    LOG(INFO) << "Invole Syn successfully." << log_common_;
  } else {
    LOG(ERROR) << log_common_ << "AppSynRequest: " << request.DebugString();
    std::string err = "fail to invoke Syn.";
    return common::ReturnErrorStatus(
        err + log_common_ + status.error_message());
  }
  return grpc::Status::OK;
}

}  // namespace jdfl
