FROM silverlogic/python3.8:latest
LABEL maintainer="chenghekai1 <chenghekai1@jd.com>"
USER root

RUN pip install --no-cache-dir -i https://mirrors.aliyun.com/pypi/simple  python-snappy==0.6.1  \
      flask redis protobuf pyarrow  && \
mkdir /app   && \
mkdir -p /mnt/transfile

COPY /app/redis_client.py  /app/redis_client.py
COPY /app/orc_pb2.py /app/orc_pb2.py
COPY start.sh /tmp/start.sh
COPY /app/file_service.py  /app/file_service.py 

ENTRYPOINT [ "sh","/tmp/start.sh" ]



