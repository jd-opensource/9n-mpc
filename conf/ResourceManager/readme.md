# K8S conf for deploy Fedlearner
k8s.conf is used to deploy the JD Federal Learning System in the kubernetes cluster. JD Federation Learning is divided into leader and follower sides, and the k8s.conf on both sides should be different.
The configuration file is divided into 4 modules：
+ coordinator
Ip and Port configured as the coordinator on the corresponding side。
+ proxy
Ip and Port configured as the proxy on the corresponding side。
+ image
Image for DataCenter and Trainer。
+ train
Start command for trainer。
+ save
JD Federation Learning use hdfs to save the model. This module is used to configure the path for checkpoint and models on both sides.
{leader/follower}_model_dir is used to save checkpoint. {leader/follower}_export_dir is used to finally export the model. The four directories cannot be the same.