# 部署 JD Fedlearner
## On Kubernetes
京东联邦学习系统支持部署在k8s集群中，并利用[KubeFlow](https://www.kubeflow.org/)中的[tf-operator](https://github.com/kubeflow/tf-operator)进行训练集群搭建、管理、状态监控。
### 安装Kubernetes集群
如果你已经有了自己的k8s集群，则调过该步骤。
你可以选择搭建自己一套k8s集群或者搭建一套[MiniKube](https://kubernetes.io/docs/tasks/tools/install-minikube/)以快速验证。集群搭建完成后，请保证coordinator环境中的kubectl能够正常访问你搭建的k8s集群。
### 创建NameSpace
一般情况下，联邦学习系统应该部署在不同的kubernetes集群。京东联邦学习系统将leader角色部署在名为`fl-leader`的NameSpace中，follower角色部署在名为`fl-follower`的NameSpace中。必须提前创建这两个NameSpace。
`kubectl create ns fl-leader`
`kubectl create ns fl-follower`
### 安装KubeFlow
按照[KubeFlow安装](https://www.kubeflow.org/docs/started/getting-started/)部署KubeFlow，目前我们测试的版本为v0.4。如果需要安装更高版本，则对应修改适配`src/ResourceManager/template`中的[任务模板](https://git.jd.com/ads-conversion/9nfl_opensource/tree/resource_manager/src/ResourceManager/template)即可。
### HDFS
京东联邦学习系统依赖HDFS来保存checkpoints和导出模型, 所以请确认镜像中配置有HDFS访问环境, 并且根据教程[TensorFlow on Hadoop](https://github.com/tensorflow/examples/blob/master/community/en/docs/deploy/hadoop.md) 来确保TensorFlow能够访问HDFS集群。然后[配置](https://git.jd.com/ads-conversion/9nfl_opensource/tree/resource_manager/conf/ResourceManager)相关路径。
## coordinator
### kubernetes
coordinator会将联邦学习任务部署在k8s集群中，所以请保证k8s集群已经处于可用状态，kubectl配置正确，能够正常访问自己的k8s集群。
另外coordinator依赖python2以及jinja2用于将任务部署在k8s集群中。jinja2安装方法：
`pip install Jinja2==2.11.2`