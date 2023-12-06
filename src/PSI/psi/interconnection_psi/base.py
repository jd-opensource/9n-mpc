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

from google.protobuf.any_pb2 import Any


class PsiException(Exception):
    error_code: header_pb2.ErrorCode
    error_msg: str

    def __repr__(self):
        return f"{self.error_code.SerializeToString()} {self.error_msg}"


def raise_exception(error_code, err_msg, input=[], filedpos=""):
    dis = ""
    for lst in input:
        lfiled = eval("lst.{}".format(filedpos))
        dis += "[{}] ".format(str(lfiled))

    raise PsiException(error_code, f"{err_msg} {dis}")


def intersection(input, filedpos, typ):
    filedpos = ".".join(filedpos)
    filed = eval("input[0].{}".format(filedpos))

    if typ is int:
        return sum([eval("lst.{}".format(filedpos)) for lst in input]) \
            == filed*len(input)

    if typ is bool:
        return all([eval("lst.{}".format(filedpos)) for lst in input])

    if typ is list:
        if getattr(filed[0], "__hash__") is None:
            intersection = []

            for element in filed:
                is_common = True

                for lst in input[1:]:
                    lfiled = eval("lst.{}".format(filedpos))
                    if element not in lfiled:
                        is_common = False
                        break

                if is_common:
                    intersection.append(element)

            return intersection

        intersection = set(filed)

        for lst in input[1:]:
            lfiled = eval("lst.{}".format(filedpos))
            intersection = intersection.intersection(lfiled)

        return list(intersection)


def unpack_params(input, filedpos, prototyp, typ=None):
    filedpos = ".".join(filedpos)
    ret = []
    islist = True

    if not isinstance(input, list):
        input = [input]
        islist = False

    for elem in input:
        lfiled = eval("elem.{}".format(filedpos))

        if typ is list:
            for any_param in lfiled:
                if any_param.Is(prototyp.DESCRIPTOR):
                    param = prototyp()
                    any_param.Unpack(param)
                    ret.append(param)
        else:
            if lfiled.Is(prototyp.DESCRIPTOR):
                param = prototyp()
                lfiled.Unpack(param)
                ret.append(param)

    return ret if islist else ret[0]


def pack_params(input):
    if isinstance(input, list):
        ret = []

        for elem in input:
            any_message = Any()
            any_message.Pack(elem)
            ret.append(any_message)

        return ret

    any_message = Any()
    any_message.Pack(input)

    return any_message


def lctx_send_proto(lctx, protomsg):
    lctx.Send(lctx.NextRank(), protomsg.SerializeToString())


def lctx_recv_proto(lctx, prototyp):
    bs = lctx.Recv(lctx.NextRank())
    protomsg = prototyp()
    protomsg.ParseFromString(bs)

    return protomsg


def int_to_proto_enum_name(value, typ):
    return typ.DESCRIPTOR.values_by_number[value].name


class crypto_func():
    def __init__(self,  value, typ, func) -> None:
        self.value = value
        self.typ = typ
        self.func = func

    def __call__(self, *args: Any, **kwds: Any) -> Any:
        return self.func(int_to_proto_enum_name(
            self.value, self.typ), *args, **kwds)


SENDER_RANK = 0
RECEIVER_RANK = 1
