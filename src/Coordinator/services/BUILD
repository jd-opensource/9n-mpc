package(default_visibility = ["//visibility:public"],)

cc_library(  
  name = "services",
  hdrs = [
    "common.h",
    "invoke_module.h",
    "check_app_status.h",
    "internal_service_impl.h",
    "external_service_impl.h",
  ],
  srcs = [
    "common.cc",
    "invoke_module.cc",
    "check_app_status.cc",
    "internal_service_impl.cc",
    "external_service_impl.cc",
  ],
  deps = [
    "//common:common",
    "//resource:resource",
    "@com_github_gflags_gflags//:gflags",
    "@com_github_google_glog//:glog",
    "@com_github_grpc_grpc//:grpc++",
    "//proto:service_cc_grpc",
  ],
  defines = ["BAZEL_BUILD"],
)
