# Copyright 2020 The 9nFL Authors. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from .interconnection.common import header_pb2
from .interconnection.handshake import entry_pb2
from .interconnection.handshake.algos import psi_pb2
from .interconnection.handshake.protocol_family import ecc_pb2

from typing import Any, List
import crypto
import os
from .base import raise_exception, intersection, unpack_params, pack_params, int_to_proto_enum_name, crypto_func, SENDER_RANK
from google.protobuf.any_pb2 import Any
from .log import logger


class Context():
    def __init__(self, rank, item_num, result_to_rank, domain_list) -> None:
        self.version = 1

        self.protocol_families: List[int] = [
            entry_pb2.ProtocolFamily.PROTOCOL_FAMILY_ECC]
        self.protocol_family_params: List[Any] = [
            ecc_pb2.EccProtocolProposal(
                supported_versions=[1],
                ec_suits=[
                    ecc_pb2.EcSuit(curve=ecc_pb2.CurveType.CURVE_TYPE_CURVE25519,
                                   hash=ecc_pb2.HashType.HASH_TYPE_SHAKE_256,
                                   hash2curve_strategy=ecc_pb2.HashToCurveStrategy.HASH_TO_CURVE_STRATEGY_DIRECT_HASH_AS_POINT_X,
                                   )
                ],
                point_octet_formats=[
                    ecc_pb2.PointOctetFormat.POINT_OCTET_FORMAT_UNCOMPRESSED],
                support_point_truncation=False,
            )
        ]

        self.supported_algos: List[int] = [
            entry_pb2.AlgoType.ALGO_TYPE_ECDH_PSI]
        self.supported_versions: List[int] = [1]

        self.need_recv_cipher = False
        self.need_send_cipher = False

        self.rank = rank
        self.item_num = item_num
        self.result_to_rank = result_to_rank
        self.domain_list = domain_list

    def make_handshake_request(self) -> entry_pb2.HandshakeRequest:
        protocol_family_params = []
        for param in self.protocol_family_params:
            t = Any()
            t.Pack(param)
            protocol_family_params.append(t)

        self.io_param = psi_pb2.PsiDataInfoProposal(supported_versions=self.supported_versions,
                                                    item_num=self.item_num,
                                                    result_to_rank=self.result_to_rank)
        io_param = Any()
        io_param.Pack(self.io_param)

        req = entry_pb2.HandshakeRequest(
            version=self.version,
            requester_rank=self.rank,
            supported_algos=self.supported_algos,
            protocol_families=self.protocol_families,
            protocol_family_params=protocol_family_params,
            io_param=io_param,
        )

        return req

    def reset_from_response(self, rsp: entry_pb2.HandshakeResponse):
        ecc_param = unpack_params(
            rsp, ["protocol_family_params"], ecc_pb2.EccProtocolResult, list)
        self.ecc_param = ecc_param

        self.key = os.urandom(32)

        self.hash_to_curve_func = crypto_func(
            self.ecc_param.ec_suit.hash2curve_strategy, ecc_pb2.HashToCurveStrategy, crypto.hash_to_curve)
        self.hash_func = crypto_func(
            self.ecc_param.ec_suit.hash, ecc_pb2.HashType, crypto.hash)
        self.curve = crypto.Curve(int_to_proto_enum_name(
            self.ecc_param.ec_suit.curve, ecc_pb2.CurveType), self.key)
        self.point_octet_marshal = crypto_func(
            self.ecc_param.point_octet_format, ecc_pb2.PointOctetFormat, crypto.point_octet_marshal)
        self.point_octet_unmarshal = crypto_func(
            self.ecc_param.point_octet_format, ecc_pb2.PointOctetFormat, crypto.point_octet_unmarshal)

        def hash_and_encrypt(array):
            array = self.hash_func(array)
            array = self.hash_to_curve_func(array)
            return self.curve.encrypt(array)
        self.hash_and_encrypt = hash_and_encrypt

        if self.result_to_rank == -1:
            self.need_recv_cipher = True
            self.need_send_cipher = True
        else:
            if self.rank == SENDER_RANK:
                self.need_send_cipher = True
            else:
                self.need_recv_cipher = True

        logger.info(f"rank={self.rank} item_num={self.item_num} "
                    f"result_to_rank={self.result_to_rank} "
                    f"need_send_cipher={self.need_send_cipher} "
                    f"need_recv_cipher={self.need_recv_cipher} "
                    )

    def negotiate_handshake(self, req: entry_pb2.HandshakeRequest) -> entry_pb2.HandshakeResponse:
        reqs = [self.make_handshake_request(), req]
        rsp = entry_pb2.HandshakeResponse()

        if intersection(reqs, ["version"], int) is False:
            raise_exception(
                header_pb2.ErrorCode.UNSUPPORTED_VERSION, "", reqs, ["version"])

        supported_algos = intersection(reqs, ["supported_algos"], list)
        if entry_pb2.AlgoType.ALGO_TYPE_ECDH_PSI not in supported_algos:
            raise_exception(header_pb2.ErrorCode.UNSUPPORTED_ALGO,
                            "algo not support ALGO_TYPE_ECDH_PSI",
                            reqs, ["supported_algos"])
        algo = entry_pb2.AlgoType.ALGO_TYPE_ECDH_PSI

        io_param = unpack_params(
            reqs, ["io_param"], psi_pb2.PsiDataInfoProposal)
        supported_versions = intersection(
            io_param, ["supported_versions"], list)
        if len(supported_versions) == 0:
            raise_exception(header_pb2.ErrorCode.UNSUPPORTED_VERSION, "",
                            io_param, ["supported_versions"])
        algo_version = supported_versions[0]
        if intersection(io_param, ["result_to_rank"], int) is False:
            raise_exception(header_pb2.ErrorCode.UNSUPPORTED_PARAMS, "",
                            io_param, ["result_to_rank"])

        protocol_families = intersection(reqs, ["protocol_families"], list)
        if entry_pb2.ProtocolFamily.PROTOCOL_FAMILY_ECC not in protocol_families:
            raise_exception(
                header_pb2.ErrorCode.UNSUPPORTED_PARAMS,
                "cannot negotiate an protocol_families ",
                reqs, ["protocol_families"])
        protocol_families = [entry_pb2.ProtocolFamily.PROTOCOL_FAMILY_ECC]

        protocol_family_params = unpack_params(
            reqs, ["protocol_family_params"], ecc_pb2.EccProtocolProposal, list)
        if len(protocol_family_params) == 0:
            raise_exception(
                header_pb2.ErrorCode.UNSUPPORTED_PARAMS,
                "cannot negotiate an protocol_family_params ",
                reqs, ["protocol_family_params"])

        supported_versions = intersection(
            protocol_family_params, ["supported_versions"], list)
        if len(supported_versions) == 0:
            raise_exception(header_pb2.ErrorCode.UNSUPPORTED_VERSION, "",
                            protocol_family_params, ["supported_versions"])
        ecc_versions = supported_versions[0]

        ec_suits = intersection(
            protocol_family_params, ["ec_suits"], list)
        if len(ec_suits) == 0:
            raise_exception(
                header_pb2.ErrorCode.UNSUPPORTED_PARAMS,
                "cannot negotiate an ec_suits ",
                protocol_family_params, ["ec_suits"])

        point_octet_formats = intersection(
            protocol_family_params, ["point_octet_formats"], list)
        if len(point_octet_formats) == 0:
            raise_exception(
                header_pb2.ErrorCode.UNSUPPORTED_PARAMS,
                "cannot negotiate an point_octet_formats ",
                protocol_family_params, ["point_octet_formats"])

        support_point_truncation = intersection(
            protocol_family_params, ["support_point_truncation"], bool)
        if support_point_truncation is True:
            raise_exception(
                header_pb2.ErrorCode.UNSUPPORTED_PARAMS, "not support point_truncation")

        rsp = entry_pb2.HandshakeResponse(
            algo=algo,
            protocol_families=protocol_families,
            protocol_family_params=pack_params([
                ecc_pb2.EccProtocolResult(
                    version=ecc_versions,
                    ec_suit=ec_suits[0],
                    point_octet_format=point_octet_formats[0],
                    bit_length_after_truncated=0,
                    result_to_rank=self.result_to_rank,
                )
            ]),
            io_param=pack_params(psi_pb2.PsiDataInfoResult(
                version=algo_version, result_to_rank=self.result_to_rank))
        )

        self.reset_from_response(rsp)

        return rsp
