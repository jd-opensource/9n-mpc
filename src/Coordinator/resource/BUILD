package(
  default_visibility = ["//visibility:public"],
)

cc_library(
  name = "resource",
  hdrs = [
    'base_executor.h',
    'coordinator_executor.h',
    'resource.h',
    'hiredis_executor.h',
  ],
  srcs = [
    'resource.cc',
    'coordinator_executor.cc',
    'hiredis_executor.cc',
  ],
  deps = [
    "//common:common",
    "@com_github_google_glog//:glog",
    "@hiredis//:hiredis",
  ],
  defines = ["BAZEL_BUILD"],
)
