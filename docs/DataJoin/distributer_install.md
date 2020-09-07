二. Cluster Deployment Guide:

1. Make Base Image

`cd /app/9nfl_opensource/deploy/data_join/images` 

`docker build -t  Base_Mirror . -f Base_Dockerfile`

please replace "Base_Mirror"  with your base image name 

2. Make  Data Join Image

`cd /app/9nfl_opensource/deploy/data_join/images`

`docker build -t  DataJoin_Mirror . -f Base_Dockerfile`

please replace "DataJoin_Mirror"  with your data join image name 

Making leader data join image  and follower data join image is the same  as the second step

3. deploy

`cd /app/9nfl_opensource/deploy/data_join/k8s`

After finished setting Environment variables, and replace the Environment variables of 
the deployment_worker.yaml with real value;

`deployment_worker.yaml | kubectl --namespace=${NAMESPACE} create -f -`
