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
#-*-encoding=utf-8-*-
import tensorflow as tf
from tensorboard import summary as summary_lib
import logging
import subprocess
import functools
import os


def with_name_scope(name):
    """
    set name scope
    """
    def decorator(method):
        @functools.wraps(method)
        def inner(self, *args, **kwargs):
            with tf.name_scope(name):
                return method(self, *args, **kwargs)
        return inner
    return decorator


@with_name_scope('auc')
def auc(logits, labels, name='auc_metrcs', 
        num_thresholds=200, summary=True):
    """
    流式auc， 默认分桶数为tf默认值200
    分桶数越大，精确度越高
    """
    logistics = tf.nn.sigmoid(logits)
    logistics = tf.reshape(logistics, [-1])
    labels = tf.reshape(labels, [-1])
    auc = tf.metrics.auc(labels, logistics, name=name,
            num_thresholds=num_thresholds)
    return auc


@with_name_scope('cross_entropy_loss')
def cross_entropy_loss(logits, labels, weight=1.0):
    logits = tf.reshape(logits, [-1])
    labels = tf.reshape(labels, [-1])
    loss = tf.reduce_mean(tf.nn.sigmoid_cross_entropy_with_logits(
            logits=logits, labels=labels))
    loss = tf.multiply(weight, loss)
    return loss


def rm_dir(dir_name):
    """
    删除目录
    """
    if tf.io.gfile.exists(dir_name):
        tf.io.gfile.rmtree(dir_name)
    tf.io.gfile.makedirs(dir_name)


def mk_dir(dir_name):
    """
    创建目录
    """
    if not tf.io.gfile.exists(dir_name):
        tf.io.gfile.makedirs(dir_name)


def run(cmd):
    """
    获取shell的结果
    """
    logging.info(cmd)
    buff = subprocess.check_output(cmd, shell=True)
    # 兼容python2和python3
    if type(buff) == str:
        return buff.strip()
    else:
        return str(buff, encoding='utf-8').strip()


def download_from_hdfs(src, dest):
    """
    从hdfs上下载
    """
    assert(src)
    assert(dest)
    run("hadoop fs -get %s/* %s/" % (src, dest))


