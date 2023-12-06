# -*- coding: utf-8 -*-
# Generated by the protocol buffer compiler.  DO NOT EDIT!
# source: interconnection_psi/interconnection/handshake/entry.proto
"""Generated protocol buffer code."""
from google.protobuf import descriptor as _descriptor
from google.protobuf import descriptor_pool as _descriptor_pool
from google.protobuf import symbol_database as _symbol_database
from google.protobuf.internal import builder as _builder
# @@protoc_insertion_point(imports)

_sym_db = _symbol_database.Default()


from google.protobuf import any_pb2 as google_dot_protobuf_dot_any__pb2
from interconnection_psi.interconnection.common import header_pb2 as interconnection__psi_dot_interconnection_dot_common_dot_header__pb2


DESCRIPTOR = _descriptor_pool.Default().AddSerializedFile(b'\n9interconnection_psi/interconnection/handshake/entry.proto\x12\x16org.interconnection.v2\x1a\x19google/protobuf/any.proto\x1a\x37interconnection_psi/interconnection/common/header.proto\".\n\x1bHandshakeVersionCheckHelper\x12\x0f\n\x07version\x18\x01 \x01(\x05\"\xae\x02\n\x10HandshakeRequest\x12\x0f\n\x07version\x18\x01 \x01(\x05\x12\x16\n\x0erequester_rank\x18\x02 \x01(\x05\x12\x17\n\x0fsupported_algos\x18\x03 \x03(\x05\x12)\n\x0b\x61lgo_params\x18\x04 \x03(\x0b\x32\x14.google.protobuf.Any\x12\x0b\n\x03ops\x18\x05 \x03(\x05\x12\'\n\top_params\x18\x06 \x03(\x0b\x32\x14.google.protobuf.Any\x12\x19\n\x11protocol_families\x18\x07 \x03(\x05\x12\x34\n\x16protocol_family_params\x18\x08 \x03(\x0b\x32\x14.google.protobuf.Any\x12&\n\x08io_param\x18\t \x01(\x0b\x32\x14.google.protobuf.Any\"\xaf\x02\n\x11HandshakeResponse\x12\x33\n\x06header\x18\x01 \x01(\x0b\x32#.org.interconnection.ResponseHeader\x12\x0c\n\x04\x61lgo\x18\x02 \x01(\x05\x12(\n\nalgo_param\x18\x03 \x01(\x0b\x32\x14.google.protobuf.Any\x12\x0b\n\x03ops\x18\x04 \x03(\x05\x12\'\n\top_params\x18\x05 \x03(\x0b\x32\x14.google.protobuf.Any\x12\x19\n\x11protocol_families\x18\x06 \x03(\x05\x12\x34\n\x16protocol_family_params\x18\x07 \x03(\x0b\x32\x14.google.protobuf.Any\x12&\n\x08io_param\x18\x08 \x01(\x0b\x32\x14.google.protobuf.Any*e\n\x08\x41lgoType\x12\x19\n\x15\x41LGO_TYPE_UNSPECIFIED\x10\x00\x12\x16\n\x12\x41LGO_TYPE_ECDH_PSI\x10\x01\x12\x13\n\x0f\x41LGO_TYPE_SS_LR\x10\x02\x12\x11\n\rALGO_TYPE_SGB\x10\x03*6\n\x06OpType\x12\x17\n\x13OP_TYPE_UNSPECIFIED\x10\x00\x12\x13\n\x0fOP_TYPE_SIGMOID\x10\x01*{\n\x0eProtocolFamily\x12\x1f\n\x1bPROTOCOL_FAMILY_UNSPECIFIED\x10\x00\x12\x17\n\x13PROTOCOL_FAMILY_ECC\x10\x01\x12\x16\n\x12PROTOCOL_FAMILY_SS\x10\x02\x12\x17\n\x13PROTOCOL_FAMILY_PHE\x10\x03\x62\x06proto3')

_globals = globals()
_builder.BuildMessageAndEnumDescriptors(DESCRIPTOR, _globals)
_builder.BuildTopDescriptorsAndMessages(DESCRIPTOR, 'interconnection_psi.interconnection.handshake.entry_pb2', _globals)
if _descriptor._USE_C_DESCRIPTORS == False:
  DESCRIPTOR._options = None
  _globals['_ALGOTYPE']._serialized_start=828
  _globals['_ALGOTYPE']._serialized_end=929
  _globals['_OPTYPE']._serialized_start=931
  _globals['_OPTYPE']._serialized_end=985
  _globals['_PROTOCOLFAMILY']._serialized_start=987
  _globals['_PROTOCOLFAMILY']._serialized_end=1110
  _globals['_HANDSHAKEVERSIONCHECKHELPER']._serialized_start=169
  _globals['_HANDSHAKEVERSIONCHECKHELPER']._serialized_end=215
  _globals['_HANDSHAKEREQUEST']._serialized_start=218
  _globals['_HANDSHAKEREQUEST']._serialized_end=520
  _globals['_HANDSHAKERESPONSE']._serialized_start=523
  _globals['_HANDSHAKERESPONSE']._serialized_end=826
# @@protoc_insertion_point(module_scope)