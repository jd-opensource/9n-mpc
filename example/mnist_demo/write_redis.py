#-*-encoding=utf-8-*-
import json
import redis
import logging
import sys
import argparse


logging.basicConfig(level = logging.INFO, \
    format = '%(asctime)s %(filename)s[line:%(lineno)d] %(levelname)s %(message)s', \
    stream = sys.stdout, \
    datefmt = '%a, %d %b %Y %H:%M:%S', \
    filemode = 'a')


class RedisManager(object):
    def __init__(self, db_config):
        self.pool = redis.ConnectionPool(**db_config)
        self.redis_cli = redis.Redis(connection_pool=self.pool)

    def redis_set_json(self, key, data):
        if not data:
            return data
        json_str = json.dumps(data)
        code = self.redis_cli.set(key, json_str)
        if not code:
            raise Exception('redis set failed, key: %s' % key)
        logging.info('set redis key: %s' % key)
        logging.info('set redis value: %s' % json_str)
        return key
        
    def redis_get_json(self, key):
        if not key:
            return key
        value = self.redis_cli.get(key)
        if not value:
            logging.info('redis get failed, key: %s' % key)
            return ''
        value = json.loads(str(value, encoding='utf-8'))
        return value


def load_model_json(args):
    """ 
    加载一个json文件
    """
    db_config = {
        "host": args.host,
        "port": args.port,
        "password": args.password,
    }
    logging.info(str(db_config))
    r = RedisManager(db_config)
    with open(args.fname, "r") as f:
        data = json.load(f)
        model_uri = data["conf_info"]["model_uri"]
        version = data["conf_info"]["version"]
        key = "%s-%s" % (model_uri, version)
        r.redis_set_json(key, data)



if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("-H", "--host", dest="host",
        default='127.0.0.1', help="redis host")
    parser.add_argument("-P", "--port", dest="port",
        type=int, default=6379, help="redis port")
    parser.add_argument("-p", "--passwd", dest="password",
        default="", help="redis port")
    parser.add_argument("-f", "--fname", dest="fname",
        default="", help="json file")
    args = parser.parse_args()
    load_model_json(args)
