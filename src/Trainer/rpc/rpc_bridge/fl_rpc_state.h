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

#ifndef TENSORFLOW_CONTRIB_JDFL_RPC_RPC_BRIDGE_FL_RPC_STATE_H_
#define TENSORFLOW_CONTRIB_JDFL_RPC_RPC_BRIDGE_FL_RPC_STATE_H_

//
//  Modified base on "tensorflow/core/distributed_runtime/rpc/grpc_state.h".
//  Add metadata pairs, proxy require metadata forwarding.
//

#include <queue>
#include <utility>
#include <vector>
#include <memory>

#include "grpcpp/generic/generic_stub.h"
#include "grpcpp/grpcpp.h"

#include "tensorflow/core/distributed_runtime/call_options.h"
#include "tensorflow/core/distributed_runtime/rpc/grpc_client_cq_tag.h"
#include "tensorflow/core/distributed_runtime/rpc/grpc_util.h"
#include "tensorflow/core/distributed_runtime/rpc/grpc_state.h"
#include "tensorflow/core/lib/core/refcount.h"
#include "tensorflow/core/lib/core/status.h"
#include "tensorflow/core/lib/core/threadpool.h"
#include "tensorflow/core/lib/strings/strcat.h"
#include "tensorflow/core/platform/mutex.h"
#include "tensorflow/core/platform/notification.h"

namespace tensorflow {

using CallMeta = std::pair<::grpc::string, ::grpc::string>;

// Object allocated per active RPC.
// Manage the state of a single asynchronous RPC request.  If `max_retries`
// is greater than 0, the request will be retried for any transient failures
// as long as the overall deadline has not elapsed.
template <class Response>
class FlRPCState : public GrpcClientCQTag {
 public:
  // Default behavior is to set fail_fast = False and handle timeouts manually.
  FlRPCState(::grpc::GenericStub* stub, ::grpc::CompletionQueue* cq,
             const std::vector<CallMeta>& ctx_meta,
             const ::grpc::string& method, const protobuf::Message& request,
             Response* response, StatusCallback done, CallOptions* call_opts,
             thread::ThreadPool* threadpool, int32 max_retries = 0,
             bool fail_fast = false)
      : FlRPCState(stub, cq, ctx_meta, method, request, response,
                   std::move(done), call_opts, threadpool, fail_fast,
                   /*timeout_in_ms=*/0, max_retries) {}

  template <typename Request>
  FlRPCState(::grpc::GenericStub* stub, ::grpc::CompletionQueue* cq,
             const std::vector<CallMeta>& ctx_meta,
             const ::grpc::string& method, const Request& request,
             Response* response, StatusCallback done, CallOptions* call_opts,
             thread::ThreadPool* threadpool, bool fail_fast,
             int64 timeout_in_ms, int32 max_retries)
      : call_opts_(call_opts),
        threadpool_(threadpool),
        done_(std::move(done)),
        timeout_in_ms_(timeout_in_ms),
        max_retries_(max_retries),
        cq_(cq),
        stub_(stub),
        method_(method),
        fail_fast_(fail_fast),
        ctx_meta_(ctx_meta) {
    response_ = response;
    ::grpc::Status s = GrpcMaybeUnparseProto(request, &request_buf_);
    if (!s.ok()) {
      LOG(ERROR) << "GrpcMaybeUnparseProto returned with non-ok status: "
                 << s.error_message();
      // Skip retry logic if we fail to parse our request.
      done_(FromGrpcStatus(s));
      delete this;
      return;
    }
    StartCall();
  }

  void StartCall() {
    context_.reset(new ::grpc::ClientContext());
    context_->set_wait_for_ready(!fail_fast_);

    if (timeout_in_ms_ > 0) {
      context_->set_deadline(
          gpr_time_from_millis(timeout_in_ms_, GPR_TIMESPAN));
    }
    if (call_opts_) {
      call_opts_->SetCancelCallback([this]() { context_->TryCancel(); });
    }

    for (auto& kv : ctx_meta_) {
      context_->AddMetadata(kv.first, kv.second);
    }

    VLOG(2) << "Starting call: " << method_;

    call_ = std::move(
        stub_->PrepareUnaryCall(context_.get(), method_, request_buf_, cq_));
    call_->StartCall();
    call_->Finish(&response_buf_, &status_, this);
  }

  void OnCompleted(bool ok) override {
    if (call_opts_) {
      call_opts_->ClearCancelCallback();
    }

    VLOG(2) << "Completed call: " << method_;

    Status s = FromGrpcStatus(status_);
    if (s.ok() && !ok) {
      // Since this function is only being used for processing the response
      // to Finish for client-side unary calls, ok should never be false
      s.Update(errors::Internal("unexpected ok value at rpc completion"));
    }

    if (s.ok()) {
      if (threadpool_) {
        // Run parse and callback in another thread, returning this
        // one to service more RPCs.
        threadpool_->Schedule([this]() { ParseAndCallDone(); });
      } else {
        ParseAndCallDone();
      }
      return;
    }

    VLOG(1) << method_ << " returned with non-ok status: " << s
            << " Retries: " << num_retries_ << " Max: " << max_retries_ << "\n"
            << context_->debug_error_string();
    // Retry if we have any attempts left
    if (++num_retries_ <= max_retries_ &&
        (errors::IsUnavailable(s) || errors::IsUnknown(s))) {
      response_buf_.Clear();
      VLOG(1) << "Retrying call for " << method_ << "Retry: " << num_retries_
              << " of " << max_retries_;
      StartCall();
    } else {
      // Attach additional GRPC error information if any to the final status
      s = Status(s.code(),
                 strings::StrCat(s.error_message(),
                                 "\nAdditional GRPC error information:\n",
                                 context_->debug_error_string()));
      // Always treat gRPC cancellation as a derived error. This ensures that
      // other error types are preferred during status aggregation. (gRPC
      // cancellation messages do not contain the original status message).
      if (s.code() == tensorflow::error::Code::CANCELLED) {
        s = StatusGroup::MakeDerived(s);
      }

      done_(s);
      delete this;
    }
  }

  void ParseAndCallDone() {
    Status s;
    if (!GrpcMaybeParseProto(&response_buf_, response_)) {
      s.Update(errors::Internal("could not parse rpc response"));
    }
    done_(s);
    delete this;
  }

 private:
  CallOptions* call_opts_;
  std::unique_ptr<::grpc::ClientContext> context_;
  thread::ThreadPool* threadpool_;
  std::unique_ptr<::grpc::GenericClientAsyncResponseReader> call_;
  Response* response_;
  ::grpc::ByteBuffer request_buf_;
  ::grpc::ByteBuffer response_buf_;
  ::grpc::Status status_;
  StatusCallback done_;
  int64 timeout_in_ms_;

  size_t num_retries_ = 0;
  size_t max_retries_;

  ::grpc::CompletionQueue* cq_;
  ::grpc::GenericStub* stub_;
  ::grpc::string method_;
  bool fail_fast_;

  const std::vector<CallMeta> ctx_meta_;
};

template <class Response>
class FlStreamingRPCDispatcher {
 public:
  FlStreamingRPCDispatcher(::grpc::GenericStub* stub,
                           ::grpc::CompletionQueue* cq,
                           const ::grpc::string& method,
                           const std::vector<CallMeta>& ctx_meta)
      : stub_(stub), cq_(cq), method_(method), ctx_meta_(ctx_meta) {}

  // Attempts to send the next request. If there is no active streaming call,
  // starts one and sends the request on top of it. `done` is invoked when
  // `response` has been filled with the data from the server, or if there
  // is an error. `done` can be invoked before SendNextRequest returns.
  Status SendNextRequest(const protobuf::Message& request, Response* response,
                         StatusCallback done) {
    mutex_lock l(mu_);
    if (state_ == nullptr) {
      CreateStreamingState();
    }

    bool is_call_alive = state_->SendNextRequest(request, response, done);
    if (is_call_alive) {
      return Status::OK();
    } else {
      Status s = errors::Unknown("gRPC call failed right after it was created");
      // Consider retrying to create and start a call few more times.
      // done(s);
      return s;
    }

    // The attempt to send failed because the call was dead, create a new
    // call and try again. When the call is dead SendNextRequest does not call
    // `done`.
    // CreateStreamingState();

    // is_call_alive = state_->SendNextRequest(request, response, done);
    // if (!is_call_alive) {
    //  Status s = errors::Unknown("gRPC call failed right after it was
    //  created");
    //  // Consider retrying to create and start a call few more times.
    //  done(s);
    //  return s;
    //}
    //
    // return Status::OK();
  }

  void ResetCall() {
    mutex_lock l(mu_);
    state_ = nullptr;
    LOG(INFO) << "gRPC call failed, reset Streaming Client Ctx ...";
  }

  // Request to cancel the current streaming call. Non-blocking.
  void CancelCall() {
    mutex_lock l(mu_);
    if (state_ == nullptr) {
      return;
    }
    context_->TryCancel();
    state_ = nullptr;
  }

 private:
  void CreateStreamingState() EXCLUSIVE_LOCKS_REQUIRED(mu_) {
    LOG(INFO) << "Create Streaming Client Ctx ...";

    // ClientContext cannot be reused across calls.
    context_ = std::make_shared<::grpc::ClientContext>();
    // Don't immediately fail StartCall if the channel is not ready. Wait for
    // the channel to become ready.
    context_->set_wait_for_ready(true);

    for (auto& kv : ctx_meta_) {
      LOG(INFO) << "Client Ctx AddMetadata(), meta_key " << kv.first
                << ", meta_value " << kv.second;
      context_->AddMetadata(kv.first, kv.second);
    }

    std::unique_ptr<grpc::GenericClientAsyncReaderWriter> call =
        std::move(stub_->PrepareCall(context_.get(), method_, cq_));

    state_.reset(new StreamingRPCState<Response>(std::move(call), context_));
  }

  mutable mutex mu_;

  // Both are thread-safe
  ::grpc::GenericStub* const stub_;
  ::grpc::CompletionQueue* const cq_;

  // Does not need synchronization since it is constant.
  const ::grpc::string method_;

  const std::vector<CallMeta> ctx_meta_;

  std::shared_ptr<::grpc::ClientContext> context_ GUARDED_BY(mu_);
  core::RefCountPtr<StreamingRPCState<Response>> state_ GUARDED_BY(mu_);
};

}  // namespace tensorflow

#endif  // TENSORFLOW_CONTRIB_JDFL_RPC_RPC_BRIDGE_FL_RPC_STATE_H_
