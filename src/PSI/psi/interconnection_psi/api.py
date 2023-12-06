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

from .config import Context
from .flow import shakehand_flow, diffie_hellman_flow
from .base import SENDER_RANK, RECEIVER_RANK

import link_py
import pyarrow as pa
from .register_uuid import Register


def psi_sender(psi_in: pa.RecordBatch, task_id: str,
               local_address: str, remote_address: str,
               self_domain: str, target_domain: str,
               redis_address: str, redis_password: str,
               send_back: bool) -> pa.RecordBatch:
    """
    sender.

    Args:
        psi_in (pyarrow.RecordBatch): intersection input.
        task_id (str): unique task id.
        local_address (str): local address.
        remote_address (str): remote proxy address.
        self_domain (str): self domain.
        target_domain (str): target domain.
        redis address (str): redis address.
        redis password (str): redis password.
        send back (str): if result send back.

    Returns:
        return intersection result.
    """

    ctx = Context(rank=SENDER_RANK, item_num=psi_in.num_rows,
                  result_to_rank=-1 if send_back else RECEIVER_RANK,
                  domain_list=[self_domain, target_domain])

    lctx = link_py.CreateContext(rank=SENDER_RANK, self_domain=self_domain,
                                 target_domain=target_domain, id=task_id,
                                 parties=[(self_domain, local_address), (target_domain, remote_address)])
    reg = Register(redis_address, redis_password, int(
        local_address.split(':')[1]), task_id)
    lctx.ConnectToMesh()

    shakehand_flow(ctx, lctx)

    result = diffie_hellman_flow(psi_in, ctx, lctx)

    lctx.WaitLinkTaskFinish()

    return result


def psi_receiver(psi_in: pa.RecordBatch, task_id: str,
                 local_address: str, remote_address: str,
                 self_domain: str, target_domain: str,
                 redis_address: str, redis_password: str,
                 send_back: bool) -> pa.RecordBatch:
    """
    receiver.

    Args:
        psi_in (pyarrow.RecordBatch): intersection input.
        task_id (str): unique task id.
        local_address (str): local address.
        remote_address (str): remote proxy address.
        self_domain (str): self domain.
        target_domain (str): target domain.
        redis address (str): redis address.
        redis password (str): redis password.
        send back (str): if result send back.

    Returns:
        return intersection result.
    """

    ctx = Context(rank=RECEIVER_RANK,
                  item_num=psi_in.num_rows,
                  result_to_rank=-1 if send_back else RECEIVER_RANK,
                  domain_list=[target_domain, self_domain])

    lctx = link_py.CreateContext(rank=RECEIVER_RANK, self_domain=self_domain,
                                 target_domain=target_domain, id=task_id,
                                 parties=[(target_domain, remote_address), (self_domain, local_address)])
    reg = Register(redis_address, redis_password, int(
        local_address.split(':')[1]), task_id)
    lctx.ConnectToMesh()

    shakehand_flow(ctx, lctx)

    result = diffie_hellman_flow(psi_in, ctx, lctx)

    lctx.WaitLinkTaskFinish()

    return result
