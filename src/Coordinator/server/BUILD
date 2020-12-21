package(default_visibility = ["//visibility:public"],)

cc_binary(
  name = "fl_server",
  srcs = [
    "main.cc",
  ],
  deps = [
    "//services:services",
    '//resource:resource',
  ],
  defines = ["BAZEL_BUILD"],
  linkstatic=1,
  linkopts = [
  ],
)
cc_binary(
  name = "fl_client",
  srcs = [
    "client.cc",
  ],
  deps = [
    "//services:services",
    '//resource:resource',
  ],
  defines = ["BAZEL_BUILD"],
  linkstatic=1,
  linkopts = [
  ],
)
