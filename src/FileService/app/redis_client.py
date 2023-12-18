import os
import sys
import time

import redis
import traceback
import logging
import zlib

def singleton(cls, *args, **kw):
    _registry = dict()

    def _singleton(*args,**kw):
        key = str(cls) + str(os.getpid())
        if key not in _registry:
            _registry[key] = cls(*args, **kw)
        return _registry[key]

    return _singleton


@singleton
class RedisManage(object):
    redis_conf = None
    def __init__(self, redis_conf, compress = False):
        RedisManage.redis_conf = redis_conf
        self.redis_pool = redis.ConnectionPool(host=redis_conf['host'], port=redis_conf['port'],
                                               password=redis_conf['pwd'], \
                                               max_connections=10, db=0)
        self.use_compress = compress
        logging.info('init redis connection pool successfully.')

    def acquire_redis_conn(self):
        return redis.Redis(connection_pool=self.redis_pool, decode_responses=True)

    def get(self, key):
        try:
            redis_conn = self.acquire_redis_conn()
            value = redis_conn.get(key)
            if value:
                if self.use_compress:
                    value = zlib.decompress(value)
                return True, value
            else:
                return False, value
        except Exception as e:
            logging.error('get value from redis failed')
            traceback.print_exc(file=sys.stdout)
            return False, None

    def set(self, key, value, expire_seconds=108000 * 24 * 5):
        try:
            if self.use_compress:
                value = zlib.compress(value)
            redis_conn = self.acquire_redis_conn()
            max_retries = 3
            retry_interval = 1
            retries = 0
            while retries < max_retries:
                try:
                    with redis_conn.pipeline() as pipe:
                        pipe.set(key, value)
                        pipe.execute()
                    return True
                    # redis_conn.setex(key, expire_seconds, value)
                except redis.exceptions.ConnectionError:
                    retries += 1
                    logging.info("写入redis失败，正在进行第" + retries + "次重试")
                    time.sleep(retry_interval)
            return False
        except Exception as e:
            logging.info('set {}:{} {} into redis failed.'.format(key, value, expire_seconds))
            traceback.print_exc(file=sys.stdout)

    def hset(self, key, field, value):
        try:
            if self.use_compress:
                value = zlib.compress(value)
            redis_conn = self.acquire_redis_conn()
            redis_conn.hset(key, field, value)
        except Exception as e:
            logging.info('hset {}:{} {} into redis failed.'.format(key, field, value))
            traceback.print_exc(file=sys.stdout)

    def hget(self, key, field):
        try:
            redis_conn = self.acquire_redis_conn()
            value = redis_conn.hget(key, field)
            if self.use_compress:
                value = zlib.decompress(value)
            return True, value
        except Exception as e:
            logging.info('hget {}:{} {} into redis failed.'.format(key, field))
            traceback.print_exc(file=sys.stdout)
            return False, None

    # *field: eg:
    def hdel(self, key, *field):
        try:
            redis_conn = self.acquire_redis_conn()
            redis_conn.hdel(key, field, *field)
        except Exception as e:
            logging.info('hdel {}:{} {} into redis failed.'.format(key, *field))
            traceback.print_exc(file=sys.stdout)


    def delete(self, *key):
        try:
            redis_conn = self.acquire_redis_conn()
            redis_conn.delete(*key)
        except Exception as e:
            logging.info('del {} from redis failed.'.format(*key))
            traceback.print_exc(file=sys.stdout)


