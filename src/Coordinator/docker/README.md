# coordinator构建说明
1. 在docker目录下 执行 docker  build . -f Dockerfile_base -t  xxxxx
2. 替换Dockerfile中jd-mpc-cn-north-1-inner.jcr.service.jdcloud.com/mpcimage/9ntrain:coor_base1为xxxxx
3. 构建项目  执行mvn clean package
4. 将target目录下的mpc-coordinator-1.0-SNAPSHOT.jar复制到docker目录下
5. 在docker目录下执行 docker  build . -f Dockerfile_base -t  coordinator镜像名
