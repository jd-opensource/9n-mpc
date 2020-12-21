#-*-encoding=utf-8-*-
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
"""
mnist baseline
"""
from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import argparse
import os
import sys
import time
import logging
import numpy as np
import argparse

sys.path.insert(0, '../')
sys.path.insert(0, './')
import tensorflow as tf

SEED = 1

def input_fn(l_dir, f_dir, batch_size, num_epochs=1):
    """
    从两个数据集中获取数据
    """
    l_feature_map = {
        "x": tf.FixedLenFeature([28*28/2], tf.float32),
        "y": tf.FixedLenFeature([], tf.int64),
        "example_id": tf.FixedLenFeature([], tf.string),
    }
    f_feature_map = {
        "x": tf.FixedLenFeature([28*28/2], tf.float32),
        "example_id": tf.FixedLenFeature([], tf.string),
    }
    def load_dataset(data_dir, feature_map):
        files = sorted([os.path.join(data_dir, f) 
            for f in os.listdir(data_dir) if "tfrecord" in f])
        dataset = tf.data.TFRecordDataset(files)\
            .map(lambda proto: tf.io.parse_single_example(
                proto, feature_map))
        return dataset
    l_ds = load_dataset(l_dir, l_feature_map)
    f_ds = load_dataset(f_dir, f_feature_map)
    dataset = tf.data.Dataset.zip((l_ds, f_ds))\
            .map(lambda l_data, f_data: (
                {"xl": l_data["x"], "xf": f_data["x"]}, 
                l_data["y"]))\
            .repeat(count=num_epochs)\
            .batch(batch_size)
    return dataset


def mnist_model(xl, xf):
    """
    mnist模型
    """
    w1l = tf.get_variable('w1l',
        shape=[28 * 28 // 2, 128],
        dtype=tf.float32,
        initializer=tf.random_uniform_initializer(-0.01, 0.01, SEED))
    b1l = tf.get_variable('b1l',
        shape=[128],
        dtype=tf.float32,
        initializer=tf.zeros_initializer())
    act1_l = tf.nn.relu(tf.nn.bias_add(tf.matmul(xl, w1l), b1l))

    w1f = tf.get_variable('w1f',
        shape=[28 * 28 // 2, 128],
        dtype=tf.float32,
        initializer=tf.random_uniform_initializer(-0.01, 0.01, SEED))
    b1f = tf.get_variable('b1f',
        shape=[128],
        dtype=tf.float32,
        initializer=tf.zeros_initializer())
    act1_f = tf.nn.relu(tf.nn.bias_add(tf.matmul(xf, w1f), b1f))

    w2 = tf.get_variable('w2',
        shape=[128 * 2, 10],
        dtype=tf.float32,
        initializer=tf.random_uniform_initializer(-0.01, 0.01, SEED))
    b2 = tf.get_variable('b2',
        shape=[10],
        dtype=tf.float32,
        initializer=tf.zeros_initializer())
    act1 = tf.concat([act1_l, act1_f], axis=1)
    logits = tf.nn.bias_add(tf.matmul(act1, w2), b2)
    return logits


def model_fn(features, labels, mode):
    xl = features['xl']
    xf = features['xf']
    logits = mnist_model(xl, xf)
    if mode == tf.estimator.ModeKeys.TRAIN:
        loss = tf.losses.sparse_softmax_cross_entropy(labels=labels, logits=logits)
        correct = tf.nn.in_top_k(predictions=logits, targets=labels, k=1)
        acc = tf.reduce_mean(input_tensor=tf.cast(correct, tf.float32))

        optimizer = tf.train.AdamOptimizer(learning_rate=0.1)

        train_op=optimizer.minimize(loss, tf.train.get_or_create_global_step())

        logging_hook = tf.train.LoggingTensorHook(
            {"loss" : loss, "acc" : acc}, every_n_iter=100)

        return tf.estimator.EstimatorSpec(
            mode=tf.estimator.ModeKeys.TRAIN,
            loss=loss,
            train_op=train_op,
            training_hooks=[logging_hook])
    elif mode == tf.estimator.ModeKeys.EVAL:
        loss = tf.losses.sparse_softmax_cross_entropy(labels=labels, logits=logits)
        return tf.estimator.EstimatorSpec(
            mode=tf.estimator.ModeKeys.EVAL,
            loss=loss,
            eval_metric_ops={
                'accuracy': tf.metrics.accuracy(
                    labels=labels, predictions=tf.argmax(logits, axis=1)),
        })
    elif mode == tf.estimator.ModeKeys.PREDICT:
        predictions = {
            'output' : logits,
            'predict': tf.argmax(logits, axis=1)
        }
        return tf.estimator.EstimatorSpec(
            mode=tf.estimator.ModeKeys.PREDICT,
            predictions=predictions)


def run_estimator(args):
    """
    用estimator
    """
    l_train = os.path.join(args.data_dir, "leader_train")
    f_train = os.path.join(args.data_dir, "follower_train")

    model_dir = "model_baseline"
    export_dir = "export_baseline"
    eval_steps = 1000
    batch_size = 128
    n_epoch = args.n_epoch

    if tf.io.gfile.exists(model_dir):
        tf.io.gfile.rmtree(model_dir)
    tf.io.gfile.makedirs(model_dir)

    tf.logging.set_verbosity(tf.logging.INFO)

    train_input = lambda config: input_fn(l_train, f_train,
            batch_size, n_epoch)

    mnist_estimator = tf.estimator.Estimator(
        model_fn=model_fn,
        model_dir=model_dir)

    mnist_estimator.train(train_input)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("-d", "--data_dir", dest="data_dir",
        help="data_dir")
    parser.add_argument("-n", "--n_epoch", dest="n_epoch",
        type=int, default=1, help="n_epoch")
    args = parser.parse_args()
    run_estimator(args)
