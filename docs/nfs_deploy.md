# NFS服务部署
## 服务端
### NFS服务端软件包
```
yum install -y nfs-utils
```
### 设置可访问NFS的主机并增加配置
```
yum install -y nfs-utils
```
向其中增加配置{shared_path} {IP_Cluster}/IP(rw,sync,fsid=0)  
比如 /home/nfs/ 192.168.241.0/24(rw,sync,fsid=0)
### 启动NFS服务
```
systemctl enable rpcbind.service
systemctl enable nfs-server.service

systemctl start rpcbind.service
systemctl start nfs-server.service
```
### 确认NFS服务生效
```
rpcinfo -p
exportfs -r
exportfs
```

## 客服端
### 启动基础服务
```
systemctl enable rpcbind.service
systemctl start rpcbind.service
```
### 检查NFS服务目录
```
showmount -e {nfs_server_ip}
```
### 挂载到客服端
```
cd /mnt && mkdir /nfs
mount -t nfs {nfs_server_ip}:{nfs_path} /mnt/nfs
```
以上步骤操作完毕后，可以进一步设置客服端到k8的Pod的挂载目录