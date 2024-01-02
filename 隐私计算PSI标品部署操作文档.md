# 隐私计算PSI标品部署操作文档
## 概述
本文档是京东隐私计算PSI产品部署操作文档，用于非京东侧（客户侧）协作方工程师部署操作引导。
## 1 环境要求
- K8S集群

    - 机器操作系统centos7.6-7.9
    - 机器最低规格8c16g

- NFS云存储/PVC
- 公网域名

## 2 基础组件

![img.png](img.png)


<table>
  <tr>
    <th>组件</th>
    <th>硬件需求</th>
    <th>镜像</th>
    <th>提供方</th>
    <th>基础镜像</th>
  </tr>
  <tr>
    <td>redis</td>
    <td>0.5c1g</td>
    <td>tianwen3/redis:v3.0</td>
    <td>dockerhub</td>
    <td>redis:latest  redis版本7.0.5</td>
  </tr>
  <tr>
    <td>mysql</td>
    <td>0.5c1g</td>
    <td>mysql_debian_8.0.31</td>
    <td>dockerhub</td>
    <td>-</td>
  </tr>
  <tr>
    <td>proxy</td>
    <td>1c2g</td>
    <td>tianwen3/proxy:v4.1</td>
    <td>dockerhub</td>
    <td>ubuntu:22.04</td>
  </tr>
 <tr>
    <td>nacos</td>
    <td>2c4g</td>
    <td>nacos/nacos-server:v2.2.3</td>
    <td>dockerhub</td>
    <td>-</td>
  </tr>
  <tr>
    <td>fileservice</td>
    <td>1c4g</td>
    <td>fileservice0.7-opensource</td>
    <td>自主生成</td>
    <td>-</td>
  </tr>
  <tr>
    <td>coordinator</td>
    <td>4c8g</td>
    <td>coordinator-opensource</td>
    <td>JD</td>
    <td>-</td>
  </tr>
  <tr>
    <td>psi</td>
    <td>4c16g</td>
    <td>psi-opensource</td>
    <td>自主生成</td>
    <td>-</td>
  </tr>
</table>

以上组件需要按顺序部署，其中proxy，psi，redis是用于执行隐私求交所需的必须镜像，nacos、mysql、coordinator是用于接入京东平台所需。

## 3 预定操作
- 创建K8S namespace，建议格式NAMESPACE=“mpc-$COMPANY-cu”
- 设定通信proxy_target，建议与K8S namespace相同，即PROXY_TARGET="mpc-$COMPANY-cu"

## 4 redis

1. 创建redis配置configmap
- REDIS_POD_PORT=6379
- REDIS_PASSWORD
- REDIS_CONF=redis

```
apiVersion: v1
kind: ConfigMap
metadata:
  name: redis
data:
  redis.conf: |
    bind 0.0.0.0
    protected-mode no
    port $REDIS_POD_PORT
    tcp-backlog 511
    timeout 0
    tcp-keepalive 300
    daemonize no
    supervised no
    pidfile /var/run/redis_6379.pid
    loglevel notice
    logfile /data/redis.log
    databases 16
    save 900 1
    save 300 10
    save 60 10000
    stop-writes-on-bgsave-error yes
    rdbcompression yes
    rdbchecksum yes
    dbfilename dump.rdb
    dir /data
    slave-serve-stale-data yes
    slave-read-only yes
    repl-diskless-sync no
    repl-diskless-sync-delay 5
    repl-disable-tcp-nodelay no
    slave-priority 100
    requirepass $REDIS_PASSWORD
    rename-command FLUSHALL ""
    rename-command FLUSHDB ""
    rename-command KEYS ""
    appendonly no
    appendfilename "appendonly.aof"
    appendfsync everysec
    no-appendfsync-on-rewrite no
    auto-aof-rewrite-percentage 100
    auto-aof-rewrite-min-size 64mb
    aof-load-truncated yes
    lua-time-limit 5000
    slowlog-log-slower-than 10000
    slowlog-max-len 128
    latency-monitor-threshold 0
    notify-keyspace-events ""
    hash-max-ziplist-entries 512
    hash-max-ziplist-value 64
    list-max-ziplist-size -2
    list-compress-depth 0
    set-max-intset-entries 512
    zset-max-ziplist-entries 128
    zset-max-ziplist-value 64
    hll-sparse-max-bytes 3000
    activerehashing yes
    client-output-buffer-limit normal 0 0 0
    client-output-buffer-limit slave 256mb 64mb 60
    client-output-buffer-limit pubsub 32mb 8mb 60
    hz 10
    aof-rewrite-incremental-fsync yes

```

2. 创建redis deployment
- REDIS_IMAGE
- REDIS_POD_PORT=6379
- REDIS_VOLUME_PATH
- REDIS_CONF=redis
```
apiVersion: apps/v1
kind: Deployment
metadata:
  name: redis
spec:
  replicas: 1
  selector:
    matchLabels:
      app: redis
  template:
    metadata:
      labels:
        app: redis
    spec:
      containers:
        - name: redis
          image: $REDIS_IMAGE
          ports:
            - containerPort: $REDIS_POD_PORT
          resources:
            limits:
              cpu: "1"
              memory: "1Gi"
            requests:
              cpu: "250m"
              memory: "256Mi"
          volumeMounts:
            - name: redis-volume
              mountPath: /data
            - name: redis-conf
              mountPath: /etc/redis.conf
              subPath: redis.conf
              readOnly: true
      volumes:
        - hostPath:
            path: $REDIS_VOLUME_PATH
            type: DirectoryOrCreate
          name: redis-volume
        - name: redis-conf
          configMap:
            name: $REDIS_CONF
```

3. 创建redis service
- REDIS_POD_PORT=6379
- REDIS_SERVICE_PORT
- REDIS_SERVICE_IP

```
apiVersion: v1
kind: Service
metadata:
  name: redis
spec:
  selector:
    app: redis
  ports:
    - protocol: TCP
      port: 6379
      targetPort: $REDIS_POD_PORT
```

## 5 mysql
1. 创建mysql用户名密码secret
- MYSQL_USERNAME
- MYSQL_PASSWORD
- MYSQL_PASSWORD_SECRET=mysql-password-secret

```
apiVersion: v1
kind: Secret
metadata:
  name: mysql-password-secret
type: Opaque
data:
  username: $MYSQL_USERNAME
  password: $MYSQL_PASSWORD
```
2. 创建mysql deployment
- MYSQL_IMAGE
- MYSQL_POD_PORT=3306
- MYSQL_VOLUME_PATH

```
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mysql
spec:
  replicas: 1
  selector:
    matchLabels:
      app: mysql
  template:
    metadata:
      labels:
        app: mysql
    spec:
      containers:
        - name: mysql
          image: $MYSQL_IMAGE
          ports:
            - containerPort: $MYSQL_POD_PORT
          env:
            - name: MYSQL_ROOT_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: $MYSQL_PASSWORD_SECRET
                  key: password
          volumeMounts:
            - name: mysql-volume
              mountPath: /var/lib/mysql
      volumes:
        - hostPath:
            path: $MYSQL_VOLUME_PATH
            type: DirectoryOrCreate
          name: mysql-volume

```

3. 创建mysql service
- MYSQL_POD_PORT=3306
- MYSQL_SERVICE_PORT=3306
- MYSQL_SERVICE_IP

```
apiVersion: v1
kind: Service
metadata:
  name: mysql
spec:
  selector:
    app: mysql
  ports:
    - protocol: TCP
      port: $MYSQL_SERVICE_PORT
      targetPort: $MYSQL_POD_PORT
  type: ClusterIP

```

4. mysql初始化

- 建库建表
```
create database mpc;
use mpc;
CREATE TABLE `parent_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` varchar(100) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT 'id',
  `status` int NOT NULL,
  `type` varchar(100) DEFAULT NULL,
  `create_at` datetime NOT NULL,
  `update_at` datetime NOT NULL,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  `params` longtext,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
CREATE TABLE `children_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '[]',
  `parent_task_id` varchar(100) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT 'id',
  `sub_id` int NOT NULL,
  `task_index` int NOT NULL,
  `pod_num` int DEFAULT NULL COMMENT 'pod',
  `status` int NOT NULL,
  `task_type` varchar(100) DEFAULT NULL,
  `create_at` datetime NOT NULL,
  `update_at` datetime NOT NULL,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  `message` text,
  `result` longtext,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

use mpc;
CREATE TABLE `data_block_meta_l` (
  `block_id` varchar(300) NOT NULL DEFAULT '',
  `dfs_data_block_dir` varchar(500) NOT NULL DEFAULT '',
  `partition_id` bigint DEFAULT '0',
  `file_version` bigint DEFAULT '0',
  `start_time` bigint DEFAULT NULL,
  `end_time` bigint DEFAULT NULL,
  `example_ids` bigint DEFAULT '0',
  `leader_start_index` bigint DEFAULT '0',
  `leader_end_index` bigint DEFAULT '0',
  `follower_start_index` bigint DEFAULT '0',
  `follower_end_index` bigint DEFAULT '0',
  `data_block_index` bigint DEFAULT '0',
  `create_time` bigint DEFAULT NULL,
  `update_time` bigint DEFAULT NULL,
  `create_status` int DEFAULT '2',
  `consumed_status` int DEFAULT NULL,
  `follower_restart_index` bigint DEFAULT '0',
  `data_source_name` varchar(300) DEFAULT NULL,
  PRIMARY KEY (`block_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

use mpc;
CREATE TABLE `job_task_stub` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `parent_task_id` varchar(100) DEFAULT NULL COMMENT '父任务id',
  `pre_job_json` longtext DEFAULT NULL COMMENT '任务详情json',
  `job_target` varchar(30) DEFAULT NULL COMMENT '任务执行端',
  `job_distributor_sign` varchar(500) DEFAULT NULL COMMENT '任务发起端签名MD5withRSA',
  `job_executor_sign` varchar(500) DEFAULT NULL COMMENT '任务执行端签名MD5withRSA',
  `job_distributor_cert` varchar(2000) DEFAULT NULL COMMENT '任务发起端证书内容',
  `job_executor_cert` varchar(2000) DEFAULT NULL COMMENT '任务执行端证书内容',
  `is_local` tinyint(4) NOT NULL COMMENT '任务来源，1=自身发起，0=外部发起',
  `create_at` datetime NOT NULL,
  `update_at` datetime NOT NULL,
  `is_deleted` tinyint(4) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8;
CREATE TABLE `cert_info` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `cert_content` varchar(2000) DEFAULT NULL COMMENT '证书内容',
  `public_exponent` varchar(1000) DEFAULT NULL COMMENT '公钥指数，ACES加密',
  `private_exponent` varchar(1000) DEFAULT NULL COMMENT '私钥指数，ACES加密',
  `modulus` varchar(1000) DEFAULT NULL COMMENT '模数，ACES加密',
  `is_root` tinyint(4) NOT NULL COMMENT '是否根证书，1是，0否',
  `create_at` datetime NOT NULL,
  `update_at` datetime NOT NULL,
  `is_deleted` tinyint(4) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8;
CREATE TABLE `auth_info` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'id',
  `domain` varchar(200) DEFAULT NULL COMMENT 'target',
  `cert_type` varchar(200) DEFAULT NULL COMMENT ' ROOT  AUTH   WORKER',
  `cert` varchar(1000) DEFAULT NULL,
  `pub_key` varchar(1000) DEFAULT NULL,
  `pri_key` varchar(1000) DEFAULT NULL,
  `status` varchar(200) DEFAULT NULL COMMENT ' SUBMIT  PASS  REJECT',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UNIQUE_DOMAIN_CERT_TYPE` (`domain`,`cert_type`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;
```           

- cert_info证书生成与导入

由京东侧完成证书生成，生成sql文件$PROCY_TARGET.sql，由客户侧直接在mysql中执行即可。
```
java -jar ../gen-0.0.1-SNAPSHOT.jar --partys=$PROXY_TARGET,false
```
该条sql中信息包含ca.crt、server_cert.pem、server_private.pem等信息，在proxy组件的部署配置中仍需用到。

- auth_info证书生成（非必须）
只有防盗版worker需要该步骤
由京东侧完成该步骤。

    - 生成ROOT证书
    ```
    curl -H "Content-Type:application/json" -X POST -d '{
  "commonName": "ROOT",
  "organizationUnit": "mpc",
  "organizationName": $PROXY_TARGET,
  "localityName": "BeiJing",
  "stateName": "BeiJing",
  "country": "CN"}'  http://mpc-auth-prodhttp.mpc-9n.svc.lf10.n.jd.local/openapi/initDomain

    ```
    - 生成AUTH证书
    ```
    curl -H "Content-Type:application/json" -X POST -d '{
  "commonName": "AUTH",
  "organizationUnit": "mpc",
  "organizationName": $PROXY_TARGET,
  "localityName": "BeiJing",
  "stateName": "BeiJing",
  "country": "CN"}' http://mpc-auth-prodhttp.mpc-9n.svc.lf10.n.jd.local/openapi/initDomain
    ```
    - 证书审批
    ```
    curl 'http://mpc-auth-prodhttp.mpc-9n.svc.lf10.n.jd.local/openapi/updateAuthInfoStatus?domain=$PROXY_TARGET&status=PASS'
    ```



## 6 fileservice
- FILESERVICE_IMAGE
- FILESERVICE_PORT=8800
- REDIS_SERVICE_IP
- REDIS_SERVICE_PORT
- REDIS_PASSWORD
- VOLUME_LOGS
- VOLUME_DATA

```
apiVersion: apps/v1
kind: Deployment
metadata:
  name: file-service
  labels:
    app: file-service
spec:
  replicas: 1
  selector:
    matchLabels:
      app: file-service
  template:
    metadata:
      labels:
        app: file-service
    spec:
      containers:
        - name: file-service
          image: $FILESERVICE_IMAGE
          ports:
            - containerPort: $FILESERVICE_PORT
          env:
            - name: REDIS_HOST
              value: $REDIS_SERVICE_IP
            - name: REDIS_PORT
              value: REDIS_SERVICE_PORT
            - name: REDIS_PASSWORD
              value: $REDIS_PASSWORD
          volumeMounts:
            - name: file-data
              mountPath: /mnt/data
            - name: logs
              mountPath: /mnt/logs
      volumes:
        - name: logs
          hostPath:
            path: $VOLUME_LOGS
            type: DirectoryOrCreate
        - name: file-data
          hostPath:
            path: $VOLUME_DATA
            type: DirectoryOrCreate


```

## 7 proxy
部署proxy之前，需要先在redis中配置需要通信一方的proxy地址，key的格式为：target:$PROXY_TARGET，值为proxy的公网ip:port。

需要先创建4个configmap，然后创建proxy。
1. 创建配置configmap nginx-conf
- NGINX_CONF=nginx-conf

```
apiVersion: v1
kind: ConfigMap
metadata:
  name: $NGINX_CONF
data:
  nginx.conf: |
    # 设置为nginx的CPU核数
    worker_processes 8;
    worker_rlimit_nofile 65535;
    error_log /usr/local/openresty/nginx/logs/error.log info;
    worker_shutdown_timeout 1h;
    
    events {
        use epoll;
    }
    
    http {
        sendfile on;
        aio threads;
        aio_write on;
        directio 8m;
        tcp_nopush on;
        tcp_nodelay on;
    
        keepalive_timeout 65;
        keepalive_requests 10000;
        
        include /usr/local/openresty/nginx/conf/mime.types;
        default_type application/octet-stream;
        lua_package_path "$prefix/lua_src/?.lua;/usr/local/openresty/lualib/resty/?.lua;;";
         log_format xlog '$http_host $remote_addr $remote_port $remote_user [$time_local] $request_time '
         '"$request" $status $body_bytes_sent '
         '"$http_referer" "$http_user_agent" "$http_host" "$http_cookie" '
         '"$upstream_response_time" $upstream_addr "$http_x_forwarded_for" $scheme '
         '"$upstream_http_set_cookie"';
    
        access_log ./logs/access.log xlog;
    
        client_max_body_size 0;
        client_body_buffer_size 32m;
    
        ssl_session_cache shared:SSL:10m;
        ssl_session_timeout 10m;
    
        resolver local=on ipv6=off;
    
        include conf.d/mpc-proxy.conf;
    }

```

2. 创建配置configmap mpc-conf
- MPC_CONF=mpc-conf
- PROXY_PORT_OUT：外部请求入proxy端口，coordinator的grpc.proxy.local-port配置
- PROXY_PORT_IN：内部请求出proxy接口，coordinator的grpc.proxy.port配置
- PROXY_NODE_IP：本侧PROXY ip

```
apiVersion: v1
kind: ConfigMap
metadata:
  name: $MPC_CONF
data:
  mpc-proxy.conf: |
    upstream worker {
      server 127.0.0.1:10000;
  
      balancer_by_lua_block {
          local balancer = require "ngx.balancer"
          local ok, err = balancer.set_current_peer(ngx.ctx.ip, ngx.ctx.port)
          if not ok then
              ngx.log(ngx.ERR, "failed to set the current peer: ", err)
              return ngx.exit(ngx.ERROR)
          end
      }
      keepalive 1024;
      keepalive_timeout 120s;
      keepalive_requests 10000;
    }

    server {
        listen $PROXY_PORT_OUT http2 ssl so_keepalive=on;
        lua_socket_log_errors off;
        ssl_certificate /cert/server_cert.pem;
        ssl_certificate_key /cert/server_private.pem;
        server_tokens off;
        proxy_ignore_client_abort on;
        grpc_socket_keepalive on;
        grpc_connect_timeout 1800s;
        grpc_read_timeout 1800s;
        grpc_send_timeout 1800s;
    
        location /gateway_protocol.Gateway/ {
            grpc_pass grpc://mpc-zeebe-gateway-test.jd.local:2000;
        }
    
        location / {
    
            access_by_lua_block {
                local routein = require "routein"
                local full_address = routein:route()
                if full_address == nil or full_address == '' then
                    ngx.log(ngx.ERR, "can not get address")
                    return ngx.exit(ngx.ERROR)
                end
                local s, e, ip = string.find(full_address, "(.+):", 1)
                local port_string = string.sub(full_address, e+1)
                local port = tonumber(port_string)
                -- ngx.log(ngx.ERR, "backend address: ", full_address)
                -- ngx.log(ngx.ERR, "ip: ", ip)
                -- ngx.log(ngx.ERR, "port: ", port)
                ngx.ctx.ip = ip
                ngx.ctx.port = port
            }
    
            grpc_pass grpc://worker;
        }
    }
    
    upstream worker_out {
        server 127.0.0.1:10000;
    
        balancer_by_lua_block {
            local balancer = require "ngx.balancer"
            local ok, err = balancer.set_current_peer(ngx.ctx.ip, ngx.ctx.port)
            if not ok then
                ngx.log(ngx.ERR, "failed to set the current peer: ", err)
                return ngx.exit(ngx.ERROR)
            end
        }
        # connection pool
        # 对外转发，所有的target共用一个pool
        keepalive 1024;
        keepalive_timeout 120s;
        keepalive_requests 10000;
    }
    
    server {
        listen $PROXY_PORT_IN http2 so_keepalive=on;
    
        # This directive can be used to toggle error logging when a failure occurs for the TCP or UDP cosockets. If you are already doing proper error handling and logging in your Lua code, then it is recommended to turn this directive off to prevent data flushing in your nginx error log files (which is usually rather expensive).
        lua_socket_log_errors off;
    
        proxy_ignore_client_abort on;
        grpc_socket_keepalive on;
        grpc_connect_timeout 1800s;
        grpc_read_timeout 1800s;
        grpc_send_timeout 1800s;
    
        # hide version and some token
        server_tokens off;
    
        ## FOR auth service
        ## Fix send to JD domain
        location /authprotocol.AuthService/ {
            grpc_pass grpcs://$PROXY_NODE_IP:$PROXY_PORT_OUT;
        }
    
        ## FOR camunda zeebe service
        ## Fix send to JD domain
        location /gateway_protocol.Gateway/ {
            grpc_pass grpcs://$PROXY_NODE_IP:$PROXY_PORT_OUT;
        }
    
        location / {
            # ssl
            grpc_ssl_verify on;
            grpc_ssl_name $http_target;
            #grpc_ssl_name $LOCAL_TARGET;
            grpc_ssl_trusted_certificate /cert/ca.crt;
            # 启用TLS客户端验证
            # grpc_ssl_certificate /sslkey/client_cert.pem;
            # grpc_ssl_certificate_key /sslkey/client_private.pem;
    
            access_by_lua_block {
                local routeout = require "routeout"
                local full_address = routeout:route()
                if full_address == nil or full_address == '' then
                    ngx.log(ngx.ERR, "can not get address")
                    return ngx.exit(ngx.ERROR)
                end
                local s, e, ip = string.find(full_address, "(.+):", 1)
                local port_string = string.sub(full_address, e+1)
                local port = tonumber(port_string)
                -- ngx.log(ngx.ERR, "backend address: ", full_address)
                -- ngx.log(ngx.ERR, "ip: ", ip)
                -- ngx.log(ngx.ERR, "port: ", port)
                ngx.ctx.ip = ip
                ngx.ctx.port = port
            }
    
            grpc_pass grpcs://worker_out;
        }
    }


```

3. 创建配置configmap lua-src
- LUA_SRC=lua-src
- REDIS_SERVICE_IP
- REDIS_SERVICE_PORT
- REDIS_PASSWORD

```
apiVersion: v1
kind: ConfigMap
metadata:
  name: lua-src
data:
  redis.lua: |
    local redis = require "resty.redis"

    local _M = {
        _VERSION = '0.0.1',
        m_host = $REDIS_SERVICE_IP,
        m_port = $REDIS_SERVICE_PORT,
        m_password = $REDIS_PASSWORD,
    }
    
    function _M.new(self)
        local red = redis:new()
        red:set_timeouts(1000, 1000, 1000)
    
        --ngx.log(ngx.INFO, "select redis addr: ", host, port)
        local ok, err = red:connect(self.m_host, self.m_port)
        if not ok then
            ngx.log(ngx.ERR, "failed to connect: ", err)
            return err
        end
        -- auth
        if self.m_password ~= nil and self.m_password ~= '' then
            local count
            count, err = red:get_reused_times()
            if 0 == count then
                ok, err = red:auth(self.m_password)
                if not ok then
                    ngx.log(ngx.ERR, "failed to auth: ", err)
                    red:close()
                    return err
                end
           elseif err then
                ngx.log(ngx.ERR"failed to get reused times: ", err)
                red:close()
                return err
           end
        end
        return red
    end
    
    function _M.get(self,key)
        local index = 0
        local err = nil
        local res
        local red = self:new()
        repeat
            res, err = red:get(key)
            if err ~= nil then
                index = index + 1
            else
                err = nil
                break
            end
        until (index > 2)
        if err ~= nil then
            ngx.log(ngx.ERR, "failed to get redis value of ", key, ":", err)
            return nil, err
        end
    
        if res == ngx.null or res == '' then
            err = "redis value is not exist"
            ngx.log(ngx.ERR, err)
            return nil, err
        end
      -- close
        red:close()
        return res, nil
    end
    
    return _M
  routein.lua: |
    
    local MAX_ADDR_NUM = 2000
    local redis = require "redis"
    
    local _M = {
        _VERSION = '0.0.1',
        t_addr = {},
        t_history = {},
        t_size = 0,
    }
    
    function _M.get(self, key)
        local value = self.t_addr[key]
        if value ~= nil then
            return value
        end
    
        value = self.t_history[key]
        if value ~= nil then
            if self.t_size > MAX_ADDR_NUM then
                self.t_history = self.t_addr
                self.t_addr = {}
                self.t_size = 0
            end
            self.t_addr[key] = value
            self.t_size = self.t_size + 1
            return value
        end
    
        local err
        value,err = redis:get(key)
        if value == nil or err ~= nil then
            ngx.log(ngx.ERR, "Get id: ", key, " from redis fail!  reson is :", err)
            return nil
        end
    
        if self.t_size > MAX_ADDR_NUM then
            self.t_history = self.t_addr
            self.t_addr = {}
            self.t_size = 0
        end
        self.t_addr[key] = value
        self.t_size = self.t_size + 1
    
        return value
    end
    
    function _M.get_uuid()
        local uuid = "network:"..ngx.var.http_id
        if uuid == nil then
            ngx.log(ngx.ERR, "get uuid fail! uri is : ", ngx.var.uri)
            return nil
        end
        ngx.log(ngx.INFO, "get uuid success, uuid is ", uuid)
        return uuid
    end
    
    function _M.route(self)
        local uuid = self.get_uuid()
        if uuid == nil then
            ngx.log(ngx.ERR, "Get uuid fail!")
            return nil
        end
    
        local remote_addr = self:get(uuid)
        return remote_addr
    end
    
    return _M
  routeout.lua: |
    
    local MAX_ADDR_NUM = 2000
    local redis = require "redis"
    
    local _M = {
        _VERSION = '0.0.1',
        t_addr = {},
        t_history = {},
        t_size = 0,
    }
    
    function _M.get(self, key)
        local value = self.t_addr[key]
        if value ~= nil then
            return value
        end
    
        value = self.t_history[key]
        if value ~= nil then
            if self.t_size > MAX_ADDR_NUM then
                self.t_history = self.t_addr
                self.t_addr = {}
                self.t_size = 0
            end
            self.t_addr[key] = value
            self.t_size = self.t_size + 1
            return value
        end
    
        local err
        value,err = redis:get(key)
        if value == nil or err ~= nil then
            ngx.log(ngx.ERR, "Get id: ", key, " from redis fail!  reson is :", err)
            return nil
        end
    
        if self.t_size > MAX_ADDR_NUM then
            self.t_history = self.t_addr
            self.t_addr = {}
            self.t_size = 0
        end
        self.t_addr[key] = value
        self.t_size = self.t_size + 1
    
        return value
    end
    
    function _M.get_uuid()
        local uuid = "target:"..ngx.var.http_target
        if uuid == nil then
            ngx.log(ngx.ERR, "get target fail! uri is : ", ngx.var.uri)
            return nil
        end
        ngx.log(ngx.INFO, "get target success, target is ", uuid)
        return uuid
    end
    
    function _M.route(self)
        local uuid = self.get_uuid()
        if uuid == nil then
            ngx.log(ngx.ERR, "Get uuid fail!")
            return nil
        end
    
        local remote_addr = self:get(uuid)
        return remote_addr
    end
    
    return _M

```

4. 创建配置configmap cert
- CERT

将部署mysql章节中cert_info生成与导入小杰获取到的ca.crt、server_cert.pem、server_private.pem等信息放入以下配置文件中。

```
apiVersion: v1
kind: ConfigMap
metadata:
  name: cert
  namespace: mpc-chk-test
data:
  ca.crt: |
    -----BEGIN CERTIFICATE-----
    -----END CERTIFICATE-----
  server_cert.pem: |
    -----BEGIN CERTIFICATE-----
    -----END CERTIFICATE-----
  server_private.pem: |
    -----BEGIN PRIVATE KEY-----
    -----END PRIVATE KEY-----
```

5. 创建proxy deployment
- PROXY_IMAGE
- NODENAME
- PROXY_NODE_IP

部署proxy时通过NODENAME指定部署在固定的节点上，重启proxy时不会改变，与京东侧通信需要给proxy开公网ip端口。若起proxy的节点无法开通公网ip和端口，也可采用svc绑定负载均衡器然后给负载均衡器开公网地址+端口的形式。

```
apiVersion: apps/v1
kind: Deployment
metadata:
  labels:
    app: proxy
  name: proxy
spec:
  replicas: 1
  revisionHistoryLimit: 10
  selector:
    matchLabels:
      app: proxy
  template:
    metadata:
      labels:
        app: proxy
    spec:
      containers:
      - image: $PROXY_IMAGE
        imagePullPolicy: Always
        name: proxy
        resources:
          limits:
            cpu: "4"
            memory: 8Gi
          requests:
            cpu: "0.5"
            memory: 1Gi
        volumeMounts:
        - mountPath: /usr/local/openresty/nginx/conf/nginx.conf
          name: nginx-conf
          subPath: nginx.conf
        - mountPath: /usr/local/openresty/nginx/conf/conf.d/mpc-proxy.conf
          name: mpc-conf
          subPath: mpc-proxy.conf
        - mountPath: /usr/local/openresty/nginx/lua_src
          name: lua-src
        - mountPath: /cert
          name: cert
      hostNetwork: true
      nodeName: $NODENAME
      restartPolicy: Always
      volumes:
      - configMap:
          defaultMode: 420
          name: nginx-conf
        name: nginx-conf
      - configMap:
          defaultMode: 420
          name: mpc-conf
        name: mpc-conf
      - configMap:
          defaultMode: 420
          name: lua-src
        name: lua-src
      - configMap:
          defaultMode: 420
          name: cert
        name: cert
```

## 8 nacos

nacos主要用于给coordinator配置信息，由于一些特定的原因，请将nacos与其它组件部署在同一个集群上，不要使用其它集群上现有的nacos。

1. 初始化数据库
```aidl
-- MySQL dump 10.13  Distrib 8.0.26, for Linux (x86_64)
--
-- Host: 11.136.250.30    Database: nacos
-- ------------------------------------------------------
-- Server version       8.0.31

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `config_info`
--

DROP TABLE IF EXISTS `config_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `config_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `data_id` varchar(255) COLLATE utf8mb3_bin NOT NULL COMMENT 'data_id',
  `group_id` varchar(128) COLLATE utf8mb3_bin DEFAULT NULL,
  `content` longtext COLLATE utf8mb3_bin NOT NULL COMMENT 'content',
  `md5` varchar(32) COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'md5',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `src_user` text COLLATE utf8mb3_bin COMMENT 'source user',
  `src_ip` varchar(50) COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'source ip',
  `app_name` varchar(128) COLLATE utf8mb3_bin DEFAULT NULL,
  `tenant_id` varchar(128) COLLATE utf8mb3_bin DEFAULT '',
  `c_desc` varchar(256) COLLATE utf8mb3_bin DEFAULT NULL,
  `c_use` varchar(64) COLLATE utf8mb3_bin DEFAULT NULL,
  `effect` varchar(64) COLLATE utf8mb3_bin DEFAULT NULL,
  `type` varchar(64) COLLATE utf8mb3_bin DEFAULT NULL,
  `c_schema` text COLLATE utf8mb3_bin,
  `encrypted_data_key` text COLLATE utf8mb3_bin NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_configinfo_datagrouptenant` (`data_id`,`group_id`,`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1963 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='config_info';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `config_info_aggr`
--

DROP TABLE IF EXISTS `config_info_aggr`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `config_info_aggr` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `data_id` varchar(255) COLLATE utf8mb3_bin NOT NULL COMMENT 'data_id',
  `group_id` varchar(128) COLLATE utf8mb3_bin NOT NULL COMMENT 'group_id',
  `datum_id` varchar(255) COLLATE utf8mb3_bin NOT NULL COMMENT 'datum_id',
  `content` longtext COLLATE utf8mb3_bin NOT NULL,
  `gmt_modified` datetime NOT NULL,
  `app_name` varchar(128) COLLATE utf8mb3_bin DEFAULT NULL,
  `tenant_id` varchar(128) COLLATE utf8mb3_bin DEFAULT '',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_configinfoaggr_datagrouptenantdatum` (`data_id`,`group_id`,`tenant_id`,`datum_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `config_info_beta`
--

DROP TABLE IF EXISTS `config_info_beta`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `config_info_beta` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `data_id` varchar(255) COLLATE utf8mb3_bin NOT NULL COMMENT 'data_id',
  `group_id` varchar(128) COLLATE utf8mb3_bin NOT NULL COMMENT 'group_id',
  `app_name` varchar(128) COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'app_name',
  `content` longtext COLLATE utf8mb3_bin NOT NULL COMMENT 'content',
  `beta_ips` varchar(1024) COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'betaIps',
  `md5` varchar(32) COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'md5',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `src_user` text COLLATE utf8mb3_bin COMMENT 'source user',
  `src_ip` varchar(50) COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'source ip',
  `tenant_id` varchar(128) COLLATE utf8mb3_bin DEFAULT '',
  `encrypted_data_key` text COLLATE utf8mb3_bin NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_configinfobeta_datagrouptenant` (`data_id`,`group_id`,`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='config_info_beta';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `config_info_tag`
--

DROP TABLE IF EXISTS `config_info_tag`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `config_info_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `data_id` varchar(255) COLLATE utf8mb3_bin NOT NULL COMMENT 'data_id',
  `group_id` varchar(128) COLLATE utf8mb3_bin NOT NULL COMMENT 'group_id',
  `tenant_id` varchar(128) COLLATE utf8mb3_bin DEFAULT '' COMMENT 'tenant_id',
  `tag_id` varchar(128) COLLATE utf8mb3_bin NOT NULL COMMENT 'tag_id',
  `app_name` varchar(128) COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'app_name',
  `content` longtext COLLATE utf8mb3_bin NOT NULL COMMENT 'content',
  `md5` varchar(32) COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'md5',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `src_user` text COLLATE utf8mb3_bin COMMENT 'source user',
  `src_ip` varchar(50) COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'source ip',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_configinfotag_datagrouptenanttag` (`data_id`,`group_id`,`tenant_id`,`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='config_info_tag';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `config_tags_relation`
--

DROP TABLE IF EXISTS `config_tags_relation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `config_tags_relation` (
  `id` bigint NOT NULL COMMENT 'id',
  `tag_name` varchar(128) COLLATE utf8mb3_bin NOT NULL COMMENT 'tag_name',
  `tag_type` varchar(64) COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'tag_type',
  `data_id` varchar(255) COLLATE utf8mb3_bin NOT NULL COMMENT 'data_id',
  `group_id` varchar(128) COLLATE utf8mb3_bin NOT NULL COMMENT 'group_id',
  `tenant_id` varchar(128) COLLATE utf8mb3_bin DEFAULT '' COMMENT 'tenant_id',
  `nid` bigint NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`nid`),
  UNIQUE KEY `uk_configtagrelation_configidtag` (`id`,`tag_name`,`tag_type`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='config_tag_relation';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `group_capacity`
--

DROP TABLE IF EXISTS `group_capacity`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `group_capacity` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `group_id` varchar(128) COLLATE utf8mb3_bin NOT NULL DEFAULT '' COMMENT 'Group ID',
  `quota` int unsigned NOT NULL DEFAULT '0' COMMENT '0',
  `usage` int unsigned NOT NULL DEFAULT '0',
  `max_size` int unsigned NOT NULL DEFAULT '0' COMMENT '0',
  `max_aggr_count` int unsigned NOT NULL DEFAULT '0' COMMENT '0',
  `max_aggr_size` int unsigned NOT NULL DEFAULT '0' COMMENT '0',
  `max_history_count` int unsigned NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_id` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='Group';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `his_config_info`
--

DROP TABLE IF EXISTS `his_config_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `his_config_info` (
  `id` bigint unsigned NOT NULL,
  `nid` bigint unsigned NOT NULL AUTO_INCREMENT,
  `data_id` varchar(255) COLLATE utf8mb3_bin NOT NULL,
  `group_id` varchar(128) COLLATE utf8mb3_bin NOT NULL,
  `app_name` varchar(128) COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'app_name',
  `content` longtext COLLATE utf8mb3_bin NOT NULL,
  `md5` varchar(32) COLLATE utf8mb3_bin DEFAULT NULL,
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `src_user` text COLLATE utf8mb3_bin,
  `src_ip` varchar(50) COLLATE utf8mb3_bin DEFAULT NULL,
  `op_type` char(10) COLLATE utf8mb3_bin DEFAULT NULL,
  `tenant_id` varchar(128) COLLATE utf8mb3_bin DEFAULT '',
  `encrypted_data_key` text COLLATE utf8mb3_bin NOT NULL,
  PRIMARY KEY (`nid`),
  KEY `idx_gmt_create` (`gmt_create`),
  KEY `idx_gmt_modified` (`gmt_modified`),
  KEY `idx_did` (`data_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1968 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `permissions`
--

DROP TABLE IF EXISTS `permissions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `permissions` (
  `role` varchar(50) NOT NULL,
  `resource` varchar(255) NOT NULL,
  `action` varchar(8) NOT NULL,
  UNIQUE KEY `uk_role_permission` (`role`,`resource`,`action`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `roles`
--

DROP TABLE IF EXISTS `roles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `roles` (
  `username` varchar(50) NOT NULL,
  `role` varchar(50) NOT NULL,
  UNIQUE KEY `idx_user_role` (`username`,`role`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tenant_capacity`
--

DROP TABLE IF EXISTS `tenant_capacity`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tenant_capacity` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `tenant_id` varchar(128) COLLATE utf8mb3_bin NOT NULL DEFAULT '' COMMENT 'Tenant ID',
  `quota` int unsigned NOT NULL DEFAULT '0' COMMENT '0',
  `usage` int unsigned NOT NULL DEFAULT '0',
  `max_size` int unsigned NOT NULL DEFAULT '0' COMMENT '0',
  `max_aggr_count` int unsigned NOT NULL DEFAULT '0',
  `max_aggr_size` int unsigned NOT NULL DEFAULT '0' COMMENT '0',
  `max_history_count` int unsigned NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tenant_info`
--

DROP TABLE IF EXISTS `tenant_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tenant_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `kp` varchar(128) COLLATE utf8mb3_bin NOT NULL COMMENT 'kp',
  `tenant_id` varchar(128) COLLATE utf8mb3_bin DEFAULT '' COMMENT 'tenant_id',
  `tenant_name` varchar(128) COLLATE utf8mb3_bin DEFAULT '' COMMENT 'tenant_name',
  `tenant_desc` varchar(256) COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'tenant_desc',
  `create_source` varchar(32) COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'create_source',
  `gmt_create` bigint NOT NULL,
  `gmt_modified` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_info_kptenantid` (`kp`,`tenant_id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='tenant_info';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `username` varchar(50) NOT NULL,
  `password` varchar(500) NOT NULL,
  `enabled` tinyint(1) NOT NULL,
  PRIMARY KEY (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2023-09-08 13:24:36
```



2. nacos-cm.yaml
```aidl
apiVersion: v1
data:
  application.properties: |
    #*************** Spring Boot Related Configurations ***************#
    ### Default web context path:
    server.servlet.contextPath=/nacos
    ### Include message field
    server.error.include-message=ALWAYS
    ### Default web server port:
    server.port=8848
    #*************** Network Related Configurations ***************#
    ### If prefer hostname over ip for Nacos server addresses in cluster.conf:
    # nacos.inetutils.prefer-hostname-over-ip=false
    ### Specify local server's IP:
    # nacos.inetutils.ip-address=
    #*************** Config Module Related Configurations ***************#
    ### Deprecated configuration property, it is recommended to use `spring.sql.init.platform` replaced.
    spring.datasource.platform=mysql
    # nacos.plugin.datasource.log.enabled=true
    #spring.sql.init.platform=mysql
    ### Count of DB:
    db.num=1
    ### Connect URL of DB:
    db.url.0=jdbc:mysql://mysql.default.svc.cluster.local:3306/nacos?characterEncoding=utf8&connectTimeout=30000&socketTimeout=30000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
    db.user.0=root
    db.password.0=123456
    ### Connection pool configuration: hikariCP
    db.pool.config.connectionTimeout=30000
    db.pool.config.validationTimeout=10000
    db.pool.config.maximumPoolSize=20
    db.pool.config.minimumIdle=2
    #*************** Naming Module Related Configurations ***************#
    ### Data dispatch task execution period in milliseconds:

    ### If enable data warmup. If set to false, the server would accept request without local data preparation:
    # nacos.naming.data.warmup=true
    ### If enable the instance auto expiration, kind like of health check of instance:
    # nacos.naming.expireInstance=true
    ### will be removed and replaced by `nacos.naming.clean` properties
    nacos.naming.empty-service.auto-clean=true
    nacos.naming.empty-service.clean.initial-delay-ms=50000
    nacos.naming.empty-service.clean.period-time-ms=30000
    ### Add in 2.0.0
    ### The interval to clean empty service, unit: milliseconds.
    # nacos.naming.clean.empty-service.interval=60000
    ### The expired time to clean empty service, unit: milliseconds.
    # nacos.naming.clean.empty-service.expired-time=60000
    ### The interval to clean expired metadata, unit: milliseconds.
    # nacos.naming.clean.expired-metadata.interval=5000
    ### The expired time to clean metadata, unit: milliseconds.
    # nacos.naming.clean.expired-metadata.expired-time=60000
    ### The delay time before push task to execute from service changed, unit: milliseconds.
    # nacos.naming.push.pushTaskDelay=500
    ### The timeout for push task execute, unit: milliseconds.
    # nacos.naming.push.pushTaskTimeout=5000
    ### The delay time for retrying failed push task, unit: milliseconds.
    # nacos.naming.push.pushTaskRetryDelay=1000
    ### Since 2.0.3
    ### The expired time for inactive client, unit: milliseconds.
    # nacos.naming.client.expired.time=180000
    #*************** CMDB Module Related Configurations ***************#
    ### The interval to dump external CMDB in seconds:
    # nacos.cmdb.dumpTaskInterval=3600
    ### The interval of polling data change event in seconds:
    # nacos.cmdb.eventTaskInterval=10
    ### The interval of loading labels in seconds:
    # nacos.cmdb.labelTaskInterval=300
    ### If turn on data loading task:
    # nacos.cmdb.loadDataAtStart=false
    #*************** Metrics Related Configurations ***************#
    ### Metrics for prometheus
    #management.endpoints.web.exposure.include=*
    ### Metrics for elastic search
    management.metrics.export.elastic.enabled=false
    #management.metrics.export.elastic.host=http://localhost:9200
    ### Metrics for influx
    management.metrics.export.influx.enabled=false
    #management.metrics.export.influx.db=springboot
    #management.metrics.export.influx.uri=http://localhost:8086
    #management.metrics.export.influx.auto-create-db=true
    #management.metrics.export.influx.consistency=one
    #management.metrics.export.influx.compressed=true
    #*************** Access Log Related Configurations ***************#
    ### If turn on the access log:
    server.tomcat.accesslog.enabled=true
    ### accesslog automatic cleaning time
    server.tomcat.accesslog.max-days=30
    ### The access log pattern:
    server.tomcat.accesslog.pattern=%h %l %u %t "%r" %s %b %D %{User-Agent}i %{Request-Source}i
    ### The directory of access log:
    server.tomcat.basedir=file:.
    #*************** Access Control Related Configurations ***************#
    ### If enable spring security, this option is deprecated in 1.2.0:
    #spring.security.enabled=false
    ### The ignore urls of auth, is deprecated in 1.2.0:
    nacos.security.ignore.urls=/,/error,/**/*.css,/**/*.js,/**/*.html,/**/*.map,/**/*.svg,/**/*.png,/**/*.ico,/console-ui/public/**,/v1/auth/**,/v1/console/health/**,/actuator/**,/v1/console/server/**
    ### The auth system to use, currently only 'nacos' and 'ldap' is supported:
    nacos.core.auth.system.type=nacos
    ### If turn on auth system:
    nacos.core.auth.enabled=false
    ### Turn on/off caching of auth information. By turning on this switch, the update of auth information would have a 15 seconds delay.
    nacos.core.auth.caching.enabled=true
    ### Since 1.4.1, Turn on/off white auth for user-agent: nacos-server, only for upgrade from old version.
    nacos.core.auth.enable.userAgentAuthWhite=false
    ### Since 1.4.1, worked when nacos.core.auth.enabled=true and nacos.core.auth.enable.userAgentAuthWhite=false.
    ### The two properties is the white list for auth and used by identity the request from other server.
    nacos.core.auth.server.identity.key=serverIdentity
    nacos.core.auth.server.identity.value=security
    ### worked when nacos.core.auth.system.type=nacos
    ### The token expiration in seconds:
    nacos.core.auth.plugin.nacos.token.expire.seconds=18000
    ### The default token (Base64 string):
    nacos.core.auth.plugin.nacos.token.secret.key=SecretKey012345678901234567890123456789012345678901234567890123456789
    ### worked when nacos.core.auth.system.type=ldap?{0} is Placeholder,replace login username
    #nacos.core.auth.ldap.url=ldap://localhost:389
    #nacos.core.auth.ldap.basedc=dc=example,dc=org
    #nacos.core.auth.ldap.userDn=cn=admin,${nacos.core.auth.ldap.basedc}
    #nacos.core.auth.ldap.password=admin
    #nacos.core.auth.ldap.userdn=cn={0},dc=example,dc=org
    #nacos.core.auth.ldap.filter.prefix=uid
    #nacos.core.auth.ldap.case.sensitive=true
    #*************** Istio Related Configurations ***************#
    ### If turn on the MCP server:
    nacos.istio.mcp.server.enabled=false

    ###*************** Add from 1.3.0 ***************###
    #*************** Core Related Configurations ***************#
    ### set the WorkerID manually
    # nacos.core.snowflake.worker-id=
    ### Member-MetaData
    # nacos.core.member.meta.site=
    # nacos.core.member.meta.adweight=
    # nacos.core.member.meta.weight=
    ### MemberLookup
    ### Addressing pattern category, If set, the priority is highest
    # nacos.core.member.lookup.type=[file,address-server]
    ## Set the cluster list with a configuration file or command-line argument
    # nacos.member.list=192.168.16.101:8847?raft_port=8807,192.168.16.101?raft_port=8808,192.168.16.101:8849?raft_port=8809
    ## for AddressServerMemberLookup
    # Maximum number of retries to query the address server upon initialization
    # nacos.core.address-server.retry=5
    ## Server domain name address of [address-server] mode
    # address.server.domain=jmenv.tbsite.net
    ## Server port of [address-server] mode
    # address.server.port=8080
    ## Request address of [address-server] mode
    # address.server.url=/nacos/serverlist
    #*************** JRaft Related Configurations ***************#
    ### Sets the Raft cluster election timeout, default value is 5 second
    # nacos.core.protocol.raft.data.election_timeout_ms=5000
    ### Sets the amount of time the Raft snapshot will execute periodically, default is 30 minute
    # nacos.core.protocol.raft.data.snapshot_interval_secs=30
    ### raft internal worker threads
    # nacos.core.protocol.raft.data.core_thread_num=8
    ### Number of threads required for raft business request processing
    # nacos.core.protocol.raft.data.cli_service_thread_num=4
    ### raft linear read strategy. Safe linear reads are used by default, that is, the Leader tenure is confirmed by heartbeat
    # nacos.core.protocol.raft.data.read_index_type=ReadOnlySafe
    ### rpc request timeout, default 5 seconds
    # nacos.core.protocol.raft.data.rpc_request_timeout_ms=5000
    #*************** Distro Related Configurations ***************#
    ### Distro data sync delay time, when sync task delayed, task will be merged for same data key. Default 1 second.
    # nacos.core.protocol.distro.data.sync.delayMs=1000
    ### Distro data sync timeout for one sync data, default 3 seconds.
    # nacos.core.protocol.distro.data.sync.timeoutMs=3000
    ### Distro data sync retry delay time when sync data failed or timeout, same behavior with delayMs, default 3 seconds.
    # nacos.core.protocol.distro.data.sync.retryDelayMs=3000
    ### Distro data verify interval time, verify synced data whether expired for a interval. Default 5 seconds.
    # nacos.core.protocol.distro.data.verify.intervalMs=5000
    ### Distro data verify timeout for one verify, default 3 seconds.
    # nacos.core.protocol.distro.data.verify.timeoutMs=3000
    ### Distro data load retry delay when load snapshot data failed, default 30 seconds.
    # nacos.core.protocol.distro.data.load.retryDelayMs=30000
    ### enable to support prometheus service discovery
    #nacos.prometheus.metrics.enabled=true
kind: ConfigMap
metadata:
  name: nacos-cm

```

3. nacos.yaml
```aidl
apiVersion: apps/v1
kind: Deployment
metadata:
  labels:
    name: nacos
  name: nacos
spec:
  progressDeadlineSeconds: 600
  replicas: 1
  revisionHistoryLimit: 10
  selector:
    matchLabels:
      name: nacos
  strategy:
    rollingUpdate:
      maxSurge: 25%
      maxUnavailable: 25%
    type: RollingUpdate
  template:
    metadata:
      creationTimestamp: null
      labels:
        name: nacos
    spec:
      containers:
      - env:
        - name: MODE
          value: standalone
        - name: SPRING_DATASOURCE_PLATFORM
          value: mysql
        - name: PREFER_HOST_MODE
          value: hostname
        image: jd-mpc-cn-north-1-inner.jcr.service.jdcloud.com/mpcimage/9ntrain:nacos_2.3.0_safe
        imagePullPolicy: Always
        name: nacos
        resources:
          limits:
            cpu: "1"
            memory: 2Gi
          requests:
            cpu: "1"
            memory: 2Gi
        terminationMessagePath: /dev/termination-log
        terminationMessagePolicy: File
        volumeMounts:
        - mountPath: /home/nacos/conf/application.properties
          name: nacos-cm
          subPath: application.properties
      dnsPolicy: ClusterFirst
      restartPolicy: Always
      schedulerName: default-scheduler
      securityContext: {}
      terminationGracePeriodSeconds: 30
      volumes:
      - configMap:
          defaultMode: 420
          items:
          - key: application.properties
            path: application.properties
          name: nacos-cm
        name: nacos-cm
---
apiVersion: v1
kind: Service
metadata:
  labels:
    name: nacos-svc
  name: nacos-svc
spec:
  ports:
  - name: http
    nodePort: 30009
    port: 8848
    protocol: TCP
    targetPort: 8848
  - name: http1
    nodePort: 31397
    port: 9848
    protocol: TCP
    targetPort: 9848
  - name: http2
    nodePort: 30140
    port: 9555
    protocol: TCP
    targetPort: 9555
  selector:
    name: nacos
  sessionAffinity: None
  type: NodePort
status:
  loadBalancer: {}
```

nacos主要用来配置coordinator启动需要的文件。将在第9小节中介绍具体配置方式。

## 9 coordinator

1. 初始化数据库

```
use mpc;

# quartz-init.sql
# In your Quartz properties file, you'll need to set
# org.quartz.jobStore.driverDelegateClass = org.quartz.impl.jdbcjobstore.StdJDBCDelegate
#
#
# By: Ron Cordell - roncordell
#  I didn't see this anywhere, so I thought I'd post it here. This is the script from Quartz to create the tables in a MySQL database, modified to use INNODB instead of MYISAM.

DROP TABLE IF EXISTS QRTZ_FIRED_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_PAUSED_TRIGGER_GRPS;
DROP TABLE IF EXISTS QRTZ_SCHEDULER_STATE;
DROP TABLE IF EXISTS QRTZ_LOCKS;
DROP TABLE IF EXISTS QRTZ_SIMPLE_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_SIMPROP_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_CRON_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_BLOB_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_JOB_DETAILS;
DROP TABLE IF EXISTS QRTZ_CALENDARS;

CREATE TABLE QRTZ_JOB_DETAILS(
SCHED_NAME VARCHAR(120) NOT NULL,
JOB_NAME VARCHAR(190) NOT NULL,
JOB_GROUP VARCHAR(190) NOT NULL,
DESCRIPTION VARCHAR(250) NULL,
JOB_CLASS_NAME VARCHAR(250) NOT NULL,
IS_DURABLE VARCHAR(1) NOT NULL,
IS_NONCONCURRENT VARCHAR(1) NOT NULL,
IS_UPDATE_DATA VARCHAR(1) NOT NULL,
REQUESTS_RECOVERY VARCHAR(1) NOT NULL,
JOB_DATA BLOB NULL,
PRIMARY KEY (SCHED_NAME,JOB_NAME,JOB_GROUP))
ENGINE=InnoDB;

CREATE TABLE QRTZ_TRIGGERS (
SCHED_NAME VARCHAR(120) NOT NULL,
TRIGGER_NAME VARCHAR(190) NOT NULL,
TRIGGER_GROUP VARCHAR(190) NOT NULL,
JOB_NAME VARCHAR(190) NOT NULL,
JOB_GROUP VARCHAR(190) NOT NULL,
DESCRIPTION VARCHAR(250) NULL,
NEXT_FIRE_TIME BIGINT(13) NULL,
PREV_FIRE_TIME BIGINT(13) NULL,
PRIORITY INTEGER NULL,
TRIGGER_STATE VARCHAR(16) NOT NULL,
TRIGGER_TYPE VARCHAR(8) NOT NULL,
START_TIME BIGINT(13) NOT NULL,
END_TIME BIGINT(13) NULL,
CALENDAR_NAME VARCHAR(190) NULL,
MISFIRE_INSTR SMALLINT(2) NULL,
JOB_DATA BLOB NULL,
PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
FOREIGN KEY (SCHED_NAME,JOB_NAME,JOB_GROUP)
REFERENCES QRTZ_JOB_DETAILS(SCHED_NAME,JOB_NAME,JOB_GROUP))
ENGINE=InnoDB;

CREATE TABLE QRTZ_SIMPLE_TRIGGERS (
SCHED_NAME VARCHAR(120) NOT NULL,
TRIGGER_NAME VARCHAR(190) NOT NULL,
TRIGGER_GROUP VARCHAR(190) NOT NULL,
REPEAT_COUNT BIGINT(7) NOT NULL,
REPEAT_INTERVAL BIGINT(12) NOT NULL,
TIMES_TRIGGERED BIGINT(10) NOT NULL,
PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
FOREIGN KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
REFERENCES QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP))
ENGINE=InnoDB;

CREATE TABLE QRTZ_CRON_TRIGGERS (
SCHED_NAME VARCHAR(120) NOT NULL,
TRIGGER_NAME VARCHAR(190) NOT NULL,
TRIGGER_GROUP VARCHAR(190) NOT NULL,
CRON_EXPRESSION VARCHAR(120) NOT NULL,
TIME_ZONE_ID VARCHAR(80),
PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
FOREIGN KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
REFERENCES QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP))
ENGINE=InnoDB;

CREATE TABLE QRTZ_SIMPROP_TRIGGERS
  (
    SCHED_NAME VARCHAR(120) NOT NULL,
    TRIGGER_NAME VARCHAR(190) NOT NULL,
    TRIGGER_GROUP VARCHAR(190) NOT NULL,
    STR_PROP_1 VARCHAR(512) NULL,
    STR_PROP_2 VARCHAR(512) NULL,
    STR_PROP_3 VARCHAR(512) NULL,
    INT_PROP_1 INT NULL,
    INT_PROP_2 INT NULL,
    LONG_PROP_1 BIGINT NULL,
    LONG_PROP_2 BIGINT NULL,
    DEC_PROP_1 NUMERIC(13,4) NULL,
    DEC_PROP_2 NUMERIC(13,4) NULL,
    BOOL_PROP_1 VARCHAR(1) NULL,
    BOOL_PROP_2 VARCHAR(1) NULL,
    PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
    REFERENCES QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP))
ENGINE=InnoDB;

CREATE TABLE QRTZ_BLOB_TRIGGERS (
SCHED_NAME VARCHAR(120) NOT NULL,
TRIGGER_NAME VARCHAR(190) NOT NULL,
TRIGGER_GROUP VARCHAR(190) NOT NULL,
BLOB_DATA BLOB NULL,
PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
INDEX (SCHED_NAME,TRIGGER_NAME, TRIGGER_GROUP),
FOREIGN KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
REFERENCES QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP))
ENGINE=InnoDB;

CREATE TABLE QRTZ_CALENDARS (
SCHED_NAME VARCHAR(120) NOT NULL,
CALENDAR_NAME VARCHAR(190) NOT NULL,
CALENDAR BLOB NOT NULL,
PRIMARY KEY (SCHED_NAME,CALENDAR_NAME))
ENGINE=InnoDB;

CREATE TABLE QRTZ_PAUSED_TRIGGER_GRPS (
SCHED_NAME VARCHAR(120) NOT NULL,
TRIGGER_GROUP VARCHAR(190) NOT NULL,
PRIMARY KEY (SCHED_NAME,TRIGGER_GROUP))
ENGINE=InnoDB;

CREATE TABLE QRTZ_FIRED_TRIGGERS (
SCHED_NAME VARCHAR(120) NOT NULL,
ENTRY_ID VARCHAR(95) NOT NULL,
TRIGGER_NAME VARCHAR(190) NOT NULL,
TRIGGER_GROUP VARCHAR(190) NOT NULL,
INSTANCE_NAME VARCHAR(190) NOT NULL,
FIRED_TIME BIGINT(13) NOT NULL,
SCHED_TIME BIGINT(13) NOT NULL,
PRIORITY INTEGER NOT NULL,
STATE VARCHAR(16) NOT NULL,
JOB_NAME VARCHAR(190) NULL,
JOB_GROUP VARCHAR(190) NULL,
IS_NONCONCURRENT VARCHAR(1) NULL,
REQUESTS_RECOVERY VARCHAR(1) NULL,
PRIMARY KEY (SCHED_NAME,ENTRY_ID))
ENGINE=InnoDB;

CREATE TABLE QRTZ_SCHEDULER_STATE (
SCHED_NAME VARCHAR(120) NOT NULL,
INSTANCE_NAME VARCHAR(190) NOT NULL,
LAST_CHECKIN_TIME BIGINT(13) NOT NULL,
CHECKIN_INTERVAL BIGINT(13) NOT NULL,
PRIMARY KEY (SCHED_NAME,INSTANCE_NAME))
ENGINE=InnoDB;

CREATE TABLE QRTZ_LOCKS (
SCHED_NAME VARCHAR(120) NOT NULL,
LOCK_NAME VARCHAR(40) NOT NULL,
PRIMARY KEY (SCHED_NAME,LOCK_NAME))
ENGINE=InnoDB;

CREATE INDEX IDX_QRTZ_J_REQ_RECOVERY ON QRTZ_JOB_DETAILS(SCHED_NAME,REQUESTS_RECOVERY);
CREATE INDEX IDX_QRTZ_J_GRP ON QRTZ_JOB_DETAILS(SCHED_NAME,JOB_GROUP);

CREATE INDEX IDX_QRTZ_T_J ON QRTZ_TRIGGERS(SCHED_NAME,JOB_NAME,JOB_GROUP);
CREATE INDEX IDX_QRTZ_T_JG ON QRTZ_TRIGGERS(SCHED_NAME,JOB_GROUP);
CREATE INDEX IDX_QRTZ_T_C ON QRTZ_TRIGGERS(SCHED_NAME,CALENDAR_NAME);
CREATE INDEX IDX_QRTZ_T_G ON QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_GROUP);
CREATE INDEX IDX_QRTZ_T_STATE ON QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_N_STATE ON QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP,TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_N_G_STATE ON QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_GROUP,TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_NEXT_FIRE_TIME ON QRTZ_TRIGGERS(SCHED_NAME,NEXT_FIRE_TIME);
CREATE INDEX IDX_QRTZ_T_NFT_ST ON QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_STATE,NEXT_FIRE_TIME);
CREATE INDEX IDX_QRTZ_T_NFT_MISFIRE ON QRTZ_TRIGGERS(SCHED_NAME,MISFIRE_INSTR,NEXT_FIRE_TIME);
CREATE INDEX IDX_QRTZ_T_NFT_ST_MISFIRE ON QRTZ_TRIGGERS(SCHED_NAME,MISFIRE_INSTR,NEXT_FIRE_TIME,TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_NFT_ST_MISFIRE_GRP ON QRTZ_TRIGGERS(SCHED_NAME,MISFIRE_INSTR,NEXT_FIRE_TIME,TRIGGER_GROUP,TRIGGER_STATE);

CREATE INDEX IDX_QRTZ_FT_TRIG_INST_NAME ON QRTZ_FIRED_TRIGGERS(SCHED_NAME,INSTANCE_NAME);
CREATE INDEX IDX_QRTZ_FT_INST_JOB_REQ_RCVRY ON QRTZ_FIRED_TRIGGERS(SCHED_NAME,INSTANCE_NAME,REQUESTS_RECOVERY);
CREATE INDEX IDX_QRTZ_FT_J_G ON QRTZ_FIRED_TRIGGERS(SCHED_NAME,JOB_NAME,JOB_GROUP);
CREATE INDEX IDX_QRTZ_FT_JG ON QRTZ_FIRED_TRIGGERS(SCHED_NAME,JOB_GROUP);
CREATE INDEX IDX_QRTZ_FT_T_G ON QRTZ_FIRED_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP);
CREATE INDEX IDX_QRTZ_FT_TG ON QRTZ_FIRED_TRIGGERS(SCHED_NAME,TRIGGER_GROUP);

commit;
```

2. 配置NACOS信息
- 2.1  APPLICATION_GROUP
    - data_id=application.properties
        - MYSQL_URL
        - MYSQL_USERNAME
        - MYSQL_PASSWORD
        - REDIS_SERVICE_IP
        - REDIS_SERVICE_PORT
        - REDIS_PASSWORD
        - PROXY_PORT_IN
        - PROXY_PORT_OUT
        - COORDINATOR_SERVICE_PORT_IN=22034
        - COORDINATOR_NODE_PORT_OUT=32461
        - COORDINATOR_NODE_PORT_IN=32460
        - PORTAL_URL
    

```
target=$PROXY_TARGET
jdTarget=9n_demo_1
server.port=8080

# k8s set
k8s.namespace=mpc-chk-test
k8s.config.path=/k8s/k8sconfig.yaml
k8s.yaml.intersection.path=/k8s/intersection-ext.yaml
k8s.yaml.feature.path=/k8s/feature-ext.yaml
k8s.yaml.train.path=/k8s/train-ext.yaml
k8s.yaml.jxz.path=/k8s/train-ext.yaml
k8s.yaml.unicom.path=/k8s/unicom-ext.yaml
k8s.name.prefix=pk
k8s.ext.psi.image= mirror.jd.com/yili-cdp/mpc-psi-worker:release.yili-cdp.header.beta
k8s.jd.psi.image= mirror.jd.com/yili-cdp/mpc-psi-worker:release.yili-cdp.header.beta
k8s.ext.local.image=mirror.jd.com/yili-cdp/mpc-local-python-worker:release.yili-cdp.alpha
k8s.jd.local.image=mirror.jd.com/yili-cdp/mpc-local-python-worker:release.yili-cdp.alpha
k8s.leader.feature.image=mirror.jd.com/9nfl/feature_engineering:leader
k8s.follow.feature.image=mirror.jd.com/9nfl/feature_engineering:follow
k8s.leader.train.image=mirror.jd.com/9nfl/train_image:leader
k8s.follow.train.image=mirror.jd.com/9nfl/train_image:leader
k8s.leader.jxz.image=mirror.jd.com/9nfl/train_image:leader
k8s.follow.jxz.image=mirror.jd.com/9nfl/train_image:leader
k8s.unicom.image=mirror.jd.com/slab/jujube:latest


# datasource set
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.url=$MYSQL_URL
spring.datasource.username=$MYSQL_USERNAME
spring.datasource.password=$MYSQL_PASSWORD
spring.redis.host=$REDIS_SERVICE_IP
spring.redis.port=$REDIS_SERVICE_PORT
spring.redis.password=REDIS_PASSWORD

# proxy ip + port
grpc.proxy.host=$PROXY_NODE_IP
grpc.proxy.port=$PROXY_PORT_IN
grpc.proxy.local-port=$PROXY_PORT_OUT
# coordinator
grpc.server.port=$COORDINATOR_SERVICE_PORT_IN
node.ip=$PROXY_NODE_IP
node.port=$COORDINATOR_NODE_PORT_OUT
grpc.regist.port=$COORDINATOR_NODE_PORT_IN
portal.url=$PORTAL_URL


# other set
mybatis.mapper-locations=classpath:mapper/*.xml
mybatis.type-aliases-package=com/jd/mpc/mapper
feature.server.port=
train.server.port=
mail.url=
mail.receivers=

# ext
spring.servlet.multipart.max-file-size=1000MB
spring.servlet.multipart.max-request-size=1000MB
grpc.server.maxInboundMessageSize=1073741824
grpc.server.maxOutboundMessageSize=1073741824

tde.token=
tde.isProd=true
tde.active=false

s3.prefix=
spark.master=
spark.image.source=
spark.image=
spring.task.scheduling.pool.size=5

zeebe.client.broker.gateway-address=11.136.250.28:32165
zeebe.client.security.plaintext=true
zeebe.runID.prefix=zeebe
zeebe.monitor-address=mpc-zeebe-monitor-test.jd.local
cfs.node-path.prefix=/mnt/cfs-test-env

spring.quartz.job-store-type=jdbc
spring.quartz.overwrite-existing-jobs=true
spring.quartz.jdbc.comment-prefix=QRTZ_
spring.quartz.properties.org.quartz.scheduler.instanceName=DistributedScheduler
spring.quartz.properties.org.quartz.scheduler.instanceId=AUTO
spring.quartz.properties.org.quartz.threadPool.class=org.springframework.scheduling.quartz.SimpleThreadPoolTaskExecutor
spring.quartz.properties.org.quartz.threadPool.threadCount=4
spring.quartz.properties.org.quartz.jobStore.useProperties=true
spring.quartz.properties.org.quartz.jobStore.isClustered=true
spring.quartz.properties.org.quartz.jobStore.clusterCheckinInterval=10000
spring.quartz.properties.org.quartz.jobStore.class=org.quartz.impl.jdbcjobstore.JobStoreTX
spring.quartz.properties.org.quartz.jobStore.driverDelegateClass=org.quartz.impl.jdbcjobstore.StdJDBCDelegate
spring.quartz.properties.org.quartz.jobStore.dataSource=quartzDS
spring.quartz.properties.org.quartz.dataSource.quartzDS.driver=com.mysql.cj.jdbc.Driver
spring.quartz.properties.org.quartz.dataSource.quartzDS.URL=MYSQL_URL
spring.quartz.properties.org.quartz.dataSource.quartzDS.user=MYSQL_USERNAME
spring.quartz.properties.org.quartz.dataSource.quartzDS.password=MYSQL_PASSWORD
spring.quartz.properties.org.quartz.dataSource.quartzDS.provider=hikaricp
spring.quartz.properties.org.quartz.dataSource.quartzDS.maximumPoolSize=4
spring.quartz.properties.org.quartz.dataSource.quartzDS.connectionTestQuery=SELECT 1
spring.quartz.properties.org.quartz.dataSource.quartzDS.validationTimeout=50000
spring.quartz.properties.org.quartz.dataSource.quartzDS.idleTimeout=0

schedulerTarget=9n_demo_1

# 本侧es地址
user.es.url=
es.user=
es.pwd=

#coordinate配置
coordinate.redis.key=coordinator-portal-pk
target.token.str=X-Token
target.token=test
```

- 2.2 K8S_GROUP
  - data_id=psi.yaml
该分组下只有一个配置，即需要配置PSI算子的启动YAML，见第10小节中yaml。

- 2.3 FUNCTOR_GROUP
  - data_id=psi.properties
    ```
    tmp-dir=/mnt/tmp
    send-back=true
    log-level=DEBUG
    cpu-cores=16
    csv-header=true
    ```

- 2.4 nacos配置
  - 可以通过浏览器配置
    - 没有网络隔离时采用该种方式，直接打开nacos自带前端页面访问nacos。按以下步骤进行：
      - 创建命名空间$NAMESPACE
      - 创建分组APPLICATION_GROUP，创建配置文件application.properties，将coordinator配置文件填入并发布。
      - 创建分组K8S_GROUP，创建配置文件psi.yaml。
      - 创建分组FUNCTOR_GROUP，创建配置文件psi.properties。
  - 无法通过浏览器配置
    - 若部署服务的机器与本地机器有隔离，可以通过api的方式配置，将以上信息配置进入。
    - 可参考配置脚本：nacos_init.sh


3. 创建coordinator配置configmap
- COORDINATOR_CONF=coordinator-conf
- NACOS_DOMAIN：请使用本集群上部署的nacos的svc域名
- NAMESPACE

```
apiVersion: v1
kind: ConfigMap
metadata:
  name: $COORDINATOR_CONF
data:
  application.properties: |
    nacos.config.server-addr=$NACOS_DOMAIN
    nacos.config.remote-first=true
    nacos.config.data-id=application.properties
    nacos.config.namespace=$NAMESPACE
    nacos.config.group=APPLICATION_GROUP
    nacos.config.type=properties
    nacos.config.auto-refresh=true
    nacos.config.local-disk-cache-dir=/k8s/nacos
```

4. 创建coordinator的K8S认证configmap

- K8S-CONF=k8s-conf
```
apiVersion: v1
data:
  k8sconfig.yaml: |+
    apiVersion: v1
    clusters:
    - cluster:
        server: 
        insecure-skip-tls-verify: true
      name: kubernetes
    contexts:
    - context:
        cluster: kubernetes
        user: basic-authentication
      name: basic-authentication@kubernetes
    - context:
        cluster: kubernetes
        user: cert-authentication
      name: cert-authentication@kubernetes
    current-context: cert-authentication@kubernetes
    kind: Config
    preferences: {}
    users:
    - name: basic-authentication
      user:
        username: kube-admin
        password: 
    - name: cert-authentication
      user:
        client-certificate-data: 
        client-key-data:
```

该项配置是k8s集群的认证信息，用于coordinator起pod时使用。

5. 创建coordinator deployment
- COORDINATOR_IMAGE
- VOLUME_LOGS
- VOLUME_DATA


```
apiVersion: apps/v1
kind: Deployment
metadata:
  labels:
    app: coordinator
  name: coordinator
spec:
  replicas: 2
  selector:
    matchLabels:
      app: coordinator
  template:
    metadata:
      labels:
        app: coordinator
    spec:
      containers:
      - image: $COORDINATOR_IMAGE
        imagePullPolicy: Always
        name: coordinator
        volumeMounts:
        - mountPath: /home/config/application.properties
          name: coordinator-conf
          readOnly: true
          subPath: application.properties
        - mountPath: /home/config/k8sconfig.yaml
          name: k8s-conf
          readOnly: true
          subPath: k8sconfig.yaml
        - mountPath: /k8s
          name: k8s
        - mountPath: /mnt/logs
          name: logs
        resources:
          limits:
            cpu: "4"
            memory: 8Gi
          requests:
            cpu: "4"
            memory: 8Gi
      restartPolicy: Always
      volumes:
      - hostPath:
          path: $VOLUME_LOGS
          type: DirectoryOrCreate
        name: logs
      - hostPath:
          path: $VOLUME_DATA
          type: DirectoryOrCreate
        name: k8s
      - configMap:
          defaultMode: 420
          name: coordinator-conf
        name: coordinator-conf
      - configMap:
          defaultMode: 420
          name: k8s-conf
        name: k8s-conf
```

6. 创建coordinator service
- COORDINATOR_NODE_PORT_OUT=32461
- COORDINATOR_NODE_PORT_IN=32460
- COORDINATOR_SERVICE_PORT_OUT=8080
- COORDINATOR_SERVICE_PORT_IN=22034
- COORDINATOR_POD_PORT_OUT=8080
- COORDINATOR_POD_PORT_IN=22034

```
apiVersion: v1
kind: Service
metadata:
  labels:
    name: coordinator
  name: coordinator
spec:
  ports:
  - name: coo-http
    nodePort: $COORDINATOR_NODE_PORT_OUT
    port: $COORDINATOR_SERVICE_PORT_OUT
    protocol: TCP
    targetPort: $COORDINATOR_POD_PORT_OUT
  - name: coo-http2
    nodePort: $COORDINATOR_NODE_PORT_IN
    port: $COORDINATOR_SERVICE_PORT_IN
    protocol: TCP
    targetPort: $COORDINATOR_POD_PORT_IN
  selector:
    app: coordinator
  type: NodePort
```

## 10 PSI
- NAMESPACE
- PSI_IMAGE
- VOLUME_LOGS
- VOLUME_DATA

PSI挂载路径需要与fileservice一致，否则会找不到文件。PSI不需要手动启动，在隐私计算平台配置好求交任务点击运行即可。

```
apiVersion: apps/v1
kind: Deployment
metadata:
  labels:
    name: psi-worker
  name: psi-worker
  namespace: $NAMESPACE
spec:
  replicas: 1
  selector:
    matchLabels:
      name: psi-worker
  template:
    metadata:
      labels:
        name: psi-worker
    spec:
      containers:
        - image: $PSI_IMAGE
          imagePullPolicy: IfNotPresent
          name: psi-worker
          env:
          - name: Local_IP
            valueFrom:
              fieldRef:
                apiVersion: v1
                fieldPath: status.podIP          
          ports:
            - containerPort: 22020
              name: http
              protocol: TCP
          resources:
            limits:
              cpu: "16"
              memory: 16Gi
            requests:
              cpu: "16"
              memory: 16Gi
          volumeMounts:
            - mountPath: /mnt/data
              name: data
            - mountPath: /mnt/logs
              name: logs
            - mountPath: /dev/shm
              name: dshm
      dnsPolicy: ClusterFirst
      restartPolicy: Always
      volumes:
        - name: data
          hostPath:
            path: $VOLUME_DATA
            type: DirectoryOrCreate
        - name: logs
          hostPath:
            path: $VOLUME_LOGS
            type: DirectoryOrCreate
        - name: dshm
          emptyDir:
            medium: Memory

```


