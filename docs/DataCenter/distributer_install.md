Cluster Deployment Guide:

1. Make Data Center Image

`cd /app/9nfl_opensource/deploy/data_center/images`
`docker build -t  Data_Center_Mirror . -f Data_Center_Dockerfile`

please replace "Data_Center_Mirror"  with your data join image name 

when Making follower data center image,need to change mysql config as follower mysql,
 the method of making follower data center image is the same  as above
