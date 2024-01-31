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

from .interconnection.runtime import ecdh_psi_pb2

from .base import lctx_send_proto

import crypto
import queue
from threading import Event


class CipherStore():
    def __init__(self) -> None:
        self.peer_cipher = queue.Queue()
        self.peer_cipher_set = crypto.BytesHashSet()
        self.peer_cipher_set_done = Event()

        self.local_index_record = 0
        self.local_take_index = []

    def calcu_add_peer_cipher(self, array, ctx, done):
        array = ctx.curve.diffie_hellman(array)

        if done:
            self.peer_cipher.put(None)
        self.peer_cipher.put(array)

        if ctx.need_recv_cipher:
            for cipher in array.to_pylist():
                self.peer_cipher_set.insert(cipher)
            if done:
                self.peer_cipher_set_done.set()

    def send_dualenc(self, ctx, lctx):
        batch_index = 0
        is_last_batch = False

        while not is_last_batch:
            arr = self.peer_cipher.get()

            if arr is None:
                arr = self.peer_cipher.get()
                is_last_batch = True

            arr = ctx.point_octet_marshal(arr)
            count = len(arr)

            protomsg = ecdh_psi_pb2.EcdhPsiCipherBatch(
                type="dual.enc",
                batch_index=batch_index,
                is_last_batch=is_last_batch,
                count=count,
                ciphertext=b''.join(arr.to_pylist())
            )

            lctx_send_proto(lctx, protomsg)

            batch_index += 1

    def recv_duaenc_local_cipher(self, array, ctx, done):
        self.peer_cipher_set_done.wait()
        for cipher in array.to_pylist():
            if self.peer_cipher_set.contains(cipher):
                self.local_take_index.append(self.local_index_record)

            self.local_index_record += 1
