# Copyright 2020 The 9nFL Authors. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import sys
import time
import json
from datetime import datetime

import tensorflow.compat.v1 as tf
from tensorflow_estimator.python.estimator import model_fn as model_fn_lib
from tensorflow.estimator import ModeKeys
from tensorflow.python.client import timeline
from tensorflow.core.protobuf import config_pb2

def _extract_metric_update_ops(eval_dict):
        """Separate update operations from metric value operations."""
        update_ops = []
        value_ops = {}
        # Sort metrics lexicographically so graph is identical every time.
        for name in sorted(eval_dict.keys()):
                metric_tensor, update_op = eval_dict[name]
                value_ops[name] = metric_tensor
                update_ops.append(update_op)
        return update_ops, value_ops


def _dict_to_str(dictionary):
        """Get a `str` representation of a `dict`.
        Args:
                dictionary: The `dict` to be represented as `str`.
        Returns:
                A `str` representing the `dictionary`.
        """
        return ', '.join('%s = %s' % (k, v)
                for k, v in sorted(dictionary.items())
                if not isinstance(v, bytes))


class FLEstimator(object):
    def __init__(self, model_fn, is_chief, fl_bridge, role, appli_id, 
                            fl_local_worker_ipport, fl_peer_worker_ipport, fl_proxy_ipport,
                            datacenter_ipport, channel_comm_uuid, worker_rank=0, rpc_service_type=0,
                            cluster_def=None, job_name=None, task_index=None):
        self._model_fn = model_fn
        self._is_chief = is_chief
        self._fl_bridge = fl_bridge
        self._role = role
        self._appli_id = appli_id
        self._fl_local_address = fl_local_worker_ipport
        self._fl_peer_address = fl_peer_worker_ipport
        self._fl_proxy_address = fl_proxy_ipport
        self._dc_address = datacenter_ipport
        self._proxy_comm_uuid = channel_comm_uuid
        self._rank_id = worker_rank
        self._rpc_serv_type = rpc_service_type
        self._cluster_spec = cluster_def
        self._job_name = job_name
        self._task_index = task_index

        # timeline 
        self._run_options = config_pb2.RunOptions(
                trace_level=config_pb2.RunOptions.FULL_TRACE)
        self._run_metadata = config_pb2.RunMetadata()

        self._timeline_trace=None

    def _update_timeline(self):
        """ update self._timeline_trace"""
        fetched_timeline = timeline.Timeline(self._run_metadata.step_stats)
        chrome_trace = fetched_timeline.generate_chrome_trace_format()
        tl_trace = json.loads(chrome_trace)
        if self._timeline_trace is None:
            self._timeline_trace = tl_trace
        else:
            for e in tl_trace['traceEvents']:
                if 'ts' in e:
                    self._timeline_trace['traceEvents'].append(e)

    def _save_timeline(self, timeline_path):
        """ save to timeline_path/timeline_step_role_taskindex.json"""
        tf.logging.info('*** Dump timeline path: %s', timeline_path)
        f_name = '{}/timeline_step_{}_{}.json'.format(
                        timeline_path, self._role, str(self._rank_id))
        with open(f_name, 'w') as f:
            json.dump(self._timeline_trace, f)

    def train(self, input_fn,
                    checkpoint_path=None,
                    save_checkpoint_steps=None):
        self.train_or_eval(input_fn, ModeKeys.TRAIN,
                    checkpoint_path, save_checkpoint_steps)

    def eval(self, input_fn,
                    checkpoint_path=None,
                    save_checkpoint_steps=None):
        self.train_or_eval(input_fn, ModeKeys.EVAL,
                    checkpoint_path, save_checkpoint_steps)

    def train_or_eval(self, input_fn, mode,
                    checkpoint_path=None,
                    save_checkpoint_steps=None):
        config = tf.ConfigProto()
        if self._cluster_spec is not None:
            device_fn = tf.train.replica_device_setter(
                    worker_device="/job:%s/task:%d" % (self._job_name, self._task_index),
                    merge_devices=True, cluster=self._cluster_spec)
            #cluster_spec = self._cluster_spec.as_cluster_def()
            cluster_spec = tf.train.ClusterSpec(self._cluster_spec)
            server = tf.train.Server(self._cluster_spec,
                                                     job_name=self._job_name,
                                                     task_index=self._task_index)
            target = server.target
            device_filters = [
                    '/job:ps', '/job:%s/task:%d' % (self._job_name, self._task_index)
            ]
            config = tf.ConfigProto(device_filters=device_filters)
        else:
            device_fn = None
            cluster_spec = None
            target = ''

        config.experimental.share_session_state_in_clusterspec_propagation = True
        tf.config.set_soft_device_placement(False)

        with tf.Graph().as_default() as g:
            with tf.device(device_fn):
                # FlBridge Init
                bridge_addr_placeholder = tf.placeholder(dtype=tf.string)
                appli_id_placeholder = tf.placeholder(dtype=tf.string)
                rank_id_placeholder = tf.placeholder(dtype=tf.int32)
                role_placeholder = tf.placeholder(dtype=tf.string)
                rpc_serv_type_placeholder = tf.placeholder(dtype=tf.int32)
                comm_uuid_placeholder = tf.placeholder(dtype=tf.string)
                fl_init_op = self._fl_bridge.fl_bridge_server_init([bridge_addr_placeholder], [appli_id_placeholder], 
                                        [rank_id_placeholder], [role_placeholder], [rpc_serv_type_placeholder], [comm_uuid_placeholder])

                # client channel init
                peer_addr_placeholder = tf.placeholder(dtype=tf.string)
                dc_addr_placeholder = tf.placeholder(dtype=tf.string)
                fl_train_channel_init = self._fl_bridge.fl_rpc_channel_init([peer_addr_placeholder], channel_type='TRAIN')
                fl_data_channel_init = self._fl_bridge.fl_rpc_channel_init([dc_addr_placeholder], channel_type='DATA')
                
                # train control OP
                target_connect_op = self._fl_bridge.fl_channel_connect()
                wait_peer_op = self._fl_bridge.fl_wait_peer_ready()
                train_start_placeholder = tf.placeholder(dtype=tf.string)
                train_start_op = self._fl_bridge.fl_train_start(train_start_placeholder)
                train_follow_op = self._fl_bridge.fl_train_follow()
                train_commit_op = self._fl_bridge.fl_train_step_commit()
            
                features, labels = input_fn(
                        self._fl_bridge, self._role)
                model_spec = self._model_fn(self._fl_bridge, features, labels, mode)

            if mode == ModeKeys.TRAIN:
                    hooks = model_spec.training_hooks
            else:
                    # Track the average loss in default
                    eval_metric_ops = model_spec.eval_metric_ops or {}
                    if model_fn_lib.LOSS_METRIC_KEY not in eval_metric_ops:
                        loss_metric = tf.metrics.mean(model_spec.loss)
                        eval_metric_ops[model_fn_lib.LOSS_METRIC_KEY] = loss_metric

                    update_ops, eval_dict = _extract_metric_update_ops(eval_metric_ops)
                    eval_op = tf.group(*update_ops)

                    # Create the real eval oplso track the global step
                    if tf.GraphKeys.GLOBAL_STEP in eval_dict:
                            raise ValueError(
                                        'Metric with name `global_step` is not allowed, because '
                                        'Estimator already defines a default metric with the '
                                        'same name.')
                    eval_dict[tf.GraphKeys.GLOBAL_STEP] = \
                            tf.train.get_or_create_global_step()

                    # Prepare hooks
                    hooks = list(model_spec.evaluation_hooks) or []
                    final_ops_hook = tf.train.FinalOpsHook(eval_dict)
                    hooks.append(final_ops_hook)

            start_tm = datetime.now()
            with tf.train.MonitoredTrainingSession(
                        master=target,
                        config=config,
                        is_chief=self._is_chief,
                        checkpoint_dir=checkpoint_path,
                        save_checkpoint_steps=save_checkpoint_steps,
                        hooks=hooks) as sess:

                if not sess.should_stop():
                    tf.logging.info('FlBridge init... ')
                    sess.run_step_fn(lambda step_context:
                            step_context.session.run(
                                [fl_init_op], 
                                feed_dict = { 
                                        bridge_addr_placeholder: self._fl_local_address, 
                                        appli_id_placeholder: self._appli_id, 
                                        rank_id_placeholder: self._rank_id, 
                                        role_placeholder: self._role, 
                                        rpc_serv_type_placeholder: self._rpc_serv_type, 
                                        comm_uuid_placeholder: self._proxy_comm_uuid
                                })
                    )

                    sess.run_step_fn(lambda step_context:
                            step_context.session.run(
                                [fl_train_channel_init, fl_data_channel_init],
                                feed_dict = {
                                        peer_addr_placeholder: self._fl_proxy_address,
                                        dc_addr_placeholder: self._dc_address
                                })
                    )

                    def _try_connect():
                        status_code, err_msg = sess.run_step_fn(lambda step_context:
                            step_context.session.run(target_connect_op))
                        if status_code == 0:
                            tf.logging.info("*** Connect OK ***")
                            return True
                        else:
                            tf.logging.info("Connect failed: %s", err_msg)
                            return False
                    def _wait_connect():
                        for _ in range(3):
                            status_code, err_msg = sess.run_step_fn(lambda step_context:
                                step_context.session.run(wait_peer_op))
                            if status_code == 0:
                                tf.logging.info("*** Peer Connected OK ***")
                                return True
                            else:
                                tf.logging.info("Wait peer failed: %s", err_msg)
                                continue
                        return False
                    def _wait_train_ready():
                        for _ in range(3):
                            status_code, err_msg = sess.run_step_fn(lambda step_context:
                                step_context.session.run(train_follow_op))
                            if status_code == 0:
                                tf.logging.info("*** Follow Train OK ***")
                                return True
                            else:
                                tf.logging.info("Follow Train failed: %s", err_msg)
                                continue
                        return False

                    while True:
                        tf.logging.info("*** Try connect %s" % (self._fl_proxy_address))
                        next_step = _try_connect()
                        if not next_step:
                            tf.logging.info("Sleep 5s. Try again...")
                            time.sleep(5)
                            continue

                        tf.logging.info("*** Wait Connected ...")
                        next_step = _wait_connect()
                        if not next_step:
                            # restart connect
                            continue

                        if self._role == 'follower':
                            tf.logging.info("*** Follower Train Wait ...")
                            next_step = _wait_train_ready()
                            if not next_step:
                                # restart connect, peer may restart
                                continue
                        
                        tf.logging.info("*** TRAIN READY ...")
                        break

                run_steps = 1
                tf.logging.info("*** Start train ...")
                start_tm = datetime.now()
                while not sess.should_stop():
                    sess.run_step_fn(lambda step_context:
                            step_context.session.run(train_start_op,
                            feed_dict={train_start_placeholder:'/FlTrainStart'}))
                    if mode == ModeKeys.TRAIN: 
                        sess.run(model_spec.train_op, feed_dict={})
                    elif mode == ModeKeys.EVAL:
                        sess.run([model_spec.loss, eval_op], feed_dict={})
                    
                    run_steps += 1
                    
                    sess.run_step_fn(lambda step_context:
                        step_context.session.run([train_commit_op]))
            end_tm = datetime.now()
            delta = end_tm - start_tm
            if mode == ModeKeys.TRAIN:
                tf.logging.info('Train duration: %d' % delta.total_seconds())
                tf.logging.info('*** Train finished ...')
            else:
                # Print result
                tf.logging.info('Metrics for iteration %d: %s',
                                run_steps, _dict_to_str(final_ops_hook.final_ops_values))

                tf.logging.info('Eval duration: %d' % delta.total_seconds())
                tf.logging.info('*** Eval finished ...')


    def export_savedmodel(self, export_dir_base,
                                             serving_input_receiver_fn,
                                             checkpoint_path=None):
        with tf.Graph().as_default() as g:
            receiver = serving_input_receiver_fn()
            model_spec = self._model_fn(self._fl_bridge, receiver.features,
                                                        None, ModeKeys.PREDICT)
            tf.logging.info('Start export savedmodel ...')
            with tf.Session() as sess:
                saver_for_restore = tf.train.Saver(sharded=True)
                saver_for_restore.restore(
                        sess, tf.train.latest_checkpoint(checkpoint_path))
                #print(model_spec.predictions)
                #print(receiver.receiver_tensors)
                tf.saved_model.simple_save(
                        sess, export_dir_base, receiver.receiver_tensors,
                        model_spec.predictions, None)

            return export_dir_base

