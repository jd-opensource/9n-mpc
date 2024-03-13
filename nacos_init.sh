#!/bin/sh

NACOS_DOMAIN="http://IP:8848"
NACOS_CREATE_NAMESPACE_URL="$NACOS_DOMAIN/nacos/v1/console/namespaces"
NACOS_CONFIG__URL="$NACOS_DOMAIN/nacos/v1/cs/configs"
TENANT="mpc-mg-test"
echo "$NACOS_CREATE_NAMESPACE_URL"

# 创建命名空间
curl -X POST  "$NACOS_CREATE_NAMESPACE_URL"  -d "customNamespaceId=$TENANT&namespaceName=$TENANT&namespaceDesc=test mangguo psi"


# 导入配置
curl -X POST "$NACOS_CONFIG__URL"  \
  -d "dataId=application.properties&group=APPLICATION_GROUP&tenant=$TENANT&type=properties&content=target=mpc-chk-test
jdTarget=9n_demo_1
server.port=
# k8s set
k8s.namespace=
k8s.config.path=/k8s/k8sconfig.yaml
k8s.yaml.intersection.path=/k8s/intersection-ext.yaml
k8s.yaml.feature.path=/k8s/feature-ext.yaml
k8s.yaml.train.path=/k8s/train-ext.yaml
k8s.yaml.jxz.path=/k8s/train-ext.yaml
k8s.yaml.unicom.path=/k8s/unicom-ext.yaml
k8s.name.prefix=pk
k8s.ext.psi.image=mirror.jd.com/yili-cdp/mpc-psi-worker:release.yili-cdp.header.beta
k8s.jd.psi.image=mirror.jd.com/yili-cdp/mpc-psi-worker:release.yili-cdp.header.beta
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
spring.datasource.url=
spring.datasource.username=
spring.datasource.password=
spring.redis.host=
spring.redis.port=
spring.redis.password=
# proxy ip + port
grpc.proxy.host=
grpc.proxy.port=
grpc.proxy.local-port=
# coordinator
grpc.server.port=
node.ip=
node.port=
grpc.regist.port=
portal.url=
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
zeebe.client.broker.gateway-address=
zeebe.client.security.plaintext=true
zeebe.runID.prefix=zeebe
zeebe.monitor-address=mpc-zeebe-monitor-test.jd.local
cfs.node-path.prefix=
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
spring.quartz.properties.org.quartz.dataSource.quartzDS.URL=
spring.quartz.properties.org.quartz.dataSource.quartzDS.user=
spring.quartz.properties.org.quartz.dataSource.quartzDS.password=
spring.quartz.properties.org.quartz.dataSource.quartzDS.provider=hikaricp
spring.quartz.properties.org.quartz.dataSource.quartzDS.maximumPoolSize=4
spring.quartz.properties.org.quartz.dataSource.quartzDS.connectionTestQuery=SELECT 1
spring.quartz.properties.org.quartz.dataSource.quartzDS.validationTimeout=50000
spring.quartz.properties.org.quartz.dataSource.quartzDS.idleTimeout=0
schedulerTarget=
# 本侧es地址
user.es.url=
es.user=
es.pwd=
#coordinate配置
coordinate.redis.key=coordinator-portal-pk
target.token.str=X-Token
target.token=test"


# 导入配置
curl -X POST "$NACOS_CONFIG__URL"  \
  -d "dataId=psi.yaml&group=K8S_GROUP&tenant=$TENANT&type=yaml&content=apiVersion: apps/v1
kind: Deployment
metadata:
  labels:
    name: psi-worker
  name: psi-worker
  namespace:
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
      imagePullSecrets:
        - name: jd-cloud-secret
      containers:
        - image:
          imagePullPolicy: Always
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
            - mountPath: /mnt/data1
              name: data1
            - mountPath: /mnt/logs
              name: logs
            - mountPath: /dev/shm
              name: dshm
      dnsPolicy: ClusterFirst
      restartPolicy: Always
      volumes:
        - name: data
          hostPath:
            path: /mnt/cfs/mpc-test/test/mpc-chk-test/data
            type: DirectoryOrCreate
        - name: data1
          hostPath:
            path: /mnt/cfs/mpc-test/test/mpc-chk-test/data
            type: DirectoryOrCreate
        - name: logs
          hostPath:
            path: /mnt/cfs/mpc-test/test/mpc-chk-test/logs
            type: DirectoryOrCreate
        - name: dshm
          emptyDir:
            medium: Memory"



# 导入配置
curl -X POST "$NACOS_CONFIG__URL"  \
  -d "dataId=psi.properties&group=FUNCTOR_GROUP&tenant=$TENANT&type=properties&content=tmp-dir=/mnt/tmp
send-back=true
log-level=DEBUG
cpu-cores=16
csv-header=true"




