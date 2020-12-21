package(
  default_visibility = ["//visibility:public"],
)

cc_library(
  name = 'common',
  deps = [
      ":fl_gflags",
  ],
)

cc_library(
  name = 'fl_gflags',
  hdrs = [
    'fl_gflags.h',
    'util.h',
  ],
  srcs = [
    'fl_gflags.cc',
  ],
  deps = [
    '@com_github_grpc_grpc//:grpc++',
    '@com_github_grpc_grpc//:grpc_base_c',
    "@com_github_gflags_gflags//:gflags",
    "@com_google_protobuf//:protobuf",
  ],
)
