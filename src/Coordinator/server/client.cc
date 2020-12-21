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

#include <grpcpp/grpcpp.h>
#include <gflags/gflags.h>
#include <iostream>
#include <memory>
#include <string>
#include "proto/internal_service.grpc.pb.h"

DEFINE_string(model_uri, "DefaultModelUri", "model uri");
DEFINE_string(model_version, "DefaultVersion", "model model_version");
DEFINE_string(server_ip_port, "", "server ip:port");

class Client {
 public:
  explicit Client(std::shared_ptr<grpc::Channel> channel)
      : stub_(jdfl::StartApplication::NewStub(channel)) {}

  // Assembles the client's payload, sends it and presents the response back
  // from the server.
  std::string StartApplication(const std::string& model_uri,
                               const std::string& model_version) {
    // Data we are sending to the server.
    jdfl::ModelURI request;
    request.set_model_uri(model_uri);
    request.set_version(model_version);

    // Container for the data we expect from the server.
    jdfl::Status reply;

    // Context for the client. It could be used to convey extra information to
    // the server and/or tweak certain RPC behaviors.
    grpc::ClientContext context;

    // The actual RPC.
    grpc::Status status = stub_->StartApplication(&context, request, &reply);

    // Act upon its status.
    if (status.ok()) {
      if (0 == reply.status()) {
        return "OK!";
      } else {
        return reply.err_msg();
      }
    } else {
      std::cout << status.error_code() << ": " << status.error_message()
                << std::endl;
      return "RPC failed";
    }
  }

 private:
  std::unique_ptr<jdfl::StartApplication::Stub> stub_;
};

int main(int argc, char** argv) {
  google::ParseCommandLineFlags(&argc, &argv, true);
  // Instantiate the client. It requires a channel, out of which the actual RPCs
  // are created. This channel models a connection to an endpoint (in this case,
  // localhost at port 50051). We indicate that the channel isn't authenticated
  // (use of InsecureChannelCredentials()).
  Client client(grpc::CreateChannel(
      FLAGS_server_ip_port, grpc::InsecureChannelCredentials()));
  std::string reply = client.StartApplication(
    FLAGS_model_uri, FLAGS_model_version);
  std::cout << "Client received: " << reply << std::endl;

  return 0;
}
