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


from .interconnection.handshake import entry_pb2
from .interconnection.runtime import ecdh_psi_pb2

from .base import lctx_send_proto, lctx_recv_proto, SENDER_RANK
from .config import Context
from .cipher_store import CipherStore

import pyarrow as pa
import threading


def shakehand_flow(ctx: Context, lctx):
    if ctx.rank == SENDER_RANK:
        req = ctx.make_handshake_request()
        lctx_send_proto(lctx, req)

        rsp = lctx_recv_proto(lctx, entry_pb2.HandshakeResponse)
        ctx.reset_from_response(rsp)

    else:
        req = lctx_recv_proto(lctx, entry_pb2.HandshakeRequest)
        rsp = ctx.negotiate_handshake(req)

        lctx_send_proto(lctx, rsp)


BATCH = 10*10000


def calcucipher_and_send(array, batch, ctx, lctx):
    batch_index = 0
    is_last_batch = False

    if len(array) == 0:
        lctx_send_proto(lctx, ecdh_psi_pb2.EcdhPsiCipherBatch(
            type="enc",
            batch_index=0,
            is_last_batch=True,
            count=0,
            ciphertext=b''
        ))
        return

    for i in range(0, len(array), batch):
        arr = array[i:i+batch]
        count = len(arr)
        if i+batch >= len(array):
            is_last_batch = True

        arr = ctx.hash_and_encrypt(arr)

        protomsg = ecdh_psi_pb2.EcdhPsiCipherBatch(
            type="enc",
            batch_index=batch_index,
            is_last_batch=is_last_batch,
            count=count,
            ciphertext=b''.join(arr.to_pylist())
        )

        lctx_send_proto(lctx, protomsg)

        batch_index += 1


def recv_and_calcu(ctx, lctx, prcess):
    done = False
    while not done:
        protomsg = lctx_recv_proto(lctx, ecdh_psi_pb2.EcdhPsiCipherBatch)
        if protomsg.is_last_batch:
            done = True

        arr = ctx.point_octet_unmarshal(protomsg.ciphertext, protomsg.count)
        prcess(arr, ctx, done)


def diffie_hellman_flow(psi_in: pa.RecordBatch, ctx: Context, lctx):
    psi_id = psi_in.column(0)
    store = CipherStore()

    calcu_send_task = threading.Thread(target=calcucipher_and_send,
                                       args=(psi_id, BATCH, ctx, lctx))
    recv_calcu_task = threading.Thread(
        target=recv_and_calcu, args=(ctx, lctx, store.calcu_add_peer_cipher))

    tasks = [calcu_send_task, recv_calcu_task]

    dua_lctx = lctx.SubWorld("dua", ctx.domain_list)

    if ctx.need_send_cipher:
        send_dualenc_task = threading.Thread(
            target=store.send_dualenc, args=(ctx, dua_lctx))
        tasks.append(send_dualenc_task)

    if ctx.need_recv_cipher:
        recv_dualenc_task = threading.Thread(
            target=recv_and_calcu, args=(ctx, dua_lctx, store.recv_duaenc_local_cipher))
        tasks.append(recv_dualenc_task)

    for task in tasks:
        task.start()

    for task in tasks:
        task.join()

    if ctx.need_recv_cipher:
        local_take_index = pa.array(store.local_take_index, type=pa.int64())
        return psi_in.take(local_take_index)

    return None
