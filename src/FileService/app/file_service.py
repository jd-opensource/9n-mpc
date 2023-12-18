#!/usr/bin/python3
# -*- coding: UTF-8 -*-
import logging
import os
import socket
import uuid
import gzip
import csv
from concurrent.futures import ThreadPoolExecutor
from flask import Flask, request, jsonify
from pyarrow import fs
import orc_pb2
import pyarrow.parquet as pq

from redis_client import RedisManage

WORKDIR = "/mnt/data/transfile"
LOG_FILE = 'file_services.log'

logging.basicConfig(
    level=logging.DEBUG,
    filename=LOG_FILE,
    filemode='a',
    format='%(asctime)s - %(pathname)s[line:%(lineno)d] - %(levelname)s: %(message)s'
)

redis_host = os.environ.get('REDIS_HOST')
redis_port = os.environ.get('REDIS_PORT')
redis_password = os.environ.get('REDIS_PASSWORD')

server = Flask(__name__)

executor = ThreadPoolExecutor(8)

def get_host_ip():
    ip = os.environ.get("LOCAL_IP", "null")
    if ip == "null":
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(('8.8.8.8', 80))
            ip = s.getsockname()[0]
        finally:
            s.close()
    return ip

@server.route('/api/file/mkdirs', methods=['GET'])
def mkdirs():
    try:
        path = request.args.get('path')
        path_array = path.split(',')
        path_return = []
        for path_i in path_array:
            logging.info("cfs path_i: " + path_i)
            if os.path.exists(path_i):
                logging.info("目录" + path_i + "已存在")
            else:
                os.makedirs(path_i)
            path_return.append(path_i)
            logging.info("path_return: " + str(path_return))
        logging.info("final path_return: " + str(path_return))
        result = {
            "status": 0,
            "errMsg": "",
            "result": path_return
        }
        return result
    except Exception as e:
        logging.info("error: " + str(path_return))
        return {
            "status": 1,
            "errMsg": str(e),
            "result": False
        }

@server.route('/api/file/getSize', methods=['GET'])
def getSize():
    try:
        path = request.args.get('path')
        logging.info("path is {}".format(path))
        result_size = 0
        if os.path.isdir(path):
            result_size = sum(
                os.path.getsize(os.path.join(root, file))
                for root, dirs, files in os.walk(path)
                for file in files
                if os.path.getsize(os.path.join(root, file)) > 0
            )
        else:
            result_size = os.path.getsize(path)
        logging.info("not hdfs result_size is {}".format(result_size))
        result = {
            "status": 0,
            "errMsg": "",
            "result": result_size
        }
        return result
    except Exception as e:
        return {
            "status": 1,
            "errMsg": "",
            "result": 0
        }

def is_valid_dir(path, file_suffixes):
    if os.path.isfile(path) and os.path.splitext(path)[1][1:] in file_suffixes:
        return True
    if os.path.isdir(path):
        for filename in os.listdir(path):
            subpath = os.path.join(path, filename)
            if is_valid_dir(subpath, file_suffixes):
                return True
    return False

@server.route('/api/file/getRawDataFiles', methods=['GET'])
def getRawDataFiles():
    result = {"status": 0, "result": [], "errMsg": ""}
    try:
        path = request.args.get('path')
        file_suffixes = request.args.getlist('fileSuffixes')

        if not os.path.exists(path):
            result["status"] = 1
            result["errMsg"] = "path目录不存在！"
            return jsonify(result)

        if not os.path.isdir(path):
            result["status"] = 1
            result["errMsg"] = "path不是一个目录！"
            return jsonify(result)

        all_names = os.listdir(path)
        for name in all_names:
            name_ori = name
            name = os.path.join(path, name)
            if os.path.isfile(name):
                if name.split(".")[-1] in file_suffixes:
                    res = {"name": name_ori, "path": name}
                    result["result"].append(res)
            if os.path.isdir(name):
                if is_valid_dir(name, file_suffixes):
                    res = {"name": name_ori, "path": name}
                    result["result"].append(res)
        return jsonify(result)
    except Exception as e:
        result["status"] = 1
        result["errMsg"] = str(e)
        return jsonify(result)

def process_csv_cfs_heads_size(path):
    with open(path, encoding='utf-8') as f:
        heads = f.readline()
    result_heads = heads.strip().split(',')
    result_col = len(heads.split(','))
    result_size = os.path.getsize(path)
    return result_heads, result_col, result_size

def process_orc_cfs_heads_size(path):
    footer = orc_pb2.get_orc_footer_from_path(path)
    headers = orc_pb2.parse_orc_header(footer)
    result_col = len(headers)
    result_heads = headers
    result_size = os.path.getsize(path)
    return result_heads, result_col, result_size

def infer_format(file_path):
    lower_base_path = file_path.lower()
    if lower_base_path.endswith(".csv") or lower_base_path.endswith(".txt"):
        return "csv"
    elif lower_base_path.endswith(".gz"):
        return "gz"
    elif lower_base_path.endswith(".gz.parquet"):
        return "gz.parquet"
    elif lower_base_path.endswith(".parquet"):
        return "parquet"
    elif lower_base_path.endswith(".orc"):
        return "orc"
    else:
        return "csv"

def get_csv_gz_heads(filename):
    with gzip.open(filename, 'rt') as file:
        reader = csv.reader(file)
        heads = next(reader)
    return heads

def process_getTableInfo(path):
    table_info = {"status": 0, "result": {"heads": "", "row": 0, "col": 0, "size": 0, "format": 0}, "errMsg": ""}
    try:
        if os.path.isdir(path):
            result_size = 0
            flag = True
            for root, dirs, files in os.walk(path):
                for name in files:
                    file_path = os.path.join(root, name)
                    if os.path.getsize(file_path) == 0:
                        continue
                    if flag:
                        if infer_format(file_path) == "csv":
                            table_info["result"]["heads"], table_info["result"]["col"], _ = process_csv_cfs_heads_size(file_path)
                            table_info["result"]["format"] = 1
                        elif infer_format(file_path) == "orc":
                            table_info["result"]["heads"], table_info["result"]["col"], _ = process_orc_cfs_heads_size(file_path)
                            table_info["result"]["format"] = 2
                        elif infer_format(file_path) == "parquet":
                            file_system = fs.LocalFileSystem()
                            schemedata = pq.read_metadata(file_path, filesystem=file_system)
                            table_info["result"]["format"] = 3
                            table_info["result"]["heads"] = schemedata.schema.names
                            table_info["result"]["col"] = schemedata.num_columns
                        elif infer_format(file_path) == "csv.gz":
                            table_info["result"]["format"] = 4
                            table_info["result"]["heads"] = get_csv_gz_heads(file_path)
                            table_info["result"]["col"] = len(table_info["result"]["heads"])
                    flag = False
                    result_size += os.path.getsize(file_path)
            table_info["result"]["size"] = result_size
        else:
            if infer_format(path) == "csv":
                table_info["result"]["heads"], table_info["result"]["col"], table_info["result"]["size"] = process_csv_cfs_heads_size(path)
                table_info["result"]["format"] = 1
            elif infer_format(path) == "orc":
                table_info["result"]["heads"], table_info["result"]["col"], table_info["result"]["size"] = process_orc_cfs_heads_size(path)
                table_info["result"]["format"] = 2
            elif infer_format(path) == "parquet":
                file_system = fs.LocalFileSystem()
                schemedata = pq.read_metadata(path, filesystem=file_system)
                table_info["result"]["format"] = 3
                table_info["result"]["heads"] = schemedata.schema.names
                table_info["result"]["col"] = schemedata.num_columns
            elif infer_format(path) == "gz":
                table_info["result"]["format"] = 4
                table_info["result"]["heads"] = get_csv_gz_heads(path)
                table_info["result"]["col"] = len(table_info["result"]["heads"])
            table_info["result"]["size"] = os.path.getsize(path)
        if len(table_info["result"]["heads"]) == 0:
            table_info["status"] = 1
            table_info["errMsg"] = "The heads is empty"
        return table_info
    except Exception as e:
        logging.error("{}".format(e))
        table_info["status"] = 1
        table_info["errMsg"] = str(e)
        return table_info

@server.route('/api/file/getTableInfo', methods=['GET'])
def getTableInfo():
    try:
        path = request.args.get('path')
        logging.info("get table info on local file system")
        result = executor.submit(process_getTableInfo, path)
        return result.result()
    except Exception as e:
        logging.error("{}".format(e))
        table_info = {"status": 1, "result": {}, "errMsg": str(e)}
        return table_info

@server.route("/api/file/upload/<saveType>/<customerId>/<projectId>", methods=['POST'])
def upload(saveType, customerId, projectId):
    logging.info("start upload!")
    try:
        file = request.files.get('file')

        if file is None:
            raise ValueError("file is null")

        file_name = file.filename

        if saveType == 'cfs':
            filedir = os.path.join(WORKDIR, 'file')
            if not os.path.exists(filedir):
                os.makedirs(filedir)
            path = os.path.join(filedir, "{}.{}" .format(uuid.uuid4().hex, (file_name).split(".")[-1]))
            file.save(path)
            logging.info("finish upload :{}".format(path))
            return {"path": path}
    except Exception as e:
        return {
            "status": 1,
            "errMsg": str(e),
            "result": False
        }

if __name__ == "__main__":
    if not os.path.exists(WORKDIR):
        os.makedirs(WORKDIR)

    redis_conf = {
        "host": redis_host,
        "port": redis_port,
        "pwd": redis_password
    }
    _redis_cli = RedisManage(redis_conf)

    _redis_cli.set("file-service-addr", "{}:{}".format(get_host_ip(), "8800"))
    _redis_cli.set("file-service-addr::mart_mpc_data_serv_biz", "{}:{}".format(get_host_ip(), "8800"))

    server.run(port=8800, debug=True, host='0.0.0.0')
