# Description:
# An example for custom op and kernel defined as a TensorFlow plugin.

package(
    default_visibility = ["//tensorflow:internal"],
)

licenses(["notice"])  # Apache 2.0

exports_files(["LICENSE"])

load("//tensorflow:tensorflow.bzl", "tf_custom_op_library")
load("//tensorflow:tensorflow.bzl", "tf_py_test")
load("//tensorflow:tensorflow.bzl", "tf_cc_test")
load(
    "//tensorflow/core/platform:default/build_config.bzl",
    "tf_proto_library",
    "cc_proto_library",
    "tf_proto_library_cc",
    "tf_kernel_tests_linkstatic",
)

cc_library(
    name = "jdfl_hdrs",
    srcs = [
      '//tensorflow/core/kernels/data:name_utils.h',
      '//tensorflow/core/kernels/data:dataset_utils.h',
      '//tensorflow/core/kernels/data:captured_function.h',
      '//tensorflow/core/kernels/data:stats_utils.h',
      "//tensorflow/core/distributed_runtime:call_options.h",
      "//tensorflow/core/distributed_runtime/rpc:async_service_interface.h",
      "//tensorflow/core/distributed_runtime/rpc:grpc_channel.h",
      "//tensorflow/core/distributed_runtime/rpc:grpc_call.h",
      "//tensorflow/core/distributed_runtime/rpc:grpc_util.h",
      "//tensorflow/core/distributed_runtime/rpc:grpc_state.h",
      "//tensorflow/core/distributed_runtime/rpc:grpc_client_cq_tag.h",
    ],
    hdrs = glob([
        #"//tensorflow/core/**/*.h",
    ]),
    visibility = ["//visibility:public"],
)

tf_proto_library(
    name = "jdfl_rpc_proto",
    srcs = [
        "rpc/proto/bridge_agent.proto",
        "rpc/proto/dc_agent.proto",
    ],
    protodeps = [ "//tensorflow/core:protos_all" ],
    cc_api_version = 2,
    has_services = 1,
    cc_grpc_version = 1,
    visibility = ["//visibility:public"],
)

cc_library(
    name = "jdfl_kernel_hdrs",
    hdrs = glob([
        "kernels/**/*.h",
        "ops/**/*.h",
        "rpc/**/*.h",
    ]),
)

cc_library(
    name = "dataset_kernels",
    srcs = glob([
        "kernels/**/*.cc",
        "rpc/**/*.cc",
    ]),
    deps = [
        ":jdfl_rpc_proto_cc",
        ":jdfl_kernel_hdrs",
        ":jdfl_hdrs",
        "//tensorflow/core:framework_headers_lib",
        "//tensorflow/core/distributed_runtime/rpc:async_service_interface",
        "//tensorflow/core/distributed_runtime:tensor_coding",
        "@com_google_absl//absl/base",
        "@com_google_absl//absl/strings",
        "@com_google_absl//absl/types:span",
        "@com_google_absl//absl/algorithm:container",
        "@grpc//:grpc++",
    ],
    alwayslink = 1,
)

tf_custom_op_library(
    name = "_fl_ops.so",
    srcs = glob([
      "ops/**/*.cc",
    ]),
    deps = [
        ":dataset_kernels",
    ],
)


py_library(
    name = "all_proto_py",
    srcs_version = "PY2AND3",
    deps = [
        ":jdfl_rpc_proto_py",
    ],
)

filegroup(
    name = "all_files",
    srcs = glob(
        ["**/*"],
        exclude = [
            "**/METADATA",
            "**/OWNERS",
        ],
    ),
    visibility = ["//tensorflow:__subpackages__"],
)

