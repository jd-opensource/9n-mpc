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
import tensorflow as tf

tf.flags.DEFINE_string("role", "leader", "FL worker role [leader, follower].")
tf.flags.DEFINE_string("appli_id", "jdfl", "Application Id.")

tf.flags.DEFINE_string("local_addr", None,\
    "FL train local worker IP port(ip:port).")
tf.flags.DEFINE_string("peer_addr", None,\
    "FL train remote worker IP port(ip:port).")
tf.flags.DEFINE_string("dc_addr", None,\
    "FL train DataCenter IP port(ip:port).")
tf.flags.DEFINE_string("coordinator_addr", None,\
    "FL RegisterUUID IP port(ip:port).")
tf.flags.DEFINE_string("proxy_addr", None,\
    "FL proxy IP port(ip:port).")
tf.flags.DEFINE_integer("worker_id", 0, "FL train worker rank id.")
tf.flags.DEFINE_integer("rpc_service_type", 1,\
    "kinds of service method: 0(Unary), 1(Bidirectional streaming).")

tf.flags.DEFINE_string("model_dir", "./models/model_dir/",\
    "The directory where the checkpoint will be loaded/stored.")
tf.flags.DEFINE_string("export_dir", "./models/export_savemodel/", \
    "The directory where the exported SavedModel will be stored.")

tf.flags.DEFINE_integer("local_debug", 0,\
    "local debug mode, without RegisterUUID or GetPairInfo")
tf.flags.DEFINE_integer("check_exampleid", 0,\
    "check exampleid for each batch, 0: not check, 1: check")
tf.flags.DEFINE_integer("eval", 0, "eval")
tf.flags.DEFINE_string("checkpoint_hdfs_path", '',\
    "hdfs checkpoint path for eval")
