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

#include "pybind11/iostream.h"
#include "pybind11/numpy.h"
#include "pybind11/pybind11.h"
#include "pybind11/stl.h"

#include "yacl/link/factory.h"

#include <exception>

namespace link_py {

namespace py = pybind11;
namespace link = yacl::link;

#define NO_GIL py::call_guard<py::gil_scoped_release>()

PYBIND11_MODULE(link_py, m) {

  py::register_exception_translator([](std::exception_ptr p) {
    try {
      if (p) {
        std::rethrow_exception(p);
      }
    } catch (const std::exception &e) {
      PyErr_SetString(PyExc_RuntimeError, e.what());
    }
  });

  py::class_<link::Context, std::shared_ptr<link::Context>>(
      m, "Context", "SecretFlow YACL Link Context")
      .def("WaitLinkTaskFinish", &link::Context::WaitLinkTaskFinish, NO_GIL)
      .def(
          "ConnectToMesh", [](link::Context &ctx) { ctx.ConnectToMesh(); },
          NO_GIL)
      .def(
          "SendAsync",
          [](link::Context &ctx, size_t dst_rank, std::string data,
             std::string_view tag) {
            yacl::Buffer buf(data.data(), data.size());
            ctx.SendAsync(dst_rank, std::move(buf), tag);
          },
          py::arg("dst_rank"), py::arg("data"), py::arg("tag") = "", NO_GIL)
      .def(
          "Send",
          [](link::Context &ctx, size_t dst_rank, std::string data,
             std::string_view tag) { ctx.Send(dst_rank, data, tag); },
          py::arg("dst_rank"), py::arg("data"), py::arg("tag") = "", NO_GIL)
      .def(
          "Recv",
          [](link::Context &ctx, size_t src_rank, std::string_view tag) {
            auto buf = ctx.Recv(src_rank, tag);
            return py::bytes(buf.data<char>(), buf.size());
          },
          py::arg("src_rank"), py::arg("tag") = "", NO_GIL)
      .def(
          "SubWorld",
          [](link::Context &ctx, std::string_view id_suffix,
             const std::vector<std::string> &sub_party_ids) {
            return std::shared_ptr<link::Context>(
                std::move(ctx.SubWorld(id_suffix, sub_party_ids)));
          },
          NO_GIL)
      .def(
          "NextRank",
          [](link::Context &ctx, size_t stride) {
            return ctx.NextRank(stride);
          },
          py::arg("stride") = 1, NO_GIL);

  m.def(
      "CreateContext",
      [](size_t rank, std::string id, std::string self_domain,
         std::string target_domain,
         std::vector<std::tuple<std::string, std::string>> parties,
         uint32_t connect_retry_times, uint32_t connect_retry_interval_ms,
         uint64_t recv_timeout_ms, uint32_t http_max_payload_size,
         uint32_t http_timeout_ms, uint32_t throttle_window_size,
         std::string brpc_channel_protocol,
         std::string brpc_channel_connection_type) {
        link::FactoryBrpc factory;
        link::ContextDesc desc;
        desc.id = id;
        for (auto &party : parties)
          desc.parties.emplace_back(
              link::ContextDesc::Party{std::get<0>(party), std::get<1>(party)});
        desc.connect_retry_times = connect_retry_times;
        desc.connect_retry_interval_ms = connect_retry_interval_ms;
        desc.recv_timeout_ms = recv_timeout_ms;
        desc.http_max_payload_size = http_max_payload_size;
        desc.http_timeout_ms = http_timeout_ms;
        desc.throttle_window_size = throttle_window_size;
        desc.brpc_channel_protocol = brpc_channel_protocol;
        desc.brpc_channel_connection_type = brpc_channel_connection_type;
        desc.controller_interceptor =
            [id, target_domain](brpc::Controller &cntl, size_t self_rank,
                                size_t peer_rank) {
              constexpr static auto ID_META = "id";
              constexpr static auto TARGET_META = "target";

              cntl.http_request().SetHeader(ID_META, id);
              cntl.http_request().SetHeader(TARGET_META, target_domain);
            };
        return factory.CreateContext(desc, rank);
      },
      py::arg("rank"), py::arg("id"), py::arg("self_domain"),
      py::arg("target_domain"), py::arg("parties"),
      py::arg("connect_retry_times") = 720,
      py::arg("connect_retry_interval_ms") = 5 * 1000,
      py::arg("recv_timeout_ms") = 3600 * 1000,
      py::arg("http_max_payload_size") = 4 * 1024 * 1024,
      py::arg("http_timeout_ms") = 20 * 1000,
      py::arg("throttle_window_size") = 10,
      py::arg("brpc_channel_protocol") = "h2:grpc",
      py::arg("brpc_channel_connection_type") = "", NO_GIL);
}

} // namespace link_py