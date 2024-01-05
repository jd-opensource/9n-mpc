# -*- coding: UTF-8 -*-

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

from ray.data import Dataset
import os
import ray
import argparse
import hashlib
from typing import Union, Optional, Tuple, List
from urllib.parse import urlparse
from pyarrow import fs
import pyarrow as pa
from enum import Enum
import logging
import requests
import zlib
import json
import struct
from pyarrow.dataset import dataset
#from workerAuth.authClient import AuthClient
import numpy as np
from ray.data.context import DatasetContext


def init_logging(app_id, level=logging.INFO, logdir='/mnt/logs', platform_id='9n-mpc', filename='run.log', catch_tf=False, custom_node_id=None):
    """
    """
    node_id = os.environ.get('NODE_ID')
    task_id = os.environ.get('TASK_ID')
    if not os.path.exists(logdir):
        os.makedirs(logdir)

    # get root logger
    logger = logging.getLogger()
    # clear all existed handlers
    logger.handlers.clear()
    logger.setLevel(level)
    if custom_node_id is None:
        basic_format = "[%(asctime)s.%(msecs)03d] [%(levelname)s] [{}] [{}] [{}] [{}] [%(filename)s:%(lineno)d] %(message)s "\
            .format(platform_id, task_id, app_id, node_id)
    else:
        basic_format = "[%(asctime)s.%(msecs)03d] [%(levelname)s] [{}] [{}] [{}] [{}] [{}] [%(filename)s:%(lineno)d] %(message)s "\
            .format(platform_id, task_id, app_id, node_id, custom_node_id)

    date_format = "%Y-%m-%d %H:%M:%S"
    formatter = logging.Formatter(basic_format, date_format)

    # console output handler
    chlr = logging.StreamHandler()
    chlr.setFormatter(formatter)
    chlr.setLevel(level)

    # update handler
    logger.addHandler(chlr)


def set_node_status(status_host: str, id: str, type: int, status: int, **kwargs) -> dict or int:
    """set node status

    Args:
        id (str): task id
        type (int): type code,0:psi,999:local ,2:callback,3:feature,4:training/inference, 5:商贾云, 6:联通
        status (int): [status code,0:finish ,-1:running, >0:wrong code]
        kwargs : message,result,percent(int)

    Returns:
        dictorint : {"status":0,"result":True}  or status_code
    """

    data = {
        "id": id,
        "type": type,
        "status": status,
        "clusterId": os.environ.get('CLUSTER_ID'),
        "nodeId": os.environ.get('NODE_ID'),
    }
    data.update(kwargs)
    response = requests.post(
        f"http://{status_host}/coordinator/outer/set-worker-info", json=data)
    if response.status_code != 200:
        return response.status_code
    return response.json


class BlockStatus(Enum):
    """
    """
    INIT = 0
    PREPARED = 1
    PROCSSING = 2
    PROCESSED = 3
    FAILED = 4


def infer_protocol(file_path: str) -> str:
    """
    """
    if file_path.startswith(("https://", "http://")):
        return "http"
    elif file_path.startswith("hdfs://"):
        return "hdfs"
    else:
        return "nfs"


def infer_format(file_path: str) -> str:
    """
    """
    lower_base_path = file_path.lower()
    if lower_base_path.endswith(".csv"):
        return "csv"
    elif lower_base_path.endswith(".txt"):
        return "csv"
    elif lower_base_path.endswith(".csv.gz"):
        return "csv.gz"
    elif lower_base_path.endswith(".gz.parquet"):
        return "gz.parquet"
    elif lower_base_path.endswith(".parquet"):
        return "parquet"
    else:
        return "csv"


def read_data_from_filesystem(file_system, file_path: str, with_head: bool, using_ray: bool = False, csv_delimiter: str = ","):
    """
    """
    infered_format = infer_format(file_path=file_path)

    if infered_format in ['csv', 'txt', 'csv.gz']:
        from pyarrow import csv
        if with_head:
            parse_options = csv.ParseOptions(delimiter=csv_delimiter)
        else:
            parse_options = csv.ParseOptions(
                delimiter=csv_delimiter, autogenerate_column_names=True)

        if using_ray:
            table_dataset = ray.data.read_csv(
                file_path,  filesystem=file_system, parse_options=parse_options)
        else:
            table_dataset = csv.read_csv(file_system.open_input_stream(
                file_path), parse_options=parse_options)
    elif infered_format in ['parquet', 'gz.parquet']:
        import pyarrow.parquet as pq
        table_dataset = pq.read_table(file_path, filesystem=file_system)
    else:
        raise ValueError(f"not support data format {infered_format}")
    return table_dataset


def write_data_to_filesystem(file_system, data, file_path: str, append: bool, with_head: bool, csv_delimiter: str = ","):
    """
    """
    infered_format = infer_format(file_path=file_path)
    logging.info(f"write_data_to {file_path} append={append} with_head={with_head}")
    if infered_format in ['csv', 'txt', 'csv.gz']:
        from pyarrow import csv
        write_options = csv.WriteOptions(
            include_header=with_head, delimiter=csv_delimiter)
        if append:
            csv.write_csv(data, file_system.open_append_stream(
                file_path), write_options=write_options)
        else:
            csv.write_csv(data, file_system.open_output_stream(
                file_path), write_options=write_options)
    elif infered_format in ['parquet', 'gz.parquet']:
        import pyarrow.parquet as pq
        compression = None
        if infered_format == 'gz.parquet':
            compression = 'GZIP'
        pq.write_table(data, file_path, filesystem=file_system,
                       compression=compression)
    else:
        raise ValueError(f"not support data format {infered_format}")


def read_data_from_hdfs(data_format: str, abs_file_path: str, with_head: bool, using_ray: bool = False, csv_delimiter: str = ",") -> pa.Table:
    """
    """
    path_url = urlparse(abs_file_path)
    path_net = f"{path_url.scheme}://{path_url.netloc}"
    file_system = fs.HadoopFileSystem.from_uri(path_net)
    return read_data_from_filesystem(file_system=file_system, file_path=path_url.path, with_head=with_head,
                                     using_ray=using_ray, csv_delimiter=csv_delimiter)


def write_data_to_hdfs(data: pa.Table, abs_file_path: str, append: bool = False, with_head=True, csv_delimiter: str = ",") -> bool:
    """
    """
    path_url = urlparse(abs_file_path)
    path_net = f"{path_url.scheme}://{path_url.netloc}"
    file_system = fs.HadoopFileSystem.from_uri(path_net)
    write_data_to_filesystem(file_system=file_system, data=data, file_path=path_url.path, append=append,
                             with_head=with_head, csv_delimiter=csv_delimiter)


def read_data_from_file(data_format: str, abs_file_path: str, with_head: bool, using_ray: bool = False, csv_delimiter: str = ",") -> pa.RecordBatch:
    """
    """
    file_system = fs.LocalFileSystem()
    return read_data_from_filesystem(file_system=file_system, file_path=abs_file_path, with_head=with_head,
                                     using_ray=using_ray, csv_delimiter=csv_delimiter)


def write_data_to_file(data: pa.Table, abs_file_path: str, append: bool = False, with_head=True, csv_delimiter: str = ","):
    """
    """
    file_system = fs.LocalFileSystem()
    write_data_to_filesystem(file_system=file_system, data=data, file_path=abs_file_path, append=append,
                             with_head=with_head, csv_delimiter=csv_delimiter)


def filter_data_paths(prefix: str, file_infos, with_directory: bool = False, with_file_type_return: bool = False):
    """
    """
    data_paths = []
    if not file_infos:
        return data_paths

    for file_info in file_infos:
        # special file-types and directory
        if file_info.type == fs.FileType.File:
            if file_info.extension in ["csv", "txt", "csv.gz", "parquet", "gz.parquet"]:
                if with_file_type_return:
                    data_paths.append(
                        (f"{prefix}{file_info.path}", file_info.type))
                else:
                    data_paths.append(f"{prefix}{file_info.path}")
        else:
            if with_directory:
                if with_file_type_return:
                    data_paths.append(
                        (f"{prefix}{file_info.path}", file_info.type))
                else:
                    data_paths.append(f"{prefix}{file_info.path}")

    def get_ele_key(e):
        if isinstance(e, str):
            return e
        elif isinstance(e, tuple):
            return e[0]
        else:
            return e
    # sort by the path
    data_paths.sort(key=get_ele_key)
    return data_paths


def get_actual_data_files_from_filesystem(file_system, prefix: str, path: str):
    """
    """
    file_selector = fs.FileSelector(path, allow_not_found=True, recursive=True)
    file_infos = file_system.get_file_info(file_selector)
    return filter_data_paths(f"{prefix}/", file_infos, with_directory=False)


def get_actual_data_files_from_hdfs(abs_file_path: str):
    """
    """
    path_url = urlparse(abs_file_path)
    path_net = f"{path_url.scheme}://{path_url.netloc}"
    file_system = fs.HadoopFileSystem.from_uri(path_net)
    return get_actual_data_files_from_filesystem(file_system=file_system, prefix=path_net, path=path_url.path)


def get_actual_data_files_from_file(abs_file_path: str):
    """
    """
    file_system = fs.LocalFileSystem()
    return get_actual_data_files_from_filesystem(file_system=file_system, prefix="", path=abs_file_path)


def refetch_if_has_directory(data_protocol, file_list_with_type: list):
    """
    """
    actual_file_paths = []
    for file_path, file_type in file_list_with_type:
        if file_type == fs.FileType.File:
            actual_file_paths.append(file_path)
        else:
            if data_protocol == 'hdfs':
                sub_list = get_actual_data_files_from_hdfs(file_path)
            else:
                sub_list = get_actual_data_files_from_file(file_path)
            sub_list.sort()
            logging.info(f"refetch_if_has_directory {file_path} has data size {len(sub_list)}")
            actual_file_paths.append(sub_list)
    return actual_file_paths


def hash16(b):
    """
    """
    m = hashlib.blake2b(digest_size=16)
    m.update(b)
    return m.digest()


def get_hash(array, type_="str"):
    """
    :param array:
    :param type: "str", "int" or "bytes"
    :return:
    """
    if type_ == "str":
        return [hash16(bytes(x, encoding="utf8")) for x in array]
    elif type_ == "int":
        return [hash16(x.to_bytes(8, byteorder='big', signed=False)) for x in array]
    elif type_ == "bytes":
        return [hash16(x) for x in array]
    elif type_ == "float":
        return [hash16(struct.pack('f', x)) for x in array]
    else:
        raise Exception("{} is not supported.".format(type_))


def add_hash_mod(df, *args):
    """
    This method is the previous job of repartition and other operations which depend on mpc.
    args[0]: str, 'example_id'
    args[1]: int, n to compute mod
    args[2]: str, hashed_mod_column_name
    """
    def hash_value(df, n):
        # return list(map(lambda x: hash(x), df))
        def func_hash(x):
            if isinstance(x, str):
                new_x = zlib.crc32(bytes(x, encoding="utf8")) % n
            elif isinstance(x, int):
                new_x = zlib.crc32(x.to_bytes(
                    8, byteorder='big', signed=False)) % n
            elif isinstance(x, bytes):
                new_x = zlib.crc32(x) % n
            else:
                raise Exception("{} is not supported.".format(type(x)))

            return new_x

        return df.apply(func_hash)

    mod_v = hash_value(df.loc[:, args[0]], args[1])
    df.insert(0, args[2], mod_v)

    return df


def add_hash_column(df: pa.Table, hashed_column_name: str, to_hash_column_name: str, hashed_mod_column_name: str = None, hashed_mod_n: int = None):
    """
    """
    id_list = df.column(to_hash_column_name).to_pylist()
    if isinstance(id_list[0], str):
        hashed_id = get_hash(id_list, "str")
    elif isinstance(id_list[0], int):
        hashed_id = get_hash(id_list, "int")
    elif isinstance(id_list[0], bytes):
        hashed_id = get_hash(id_list, "bytes")
    elif isinstance(id_list[0], float):
        hashed_id = get_hash(id_list, "float")
    else:
        raise Exception("{} is not supported.".format(type(id_list[0])))

    # if need to repartition for the one data
    if hashed_mod_column_name and hashed_mod_n:
        # hashed_mod = [x[-1] % hashed_mod_n for x in hashed_id]
        hashed_mod = [int(bytes.hex(x), 16) % hashed_mod_n for x in hashed_id]
        df = df.add_column(0, hashed_mod_column_name,
                           pa.array(hashed_mod, type=pa.int16()))

    return df.add_column(0, hashed_column_name, pa.array(hashed_id, type=pa.binary()))

def repartition_for_existed_data(data_protocol: str, data_format: str, file_path: str, with_head: bool,
                                    example_id_name: str, data_block_count: int, csv_delimiter: str = ",", parallel: int = 2):
    """
    """
    logging.info(f"enter repartition_for_existed_data {data_format} {file_path}")
    if data_protocol == 'hdfs':
        dataset = read_data_from_hdfs(
            data_format, file_path, with_head, True, csv_delimiter=csv_delimiter)
    elif data_protocol == 'nfs':
        dataset = read_data_from_file(
            data_format, file_path, with_head, True, csv_delimiter=csv_delimiter)
    else:
        raise ValueError("can not support repartition for single file")

    logging.info(f"repartition_for_existed_data data read all {data_format} {file_path}")

    real_example_id_name = get_actual_column_name(
        dataset, example_id_name, 0)
    
    @ray.remote
    class FileWriter:
        def __init__(self, names, enmpty_batch) -> None:
            self.names = names
            for name in self.names:
                write_data_to_file(enmpty_batch, name, append=True,
                               with_head=with_head, csv_delimiter=csv_delimiter)

        def write(self,batch):
            write_data_to_file(batch[0], self.names[batch[1]], append=True,
                               with_head=False, csv_delimiter=csv_delimiter)

    bucket_files = [f"/tmp/bucket-{i}" for i in range(data_block_count)]
    enmpty_batch = pa.RecordBatch.from_arrays(
                    [pa.array([], type=typ) for typ in dataset.schema().types],
                    schema=dataset.schema().base_schema
                    )
    
    bucket_files_split = np.array_split(bucket_files, parallel)
    bucket_writers = [FileWriter.remote(bucket_files_split[i], enmpty_batch) for i in range(parallel)]
    boundaries = np.cumsum([len(sub_arr) for sub_arr in bucket_files_split])
    class hash_split:
        def __init__(self):
            pass

        def __call__(self,batch):
            batch = add_hash_column(
                batch, hashed_column_name=STATIC_HASHED_VALUE_COLUMN_NAMES, to_hash_column_name=real_example_id_name)

            hash_mod_n = np.array([int(bytes.hex(value.as_py()), 16) %
                                data_block_count for value in batch.column(STATIC_HASHED_VALUE_COLUMN_NAMES)])
            batch = batch.drop([STATIC_HASHED_VALUE_COLUMN_NAMES])
            sp_batchs = [batch.filter(pa.array(hash_mod_n == i))
                        for i in range(data_block_count)]

            for i, sp_batch in enumerate(sp_batchs):
                sub_array_index = np.where(boundaries > i)[0][0]
                new_index = i - (0 if sub_array_index == 0 else boundaries[sub_array_index - 1])
                ray.get(bucket_writers[sub_array_index].write.remote((sp_batch, new_index)))

            return pa.table({})

    logging.info("parallel require:{} Available Source:{}".format(parallel, ray.available_resources()))
    nouse = dataset.map_batches(
        hash_split,
        batch_size=1000_000,
        batch_format="pyarrow",
        concurrency=(1,parallel),
    )

    dataset = None
    nouse.take()
    nouse = None

    for bucket_writer in bucket_writers:
        ray.kill(bucket_writer, no_restart=True)

    logging.info(f"repartition_for_existed_data {bucket_files} {bucket_files[0]}")

    return bucket_files

def has_column_by_name(data, column_name: str):
    """
    """
    column_names = data.column_names
    if column_name in column_names:
        return True
    else:
        return False


def get_column_name_by_index(data, column_index: int):
    """
    """
    if isinstance(data, pa.Table):
        column_names = data.column_names
    else:
        column_names = data.schema().names

    return column_names[column_index]


def get_actual_column_name(data, column_name_or_index, added_columns: int = 0):
    """
    """
    if isinstance(column_name_or_index, int):
        real_column_name = get_column_name_by_index(
            data, column_name_or_index + added_columns)
    else:
        real_column_name = column_name_or_index
    return real_column_name


#
STATIC_HASHED_VALUE_COLUMN_NAMES = "___hashed_id"
#
STATIC_HASHED_MOD_VALUE_COLUMN_NAMES = "___hashed_mod_id"


@ray.remote
class BlockDispatchActor:
    """
    """

    def __init__(self, app_id: str, data_dir_or_path: str, with_head: bool = True,
                 data_protocol: str = None, data_format: str = None, data_block_count_if_file: int = None, example_id_name: Union[str, int] = None) -> None:
        """
        """
        #
        self.app_id = app_id
        # 文件协议, 如HDFS, NFS, OSS等
        self.data_protocol = data_protocol
        # 传入的基础路径
        self.base_path = data_dir_or_path
        #
        self.with_head = with_head
        # 数据文件全路径
        self.data_paths = []
        # 文件格式, 如csv, txt, csv.gz, gz.parquet, 不区分大小写
        if data_format:
            self.data_format = data_format.lower()
        else:
            self.data_format = None
        # 对于单文件, 存放数据切分blocks
        #
        self.data_block_count = data_block_count_if_file
        self.data_blocks = []
        # block_status, origin and joined (rows, total_feature_values, total_null_values)
        self.data_status = []
        #
        self.data_type = fs.FileType.File
        #
        self.example_id_name = example_id_name

        init_logging(app_id=app_id, custom_node_id="dispatcher")

    def init_data(self) -> None:
        """
        """
        self._prepare_data_protocol_and_format()
        self._init_data_path_and_format()

    def get_results(self) -> tuple:
        """
        """
        total_origin_rows = 0
        total_origin_feature_values = 0
        total_origin_null_values = 0

        total_joined_rows = 0
        total_joined_feature_values = 0
        total_joined_null_values = 0

        for e in self.data_status:
            if e[1]:
                total_origin_rows += e[1][0]
                total_origin_feature_values += e[1][1]
                total_origin_null_values += e[1][2]
            if e[2]:
                total_joined_rows += e[2][0]
                total_joined_feature_values += e[2][1]
                total_joined_null_values += e[2][2]

        return ((total_origin_rows, total_origin_feature_values, total_origin_null_values),
                (total_joined_rows, total_joined_feature_values, total_joined_null_values))

    def get_data_protocol(self) -> str:
        """
        """
        return self.data_protocol

    def get_data_format(self) -> str:
        """
        """
        return self.data_format

    def get_data_type(self) -> fs.FileType:
        """
        """
        return self.data_type

    def update_blocks(self, data_blocks):
        """
        """
        self.data_blocks = data_blocks

    def get_data_block_count(self) -> int:
        """
        """
        return len(self.data_status)

    def get_next_unprocessed_data_and_use(self):
        """
        """
        find_index = -1
        for i, status in enumerate(self.data_status):
            if status[0] == BlockStatus.INIT:
                find_index = i
                break

        if find_index < 0:
            return None

        self.data_status[find_index] = (
            BlockStatus.PREPARED, self.data_status[find_index][1], self.data_status[find_index][2])

        if self._is_data_single_file():
            data = self.data_blocks[find_index]
            self.data_blocks[find_index] = None
            return (find_index, data)
        else:
            return (find_index, self.data_paths[find_index])

    def change_data_status(self, block_id: int, status: BlockStatus,
                           original_data_status: tuple, joined_data_status: tuple) -> None:
        """
        """
        if block_id < 0 or block_id >= len(self.data_status):
            raise ValueError(f"block_id should >= 0 and <{len(self.data_status)}")
        self.data_status[block_id] = (
            status, original_data_status, joined_data_status)

    def output_single_output_file(self, data_output_refs, output_dir: str, csv_delimiter: str = ","):
        """
        """
        if not data_output_refs:
            return

        infered_protocol = infer_protocol(output_dir)

        # 对于parquet, 需要先进行数据合并
        if self.data_format in ['parquet', 'gz.parquet']:
            data_outputs = ray.get(data_output_refs)
            all_table = pa.concat_tables(data_outputs)
            if infered_protocol == 'hdfs':
                write_data_to_hdfs(all_table, output_dir,
                                   False, self.with_head, csv_delimiter)
            else:
                write_data_to_file(all_table, output_dir,
                                   False, self.with_head, csv_delimiter)
        else:
            for i, data_output_ref in enumerate(data_output_refs):
                if data_output_ref is None:
                    logging.info(f"output_single_output_file block={i} with null data")
                    continue
                data_output = ray.get(data_output_ref)
                if data_output is None:
                    logging.info(f"output_single_output_file block={i} with null data")
                    continue

                logging.info(f"output_single_output_file block={i} with valid data")
                if i == 0:
                    with_head = True
                    append = False
                else:
                    with_head = False
                    append = True

                # if global no head, all no head
                if not self.with_head:
                    with_head = False

                if infered_protocol == 'hdfs':
                    write_data_to_hdfs(data_output, output_dir,
                                       append, with_head, csv_delimiter)
                else:
                    write_data_to_file(data_output, output_dir,
                                       append, with_head, csv_delimiter)

    def _init_data_path_and_format(self) -> None:
        """
        """
        if self.data_protocol == "hdfs":
            self._init_data_path_and_format_with_hdfs()
        elif self.data_protocol == "http":
            self._init_data_path_and_format_with_http()
        else:
            self._init_data_path_and_format_with_locals()

    def _init_data_path_and_format_with_hdfs(self) -> None:
        """
        """
        path_url = urlparse(self.base_path)
        path_net = f"{path_url.scheme}://{path_url.netloc}"
        file_system = fs.HadoopFileSystem.from_uri(path_net)
        file_info = file_system.get_file_info(path_url.path)
        if file_info.type == fs.FileType.File:
            self.data_paths = [path_url.path]
            self.data_type = fs.FileType.File
        else:
            file_selector = fs.FileSelector(
                path_url.path, allow_not_found=True, recursive=False)
            file_infos = file_system.get_file_info(file_selector)
            self.data_type = fs.FileType.Directory
            v_data_paths = filter_data_paths(
                f"{path_net}", file_infos, with_directory=True, with_file_type_return=True)
            self.data_paths = refetch_if_has_directory(
                self.data_protocol, v_data_paths)
        #
        #
        if len(self.data_paths) > 0:
            if isinstance(self.data_paths[0], str):
                self.data_format = infer_format(self.data_paths[0])
                # sort if all str
                self.data_paths.sort()
            if isinstance(self.data_paths[0], list):
                self.data_format = infer_format(self.data_paths[0][0])
        else:
            raise ValueError(f"no data to processing")

        if self._is_data_single_file():
            self.data_status = [
                (BlockStatus.INIT, None, None)] * self.data_block_count
        else:
            self.data_status = [
                (BlockStatus.INIT, None, None)] * len(self.data_paths)
        logging.info(f"original data size = {len(self.data_paths)}")

    def _init_data_path_and_format_with_http(self) -> None:
        """
        """
        raise ValueError("not support protocol http")

    def _init_data_path_and_format_with_locals(self) -> None:
        """
        """
        file_system = fs.LocalFileSystem()
        file_info = file_system.get_file_info(self.base_path)
        if file_info.type == fs.FileType.File:
            self.data_paths = [self.base_path]
            self.data_type = fs.FileType.File
        else:
            file_selector = fs.FileSelector(
                self.base_path, allow_not_found=True, recursive=False)
            file_infos = file_system.get_file_info(file_selector)
            self.data_type = fs.FileType.Directory
            v_data_paths = filter_data_paths(
                "", file_infos, with_directory=True, with_file_type_return=True)
            self.data_paths = refetch_if_has_directory(
                self.data_protocol, v_data_paths)

        #
        if len(self.data_paths) > 0:
            if isinstance(self.data_paths[0], str):
                self.data_format = infer_format(self.data_paths[0])
            if isinstance(self.data_paths[0], list):
                self.data_format = infer_format(self.data_paths[0][0])
        else:
            raise ValueError(f"no data to processing")

        # sort ascending
        self.data_paths.sort()
        if self._is_data_single_file():
            self.data_status = [
                (BlockStatus.INIT, None, None)] * self.data_block_count
        else:
            self.data_status = [
                (BlockStatus.INIT, None, None)] * len(self.data_paths)
        logging.info(f"original data = {self.data_paths}")

    def _is_data_single_file(self):
        """
        """
        return self.data_type == fs.FileType.File

    def _prepare_data_protocol_and_format(self):
        """
        """
        # protocol
        infered_protocol = infer_protocol(self.base_path)

        if self.data_protocol:
            ind_protocol = self.data_protocol.lower()
            if ind_protocol not in ["hdfs", "nfs", "http"]:
                raise ValueError("data protocol must in hdfs,nfs,http!")
            if ind_protocol != infered_protocol:
                raise ValueError(f"data protocol {ind_protocol} not matched with infer protocol {infered_protocol}")

        self.data_protocol = infered_protocol


class JoinType(Enum):
    """
    """
    PSI = 1
    PIR = 2


class RoleType(Enum):
    """
    """
    SENDER = 1
    RECEIVER = 2


@ray.remote
class PsiPirActor:
    """
    """

    def __init__(self, actor_index: int, dispatch_actor: BlockDispatchActor, example_id_name: Union[str, int],
                 with_head: bool, join_type: JoinType, role: RoleType, data_type: str, app_id: str,
                 local_address: int, proxy_address: str, local_domain: str, remote_domain: str, redis_addr: str = "", redis_pwd: str = "", engine:str = "ecdh") -> None:
        """
        """
        #
        self.actor_index = actor_index
        #
        self.dispatch_actor = dispatch_actor
        #
        self.example_id_name = example_id_name
        #
        self.with_head = with_head
        #
        self.join_type = join_type
        #
        self.role = role
        #
        self.data_type = data_type
        #
        self.app_id = app_id
        #
        self.local_address = local_address
        #
        self.proxy_address = proxy_address
        #
        self.local_domain = local_domain
        #
        self.remote_domain = remote_domain
        #
        self.redis_addr = redis_addr
        #
        self.redis_pwd = redis_pwd

        self.engine = engine
        init_logging(app_id=self.app_id,
                     custom_node_id=f"worker-{actor_index}")

    def do_action(self, block_id: int, data_protocol: str, data_format: str, block_data, output_path: str, csv_delimiter: str = ",") -> None:
        """
        """
        logging.info(f"do_action begin read block_id={block_id} actor_index={self.actor_index}")
        self._sync_block_status(block_id, BlockStatus.PROCSSING, None, None)
        recTableData, real_example_id_name = self._read_block_data(
            data_protocol, data_format, block_data, csv_delimiter)

        # generate empty result
        recBatchOutSchema = recTableData.schema
        recBatchOutColumns = recTableData.column_names
        recBatchOutDict = {}
        for name in recBatchOutColumns:
            recBatchOutDict[name] = []
        emptyRecBatchOut = pa.RecordBatch.from_pydict(
            mapping=recBatchOutDict, schema=recBatchOutSchema)

        # drop example_id if PIR and sender to prevent this column send to receiver
        if self.role == RoleType.SENDER and self.join_type == JoinType.PIR:
            recTableData = recTableData.drop([real_example_id_name])
        #
        original_data_status = self._count_values(
            recTableData, [STATIC_HASHED_VALUE_COLUMN_NAMES, real_example_id_name])

        # real action
        recTableDataBatches = recTableData.to_batches()
        if len(recTableDataBatches) == 0:
            recBatchIn = emptyRecBatchOut
        else:
            recBatchIn = recTableDataBatches[0]
        logging.info(f"****do_action begin actual do block_id={block_id} actor_index={self.actor_index}"
                     f" join_type={self.join_type} original_data_status={original_data_status} rows={recBatchIn.num_rows}")
        if self.join_type == JoinType.PSI:
            recBatchOut = self._do_psi(block_id, recBatchIn)
        else:
            recBatchOut = self._do_pir(block_id, recBatchIn)

        logging.info(f"do_action begin actual write result for block_id={block_id} actor_index={self.actor_index}")

        if recBatchOut is None:
            recBatchOut = emptyRecBatchOut

        recBatchOut = pa.Table.from_batches([recBatchOut])
        joined_data_status = self._count_values(
            recBatchOut, [STATIC_HASHED_VALUE_COLUMN_NAMES, real_example_id_name])
        # if directory, write data directory
        if self.data_type == fs.FileType.Directory:
            logging.info(f"do_action direct write results for block_id={block_id} actor_index={self.actor_index} joined_data_status={joined_data_status}")
            self._write_block_data(block_id, data_format, recBatchOut,
                                   self.data_type, output_path, False, csv_delimiter)
            recBatchOut_ref = None
        else:
            # if file, first to cached it into ray
            logging.info(f"do_action indirect write results for block_id={block_id} actor_index={self.actor_index} joined_data_status={joined_data_status}")
            recBatchOut = recBatchOut.drop([STATIC_HASHED_VALUE_COLUMN_NAMES])
            recBatchOut_ref = ray.put(recBatchOut)
            logging.info(f"block_id={block_id} actor_index={self.actor_index}"
                         f"recBatchOut_ref={recBatchOut_ref} to file generated")

        self._sync_block_status(
            block_id, BlockStatus.PROCESSED, original_data_status, joined_data_status)
        logging.info(f"do_action end block_id={block_id} actor_index={self.actor_index}")
        return (self.actor_index, block_id, recBatchOut_ref)

    def _do_psi(self, block_id: int, block_data: pa.RecordBatch) -> pa.RecordBatch:
        """
        """
        if self.engine == "ecdh":
            from interconnection_psi.api import psi_receiver, psi_sender
            local_address = self.local_address
            task_id = '{}_{}'.format(self.app_id, block_id)
            if self.role == RoleType.SENDER:
                out_table = psi_sender(psi_in=block_data, task_id=task_id,
                                    local_address=local_address, remote_address=self.proxy_address,
                                    self_domain=self.local_domain, target_domain=self.remote_domain,
                                    redis_address=self.redis_addr, redis_password=self.redis_pwd,
                                    send_back=True)
            else:
                out_table = psi_receiver(psi_in=block_data, task_id=task_id,
                                        local_address=local_address, remote_address=self.proxy_address,
                                        self_domain=self.local_domain, target_domain=self.remote_domain,
                                        redis_address=self.redis_addr, redis_password=self.redis_pwd,
                                        send_back=True)
        else:
            import pyraypsi
            local_address = self.local_address
            task_id = '{}_{}'.format(self.app_id, block_id)
            if self.role == RoleType.SENDER:
                out_table = pyraypsi.psi_sender(psi_in=block_data, task_id=task_id,
                                    local_address=local_address, remote_address=self.proxy_address,
                                    self_domain=self.local_domain, target_domain=self.remote_domain,
                                    redis_address=self.redis_addr, redis_password=self.redis_pwd,
                                    send_back=True, mult_type=1, malicious=True)
            else:
                out_table = pyraypsi.psi_receiver(psi_in=block_data, task_id=task_id,
                                    local_address=local_address, remote_address=self.proxy_address,
                                    self_domain=self.local_domain, target_domain=self.remote_domain,
                                    redis_address=self.redis_addr, redis_password=self.redis_pwd,
                                    send_back=True, mult_type=1, malicious=True)

        return out_table

    def _do_pir(self, block_id: int, block_data: pa.RecordBatch) -> pa.RecordBatch:
        """
        """
        raise Exception("Not Support")

    def _read_block_data(self, data_protocol: str, data_format: str, block_data, csv_delimiter: str = ",") -> pa.Table:
        """
        """
        if isinstance(block_data, str):
            data_protocol = infer_protocol(block_data)
            # read block data
            if data_protocol == 'hdfs':
                data = read_data_from_hdfs(
                    data_format, block_data, self.with_head, False, csv_delimiter)
            else:
                data = read_data_from_file(
                    data_format, block_data, self.with_head, False, csv_delimiter)

            real_example_name = get_actual_column_name(
                data, self.example_id_name)
            data = self._convert_string_to_large_string(data)
        elif isinstance(block_data, list):
            if len(block_data) > 0:
                for i, sub_block_data in enumerate(block_data):
                    data_protocol = infer_protocol(sub_block_data)
                    if data_protocol == 'hdfs':
                        sub_data = read_data_from_hdfs(
                            data_format, sub_block_data, self.with_head, False, csv_delimiter)
                    else:
                        sub_data = read_data_from_file(
                            data_format, sub_block_data, self.with_head, False, csv_delimiter)

                    if i == 0:
                        data = sub_data
                    else:
                        data = pa.concat_tables([data, sub_data])
            real_example_name = get_actual_column_name(
                data, self.example_id_name)
            data = self._convert_string_to_large_string(data)
        else:
            data = block_data
            added_columns = 0
            if STATIC_HASHED_VALUE_COLUMN_NAMES in data.column_names:
                added_columns += 1
            if STATIC_HASHED_MOD_VALUE_COLUMN_NAMES in data.column_names:
                added_columns += 1
            # the data has add mod and hash_values at head
            real_example_name = get_actual_column_name(
                data, self.example_id_name, added_columns)

        if data.num_rows > 0 and data.num_columns > 0:
            # add hash column
            if not has_column_by_name(data, STATIC_HASHED_VALUE_COLUMN_NAMES):
                data = add_hash_column(
                    data, STATIC_HASHED_VALUE_COLUMN_NAMES, real_example_name)
            # remove hashed mod column
            if has_column_by_name(data, STATIC_HASHED_MOD_VALUE_COLUMN_NAMES):
                data = data.drop([STATIC_HASHED_MOD_VALUE_COLUMN_NAMES])

        # force combine
        data = data.combine_chunks()
        return data, real_example_name

    @staticmethod
    def _convert_string_to_large_string(ori_table: pa.Table) -> pa.Table:
        """
        """
        ori_schema = ori_table.schema
        for field_ind in range(ori_table.num_columns):
            old_field = ori_schema.field(field_ind)
            if old_field.type == pa.string():
                new_field = old_field.with_type(pa.large_string())
                ori_schema = ori_schema.set(field_ind, new_field)

            if old_field.type == pa.binary():
                new_field = old_field.with_type(pa.large_binary())
                ori_schema = ori_schema.set(field_ind, new_field)

        return ori_table.cast(ori_schema)

    def _write_block_data(self, block_id: int, data_format: str, block_data: pa.Table,
                          output_path_type: fs.FileType, output_base_path: str, append: bool = False, csv_delimiter: str = ",") -> None:
        """
        """
        # remove psi hashed id
        if block_data is None:
            return

        data = block_data.drop([STATIC_HASHED_VALUE_COLUMN_NAMES])

        if output_path_type == fs.FileType.Directory:
            output_path = f"{output_base_path}/part_{str(block_id).zfill(6)}.{data_format}"
        else:
            output_path = output_base_path

        infered_protocol = infer_protocol(output_path)
        if infered_protocol == 'hdfs':
            write_data_to_hdfs(data, output_path, append=append,
                               with_head=self.with_head, csv_delimiter=csv_delimiter)
        elif infered_protocol == 'nfs':
            write_data_to_file(data, output_path, append=append,
                               with_head=self.with_head, csv_delimiter=csv_delimiter)

        else:
            raise ValueError(f"not support data protocol {infered_protocol}")

    def _sync_block_status(self, block_id: int, status: BlockStatus,
                           original_data_status: tuple, joined_data_status: tuple) -> None:
        """
        """
        self.dispatch_actor.change_data_status.remote(
            block_id, status, original_data_status, joined_data_status)

    def _count_values(self, data: pa.Table, exludes_list):
        """
        """
        rows = data.num_rows
        total_feature_values = 0
        total_null_values = 0
        column_names = data.column_names
        for column_name in column_names:
            if column_name in exludes_list:
                continue
            else:
                column = data.column(column_name)
                total_feature_values += rows
                total_null_values += column.null_count
        return (rows, total_feature_values, total_null_values)


def generate_result(join_type, aggre_results, output_dir):
    """
    """
    ret_result = {}
    if join_type == JoinType.PSI:
        ret_result["local"] = aggre_results[0][0]
        ret_result["join"] = aggre_results[1][0]
        if aggre_results[0][0] <= 0:
            ret_result["percent"] = 0
        else:
            ret_result["percent"] = aggre_results[1][0] / aggre_results[0][0]
    else:
        if role_type == RoleType.SENDER:
            ret_result["local"] = aggre_results[0][0]
            ret_result["local_all_values"] = aggre_results[0][1]
            ret_result["local_null_values"] = aggre_results[0][2]
            #
            ret_result["join"] = 0
            ret_result["percent"] = 0
        else:
            ret_result["local"] = aggre_results[0][0]
            ret_result["local_all_values"] = aggre_results[0][1]
            ret_result["local_null_values"] = aggre_results[0][2]
            #
            ret_result["join"] = aggre_results[1][0]
            ret_result["join_all_values"] = aggre_results[1][1]
            ret_result["join_null_values"] = aggre_results[1][2]
            if aggre_results[0][0] <= 0:
                ret_result["percent"] = 0
            else:
                ret_result["percent"] = aggre_results[1][0] / \
                    aggre_results[0][0]

    ret_result["output"] = output_dir
    return ret_result


def add_schema_to_all_empty_blocks(blocks):
    from ray.data.block import BlockAccessor
    @ray.remote
    def extract_schema(block):
        block = BlockAccessor.for_block(block)
        if block.num_rows() > 0:
            return block.slice(0, 0, True)
        else:
            return None

    need_add_schema_block_indexs = []
    blocks_schema = ray.get([extract_schema.remote(block) for block in blocks])
    new_empty_block = None
    for i, schema in enumerate(blocks_schema):
        if schema is None:
            need_add_schema_block_indexs.append(i)
        else:
            new_empty_block = schema

    if new_empty_block is None:
        raise Exception(f"all blocks is empty")

    for need_add_schema_block_index in need_add_schema_block_indexs:
        blocks[need_add_schema_block_index] = ray.put(new_empty_block)

    return blocks

def run_app(app_id, join_type, data_path, example_id_name, with_head: bool, local_ip: str, local_start_port, remote_proxy_address, bucket_num,
            role_type, local_domain, remote_domain, redis_addr, redis_pwd, output_dir, max_actors=2, csv_delimiter=",", avg_actor_cpus=1.0, engine="ecdh"):
    dispatch_actor = BlockDispatchActor.remote(app_id=app_id, data_dir_or_path=data_path, with_head=with_head,
                                               data_block_count_if_file=bucket_num, example_id_name=example_id_name)
    # must wait data initialize complete
    ray.get(dispatch_actor.init_data.remote())

    # get init infos
    data_type = ray.get(dispatch_actor.get_data_type.remote())
    data_block_num = ray.get(dispatch_actor.get_data_block_count.remote())
    data_format = ray.get(dispatch_actor.get_data_format.remote())
    data_protocol = ray.get(dispatch_actor.get_data_protocol.remote())

    # if need partition file first
    if data_type == fs.FileType.File:
        data_blocks = repartition_for_existed_data(
            data_protocol, data_format, data_path, with_head, example_id_name, bucket_num, csv_delimiter, max_actors)
        # data_blocks = add_schema_to_all_empty_blocks(data_blocks)

        ray.get(dispatch_actor.update_blocks.remote(data_blocks))

    # adjust
    if data_block_num <= max_actors:
        max_actors = data_block_num

    # PsiPirActor.options(num_cpus=avg_actor_cpus)
    actors = [PsiPirActor.remote(actor_index=i, dispatch_actor=dispatch_actor, example_id_name=example_id_name,
                                 with_head=with_head, join_type=join_type, role=role_type, data_type=data_type,
                                 app_id=app_id, local_address=f"{local_ip}:{local_start_port+i}",
                                 proxy_address=remote_proxy_address if isinstance(
                                     remote_proxy_address, str) else f"0.0.0.0:{remote_proxy_address+i}",
                                 local_domain=local_domain, remote_domain=remote_domain,
                                 redis_addr=redis_addr, redis_pwd=redis_pwd, engine=engine,
                                 ) for i in range(max_actors)]
    data_output_refs = [None] * data_block_num
    result_refs = [None]*max_actors
    for i in range(max_actors):
        to_process_data = ray.get(
            dispatch_actor.get_next_unprocessed_data_and_use.remote())
        result_refs[i] = actors[i].do_action.remote(to_process_data[0], data_protocol, data_format,
                                                    to_process_data[1], output_dir, csv_delimiter=csv_delimiter)

    while True:
        # logging.info(f"to wait a job completion...")
        ready_refs, remaining_refs = ray.wait(
            result_refs, num_returns=1, timeout=15.0, fetch_local=True)
        if ready_refs:
            ready_result = ray.get(ready_refs)[0]
            completed_actor_index = ready_result[0]
            completed_block_id = ready_result[1]
            completed_block_data = ready_result[2]
            logging.info(f"one job complete, actor_index={completed_actor_index} block_id={completed_block_id}"
                         f" completed_block_data={completed_block_data} data_type={data_type}")

            if data_type == fs.FileType.File and completed_block_data:
                data_output_refs[completed_block_id] = completed_block_data

            to_process_data = ray.get(
                dispatch_actor.get_next_unprocessed_data_and_use.remote())
            if to_process_data:
                # add a queued job
                logging.info(f"queue next data block_id={to_process_data[0]}, data={to_process_data[1]}")
                result_refs[completed_actor_index] = actors[completed_actor_index].do_action.remote(to_process_data[0],
                                                                                                    data_protocol, data_format, to_process_data[1], output_dir, csv_delimiter=csv_delimiter)
            else:
                result_refs = remaining_refs

            # if no more jobs to wait
            if not result_refs:
                break

    # all data prepared
    if data_type == fs.FileType.File and completed_block_data:
        logging.info(f"write data of blocks={len(data_output_refs)} to single output file ={output_dir}")
        ray.get(dispatch_actor.output_single_output_file.remote(
            data_output_refs, output_dir, csv_delimiter))

    # result reporting
    aggre_results = ray.get(dispatch_actor.get_results.remote())
    return generate_result(join_type, aggre_results, output_dir)


def test_main(role):
    # dir
    # python3 psi_actors.py --input=/home/yongxinghui/Projects/test_data/xty_j --output=/home/yongxinghui/Projects/test_data/xty_j/joined/leader --input-col=example_id --proxy-listen=5232 --proxy-remote=5132 --target=jd,cu1 --party-id=jd --parallels=2 --cluster-id='jd_1111'
    # python3 psi_actors.py --input=/home/yongxinghui/Projects/test_data/xty_j --output=/home/yongxinghui/Projects/test_data/xty_j/joined/follower --input-col=example_id --proxy-listen=5132 --proxy-remote=5232 --target=jd,cu1 --party-id=cu1 --parallels=2 --cluster-id='jd_1111'
    # file
    # python3 psi_actors.py --input=/home/yongxinghui/Projects/test_data/xty_j_parquet/data_B_parquet_no_hashid --output=/home/yongxinghui/Projects/test_data/xty_j_parquet/joined_data_B --input-col=example_id --proxy-listen=5232 --proxy-remote=5132 --target=jd,cu1 --party-id=jd --parallels=1 --cluster-id='jd_1111' --buckets=2
    # python3 psi_actors.py --input=/home/yongxinghui/Projects/test_data/xty_j_parquet/data_A_parquet --output=/home/yongxinghui/Projects/test_data/xty_j_parquet/joined_data_A --input-col=example_id --proxy-listen=5132 --proxy-remote=5232 --target=jd,cu1 --party-id=cu1 --parallels=1 --cluster-id='jd_1111' --buckets=2

    pass


def create_argment_parser():
    """
    """
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", help="输入路径", type=str)
    parser.add_argument("--output", help="输出路径", type=str)
    parser.add_argument(
        "--cluster-id", help="任务ID, leader或follower必须是相同的值.", type=str)
    parser.add_argument("--log-level", help="日志级别", default="WARN")
    parser.add_argument("--input-col", help="求交列选择,数字表示列序号",
                        type=str, default='0')
    parser.add_argument("--proxy-listen", help="通信监听端口",
                        type=int, default=22020)
    parser.add_argument("--proxy-remote", help="通信外发目标地址")
    parser.add_argument(
        "--redis-server", help="通信注册redis服务地址", type=str, default="")
    parser.add_argument("--redis-password",
                        help="通信注册redis密码", type=str, default="")
    parser.add_argument(
        "--target", help="逗号分割的本次任务所有参与方的target地址。leader为第一个。所有参与方的设置应相同", type=str)
    parser.add_argument("--status-server", help="任务状态上报服务地址", type=str)
    parser.add_argument("--party-id", help="通信当前域名称", type=str)
    parser.add_argument("--parallels", help="求交时并行执行的桶个数", type=int, default=0)
    parser.add_argument("--work-mode", help="", type=str, default="pir")
    parser.add_argument(
        "--buckets", help="设置分桶策略。0为两侧协商最优分桶, 大于0的值为固定分桶数, 小于0为读取已分桶好的输入。默认为0.", type=int, default=0)
    parser.add_argument(
        "--csv-header", help="选择输入的csv文件是否包含header行, 默认为false", type=bool, default=True)
    parser.add_argument("--csv-delimiter", help="CSV文件分隔符",
                        type=str, default=",")
    parser.add_argument("--engine", help="求交引擎",
                        type=str, default="ecdh", choices=["ecdh", "rr22"])


    parser.add_argument("--supplier", help="选择输出的格式")
    parser.add_argument(
        "--split-only", help="仅执行分桶操作，输出分桶结果到--output 路径", default=False)
    parser.add_argument("--send-back", help="follower是否也得到求交结果", default=True)
    parser.add_argument("--retry-interval",
                        help="通信超时后的重发间隔,配置为string类型", default=30)
    parser.add_argument(
        "--retry-times", help="通信超时重发次数，超过重发次数后引起通信错误", default=60)
    parser.add_argument("--node-id", help="分布式执行的worker序号", default=0)
    parser.add_argument("--node-total", help="分布式执行的总worker数", default=1)
    parser.add_argument("--auth_server", help="防盗版验证服务", default=None)
    return parser


def check_auth(target, status_server, actual_app_id, auth_server=None):
    if auth_server is None:
        auth_server = os.environ.get('AUTH_SERVER')
    authclient = AuthClient()
    cluster_id = os.environ.get("CLUSTER_ID")
    node_id = os.environ.get("NODE_ID")
    local_domain = os.environ.get("LOCAL_DOMAIN")
    info = {
        "random": 0,  # 随机数
        "timestamp": 0,  # 当前时间戳,精确到秒
        "workerType": "psi",  # worker类型
        "workerVersion": "1.0.0",  # worker版本
        "taskInfo": {
            "clusterID": cluster_id,
            "nodeID": node_id,
            "localDomain": local_domain,  # 发起方domain
            "remoteDomain": target if target else local_domain,  # 接收方domain
            "extra": {}  # 任务额外信息
        }
    }
    logging.info("------{}-------".format(info))
    res = authclient.requestAuth(auth_server, info)
    if res[0] != True:
        set_node_status(status_server, actual_app_id, 0, 501,
                        result="{}, parameter server cannot run, ps authentication failed".format(res[1]))
        raise Exception(
            "{}, parameter server cannot run, ps authentication failed".format(res.args[0]))


if __name__ == "__main__":

    parser = create_argment_parser()
    args, _ = parser.parse_known_args()

    #
    cpu_count = int(os.environ.get('CPU')) if os.environ.get('CPU') else 1
    n_parallels = cpu_count if args.parallels == 0 else args.parallels
    n_parallels = int(n_parallels)
    if n_parallels > cpu_count:
        n_parallels = cpu_count

    avg_parallel_cpus = cpu_count / n_parallels

    #
    task_id = os.environ.get('TASK_ID')
    actual_app_id = task_id if task_id else args.cluster_id
    #
    init_logging(app_id=actual_app_id)
    #
    set_node_status(args.status_server, actual_app_id, 0, -1)

    env_domain = os.environ.get('LOCAL_DOMAIN')
    local_domain = env_domain if env_domain else args.party_id
    # leader as receiver and follower as sender
    targets = args.target.split(",")
    if targets[0] == local_domain:
        remote_domain = targets[1]
        role_type = RoleType.RECEIVER
    elif targets[1] == local_domain:
        remote_domain = targets[0]
        role_type = RoleType.SENDER

    for target in targets:
        if target != local_domain:
            remote_domain = target
            break

    try:
        remote_addr = int(args.proxy_remote)
    except:
        remote_addr = args.proxy_remote

    if args.work_mode == 'pir':
        join_type = JoinType.PIR
    else:
        join_type = JoinType.PSI

    if args.buckets == 0:
        actual_buckets = n_parallels
    else:
        actual_buckets = args.buckets

    actual_local_ip = os.environ.get('Local_IP')
    if not actual_local_ip:
        actual_local_ip = "0.0.0.0"
    try:
        column_index_or_names = args.input_col.split(",")
        if len(column_index_or_names) > 1:
            raise ValueError("input col only support single.")

        try:
            actual_example_id_name = int(column_index_or_names[0])
        except:
            actual_example_id_name = column_index_or_names[0]

        #
        # check_auth(args.target, args.status_server,
        #            actual_app_id, args.auth_server)

        # actual doing
        ray.init()
        ray.data.DataContext.get_current().execution_options.verbose_progress = True
        logging.info("Total Source:{}".format(ray.cluster_resources()))
        results = run_app(app_id=actual_app_id, join_type=join_type, data_path=args.input, example_id_name=actual_example_id_name,
                          with_head=args.csv_header, local_ip=actual_local_ip, local_start_port=args.proxy_listen, remote_proxy_address=remote_addr,
                          bucket_num=actual_buckets, role_type=role_type, local_domain=local_domain, remote_domain=remote_domain,
                          redis_addr=args.redis_server, redis_pwd=args.redis_password, output_dir=args.output, max_actors=n_parallels,
                          csv_delimiter=args.csv_delimiter, avg_actor_cpus=avg_parallel_cpus, engine=args.engine)

        logging.info(f"all jobs done results={results}")
        set_node_status(args.status_server, actual_app_id,
                        0, 0, result=json.dumps(results))
    except Exception as e:
        logging.error(f"error: {e}", exc_info=True)
        set_node_status(args.status_server, actual_app_id, 0, 500)
