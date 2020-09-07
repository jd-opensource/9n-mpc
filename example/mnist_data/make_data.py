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
import numpy as np
import random
import uuid
import subprocess
import os
import argparse
import logging
import sys
import tensorflow as tf


logging.basicConfig(level = logging.INFO, \
    format='%(asctime)s %(filename)s[line:%(lineno)d] %(levelname)s %(message)s', \
    stream=sys.stdout, \
    datefmt='%a, %d %b %Y %H:%M:%S', \
    filemode='a')


FLOAT_TYPE = "float"
INT64_TYPE = "int64"
BYTES_TYPE = "bytes"


TRAIN_PERCENT = 0.8


def download_mnist():
    """
    download mnist data use tf
    save as `mnist_x.npy` and `mnist_y.npy`
    """
    logging.info("Generate mnist_x.npy and mnist_y.npy")
    (x, y), _ = tf.keras.datasets.mnist.load_data()
    np.save('mnist_x', x)
    np.save('mnist_y', y)

def load_data():
    """
    read mnist data from file
    x (60000, 28, 28)
    y (60000,)

    split each record into leader_part and follower_part
    and allocate example_id for each record
    """
    logging.info("Load mnist_x.npy and mnist_y.npy")
    x = np.load('mnist_x.npy')
    y = np.load('mnist_y.npy')
    x = x.reshape(x.shape[0], -1).astype(np.float32) / 255.0
    y = y.astype(np.int64)
    
    # allocate example_id
    e_ids = [str(uuid.uuid1()) for i in range(len(y))]

    xl = x[:, :x.shape[1]//2]
    xf = x[:, x.shape[1]//2:]
    return xl, xf, y, e_ids


def convert_n_bytes(n, b):
    bits = b * 8
    return (int)(((n + 2 ** (bits - 1)) % (2 ** bits) - 2 ** (bits - 1)) & 0xfffffff)


def convert_4_bytes(n):
    return convert_n_bytes(n, 4)


def getHashCode(s):
    """
    getHashCode of java
    """
    h = 0
    n = len(s)
    for i, c in enumerate(s):
        h = h + ord(c) * 31 ** (n - 1 - i)
    return convert_4_bytes(h)


def get_feature(values, f_type):
    """ 
    根据f_type返回对应的Feature对象
    """
    if f_type == FLOAT_TYPE:
        return tf.train.Feature(float_list=
            tf.train.FloatList(value=values))
    elif f_type == INT64_TYPE:
        return tf.train.Feature(int64_list=
            tf.train.Int64List(value=values))
    elif f_type == BYTES_TYPE:
        return tf.train.Feature(bytes_list=
            tf.train.BytesList(value=values))
    else:
        raise ValueError("unknown f_type")


def item_2_tf_record(example_id, xx, yy=None):
    """ 
    create tf exmaple use tf.train.Example
    """
    pb_dict = {}
    # example_id and event_time are 
    # requested by data join
    pb_dict["example_id"] = get_feature(example_id, BYTES_TYPE)
    pb_dict["event_time"] = get_feature([b"20191001042452",], BYTES_TYPE)

    pb_dict["x"] = get_feature(xx, FLOAT_TYPE)
    if yy: 
        pb_dict["y"] = get_feature(yy, INT64_TYPE)
    example = tf.train.Example(
        features=tf.train.Features(feature=pb_dict))
    return example.SerializeToString()


def get_pb_dataset(eids, x, y=None):
    """ 
    get [(example_id, pb_str)]
    """
    dataset = []
    for i in range(len(x)):
        if y is not None:
            pb_str = item_2_tf_record(
                [eids[i].encode('utf-8'),], x[i], [y[i],])
        else:
            pb_str = item_2_tf_record(
                [eids[i].encode('utf-8'),], x[i])
        dataset.append((eids[i], pb_str))
    return dataset


def repartition_and_sort(dataset, n_partition=10):
    """
    partition by example_id
    and sort in each partiton
    """
    output = [[] for i in range(n_partition)]
    for example_id, pb_str in dataset:
        partition_id = getHashCode(example_id) % n_partition
        output[partition_id].append((example_id, pb_str))
    for partition in output:
        partition.sort(key=lambda x: x[0])
    return output


def save_local(ori_data, save_dir, n_partition=10,
    drop_rate=-1, need_meta=True):
    """
    partition and sort by example id
    save as tfrecord
    save_dir will be removed if already exists
    """
    if tf.io.gfile.exists(save_dir):
        tf.io.gfile.rmtree(save_dir)
    tf.io.gfile.makedirs(save_dir)
    
    if need_meta:
        save_meta_dir = save_dir + "_meta"
        if tf.io.gfile.exists(save_meta_dir):
            tf.io.gfile.rmtree(save_meta_dir)
        tf.io.gfile.makedirs(save_meta_dir)

    data = repartition_and_sort(ori_data, n_partition)
    for i in range(n_partition):
        count = 0
        fpath = os.path.join(save_dir, '%05d.tfrecord' % i)
        meta_fpath = os.path.join(save_meta_dir, '%05d.meta' % i)
        writer = tf.python_io.TFRecordWriter(fpath)
        if need_meta:
            meta_writer = open(meta_fpath, 'w')
        for eid, record in data[i]:
            if drop_rate > 0:
                r = random.random()
                if r > drop_rate:
                    count += 1
                    writer.write(record)
                    if need_meta:
                        meta_writer.write('%s\n' % eid)
            else:
                count += 1
                writer.write(record)
                if need_meta:
                    meta_writer.write('%s\n' % eid)
        writer.close()
        if need_meta:
            meta_writer.close()
        logging.info("Write %s done. %d records" % (fpath, count))


def main(args):
    """
    generate train data and test data for fl
    """
    # l: leader, f: follower
    xl, xf, y, e_ids = load_data()
    
    len_all = len(xl)
    assert(len_all == len(y))
    len_train = min(int(len_all * TRAIN_PERCENT),
        len_all - 1)

    train_xl = xl[:len_train]
    train_xf = xf[:len_train]
    train_y = y[:len_train]
    train_eids = e_ids[:len_train]

    test_xl = xl[len_train:]
    test_xf = xf[len_train:]
    test_y = y[len_train:]
    test_eids = e_ids[len_train:]

    data_dir = args.output_dir

    l_train_path = data_dir + "/leader_train"
    f_train_path = data_dir + "/follower_train"
    l_test_path = data_dir + "/leader_test"
    f_test_path = data_dir + "/follower_test"

    n_partition = args.partition_num
    drop_rate = args.drop_rate
    need_meta = args.need_meta == 1
    
    save_local(get_pb_dataset(train_eids, train_xl, train_y),
        l_train_path, n_partition, drop_rate, need_meta) 
    save_local(get_pb_dataset(train_eids, train_xf),
        f_train_path, n_partition, drop_rate, need_meta) 
    save_local(get_pb_dataset(test_eids, test_xl, test_y),
        l_test_path, n_partition, drop_rate, need_meta) 
    save_local(get_pb_dataset(test_eids, test_xf),
        f_test_path, n_partition, drop_rate, need_meta) 


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("-o", "--output_dir", dest="output_dir",
        help="output dir")
    parser.add_argument("-d", "--download", type=int, default=1,
        help="whether to download mnist data")
    parser.add_argument("-p", "--partition_num", type=int, default=10,
        help="partition num")
    parser.add_argument("-r", "--drop_rate", type=float, default=0.0,
        help="probability of dropping a record")
    parser.add_argument("-m", "--need_meta", type=int, default=1,
        help="write tfrecord meta")
    args = parser.parse_args()

    if args.download == 1: 
        download_mnist()

    main(args)
