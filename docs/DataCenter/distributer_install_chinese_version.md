分布式版安装指导:

1. 制作Data_Center镜像

`cd /app/9nfl_opensource/deploy/data_center/images`
`docker build -t  Data_Center_Mirror . -f Data_Center_Dockerfile`

把"Data_Center_Mirror"替换成你自己定义的镜像名 

当制作follower data center镜像时,需要改变mysql数据库的配置，把follower侧数据库的配置写入到配置文件,
 制作方法和leader data center 一样
