# Copyright 2020 The 9NFL Authors. All Rights Reserved.
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
# ==============================================================================

#-*-encoding=utf-8-*-

"""
mnist follower
"""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import json
import os
import sys
import time
import logging

sys.path.insert(0, './')
sys.path.insert(0, '../')
sys.path.insert(0, './fl_comm_libs/')
sys.path.insert(0, '../fl_comm_libs/')

import tensorflow as tf
from tensorflow.python.framework import ops
from tensorflow.python.platform import flags

import fl_comm_libs.fl_ops as jdfl_ops
import fl_comm_libs.fl_estimator as jdfl_estimator

import fl_comm_libs.util as util
import fl_comm_libs.flflags as fl_flags
import fl_comm_libs.flenv as flenv


logging.basicConfig(
        level=logging.DEBUG,
        format='%(asctime)s %(filename)s[line:%(lineno)d] %(levelname)s %(message)s',
        datefmt='%Y-%m-%d %H:%M:%S')

SEED = 1

def input_fn(fl_bridge, role):
    """
    leader的输入函数
    """
    grpc_ds = jdfl_ops.FlGrpcFetchDataset(role)
    grpc_ds = grpc_ds.prefetch(3)
    compression_type = ''
    buffer_size = 0 

    feature_map = { 
        "example_id": tf.FixedLenFeature([], tf.string),
        "x": tf.FixedLenFeature([28*28/2], tf.float32),
    }
    
    dataset = jdfl_ops.FlTFRecordDataset(grpc_ds, 
        compression_type, buffer_size)\
        .prefetch(512)\
        .batch(128, drop_remainder=True)
    tfrecord_data = tf.data.make_one_shot_iterator(dataset)\
        .get_next()
    features = tf.parse_example(tfrecord_data, features=feature_map)
    return features, {}


def serving_input_receiver_fn():
    """
    用于导模型
    """
    feature_map = {
        "x": tf.FixedLenFeature([28 * 28 // 2], tf.float32),
    }

    records = tf.placeholder(dtype=tf.string, name='records')
    features = tf.parse_example(records, features=feature_map)
    receiver_tensors = {'records': records}
    return tf.estimator.export.ServingInputReceiver(features, receiver_tensors)


def fl_model_fn(fl_bridge, features, labels, mode):
    """
    mode_fn for fl_estimator
    """
    x = features['x']

    w1f = tf.get_variable('w1f',
        shape=[28 * 28 / 2, 128],
        dtype=tf.float32,
        initializer=tf.random_uniform_initializer(-0.01, 0.01, SEED))
    b1f = tf.get_variable('b1f',
        shape=[128],
        dtype=tf.float32,
        initializer=tf.zeros_initializer())

    act1_f = tf.nn.relu(tf.nn.bias_add(tf.matmul(x, w1f), b1f))

    if mode == tf.estimator.ModeKeys.PREDICT:
        predictions = {
            'output' : act1_f
        }
        return tf.estimator.EstimatorSpec(
                mode=tf.estimator.ModeKeys.PREDICT,
                predictions=predictions)

    ids = features['example_id']
    ids_chk_op = flenv.verify_example_ids(fl_bridge, ids, debug_mode=True)

    if mode == tf.estimator.ModeKeys.TRAIN:
        with ops.control_dependencies([act1_f, ids_chk_op]):
            recv_grad = fl_bridge.fl_tensor_send_recv(act1_f,
                datamsg_sname='act1_f', datamsg_rname='act1_f_grad')

        # train
        optimizer = tf.train.AdamOptimizer(learning_rate=0.1)

        train_op=optimizer.minimize(act1_f, 
                global_step=tf.train.get_or_create_global_step(),
                grad_loss=recv_grad)
        
        return tf.estimator.EstimatorSpec(
                mode=tf.estimator.ModeKeys.TRAIN,
                loss=recv_grad,
                train_op=train_op)
    else:
        # eval
        with ops.control_dependencies([act1_f, ids_chk_op]):
            output = fl_bridge.fl_tensor_send(act1_f, datamsg_sname='act1_f')
        
        return tf.estimator.EstimatorSpec(
                mode=tf.estimator.ModeKeys.EVAL,
                loss=output)


def fl_run(is_chief, run_config, cluster=None, job_name=None, task_index=None):
    fl_bridge = jdfl_ops.load_fl_ops_lib()
    
    estimator = jdfl_estimator.FLEstimator(
        fl_model_fn,
        is_chief,
        fl_bridge,
        run_config['role'],
        run_config['appli_id'],
        run_config['bind_addr'],
        run_config['peer_addr'],
        run_config['proxy_addr'],
        run_config['dc_addr'],
        run_config['channel_comm_uuid'],
        run_config['worker_id'],
        run_config['rpc_service_type'],
        cluster,
        job_name,
        task_index)

    if run_config["eval"] == 1:
        # start eval
        estimator.eval(input_fn,
                checkpoint_path=run_config['model_dir'])
    else: 
        # Start train
        estimator.train(input_fn,
                checkpoint_path=run_config['model_dir'],
                save_checkpoint_steps=1000)

        if is_chief:
            estimator.export_savedmodel(run_config['export_dir'],
                    serving_input_receiver_fn,
                    checkpoint_path=run_config['model_dir'])
            tf.logging.info('The exported SavedModel % s', run_config['export_dir'])
            return
        tf.logging.info('Train worker finished.')
        time.sleep(30)


def main(unused_argv):
    env = flenv.FLEnv(flags.FLAGS)
    env.set_role('follower')
    env.run_cluster(fl_run)


if __name__ == '__main__':
   
    os.environ["_FL_DEBUG"] = "50"
 
    logging.basicConfig(level=logging.INFO)
    logging.info(__file__)
    logging.info(str(sys.argv))

    tf.compat.v1.app.run()
