二. 分布式版安装指导:

1. 制作基础镜像

`cd /app/9nfl_opensource/deploy/data_join/images` 

`docker build -t  Base_Mirror . -f Base_Dockerfile`

把"Base_Mirror"替换成你自己定义的镜像名 

2. 制作数据求交镜像

`cd /app/9nfl_opensource/deploy/data_join/images`

`docker build -t  DataJoin_Mirror . -f Base_Dockerfile`

把"DataJoin_Mirror"替换成你自己定义的镜像名称

leader侧镜像和follower侧镜像制作步骤一样

3. 部署

`cd /app/9nfl_opensource/deploy/data_join/k8s`

把deployment_worker.yaml的环境变量替换成真实值以后, 部署pod到k8s集群;

`deployment_worker.yaml | kubectl --namespace=${NAMESPACE} create -f -`
