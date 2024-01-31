package com.jd.mpc.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import jakarta.annotation.Resource;

import com.alibaba.nacos.api.annotation.NacosInjected;
import com.alibaba.nacos.api.config.ConfigService;
import com.jd.mpc.common.constant.CommonConstant;
import com.jd.mpc.common.util.CommonUtils;
import io.fabric8.kubernetes.api.model.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jd.mpc.common.constant.DeploymentPathConstant;
import com.jd.mpc.common.enums.TaskStatusEnum;
import com.jd.mpc.common.enums.TaskTypeEnum;
import com.jd.mpc.common.response.CommonException;
import com.jd.mpc.domain.offline.commons.OfflineTask;
import com.jd.mpc.domain.offline.commons.SubTask;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import lombok.extern.slf4j.Slf4j;

/**
 * 参数解析
 *
 * 
 * @date 2021/12/6 3:54 下午
 */
@Component
@Slf4j
public class PodFactory {

    @Value("${k8s.namespace}")
    private String namespace;

    @Resource
    private TaskPersistenceService taskPersistenceService;

    @NacosInjected
    private ConfigService configService;

    @Transactional
    public void createPodList(SubTask subTask, DefaultKubernetesClient k8sClient) {

        // 批量创建任务
        subTask.getTasks().forEach(offlineTask -> {
            String id = offlineTask.getId();
            Integer subId = offlineTask.getSubId();
            Integer taskIndex = offlineTask.getTaskIndex();
            String clusterId = id + "-" + subId + "-" + taskIndex;

            this.createDeployment(offlineTask, subTask.getEnv(), k8sClient, clusterId);
            // 更新状态
            offlineTask.setStatus(TaskStatusEnum.RUNNING.getStatus());
            taskPersistenceService.updateChildrenTaskStatus(id, subId, taskIndex, TaskStatusEnum.RUNNING.getStatus());
        });
    }

    private void createDeployment(OfflineTask offlineTask, String env, DefaultKubernetesClient k8sClient, String clusterId) {
        MixedOperation<Deployment, DeploymentList, RollableScalableResource<Deployment>> de = k8sClient.apps().deployments();
        Deployment deployment;
        TaskTypeEnum taskType = TaskTypeEnum.getByValue(offlineTask.getTaskType());
        try {
            String yaml;
            if (taskType == null) {
                yaml = configService.getConfig(offlineTask.getDeploymentPath(), CommonConstant.DEFAULT_GROUP, 10000);
            }else {
                yaml = configService.getConfig(offlineTask.getDeploymentPath(), CommonConstant.K8S_GROUP, 10000);
            }
            deployment = de.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))).get();
        }catch (Exception e){
            e.printStackTrace();
            throw new CommonException("read functor's "+offlineTask.getDeploymentPath()+" config failed!");
        }
        PodSpec spec = deployment.getSpec().getTemplate().getSpec();
        this.defaultDeployment(spec, offlineTask, env, deployment, clusterId, de);
        if (!org.apache.commons.lang3.StringUtils.isBlank(offlineTask.getServiceName())) {
            Service service = k8sClient.services().load(K8sService.class.getResourceAsStream(offlineTask.getServicePath())).get();
            service.getMetadata().setNamespace(namespace);
            service.getMetadata().setName(offlineTask.getServiceName());
            service.getMetadata().getLabels().put("name", offlineTask.getServiceName());
            service.getSpec().getSelector().put("app", offlineTask.getServiceName());
            k8sClient.services().create(service);
        }
    }

    private void defaultDeployment(PodSpec spec, OfflineTask offlineTask, String env, Deployment deployment, String clusterId, MixedOperation<Deployment, DeploymentList, RollableScalableResource<Deployment>> de) {
        if (offlineTask.getLabels() != null){
            Map<String, String> labels = deployment.getMetadata().getLabels();
            labels.putAll(offlineTask.getLabels());
        }
        this.fillPod(spec, offlineTask);
        // 测试环境镜像地址替换
        this.replaceEnv(spec, env);
        spec.getContainers().forEach(container -> {
            // 批量创建pod
            for (int i = 0; i < offlineTask.getPodNum(); i++) {
                // 设置名称
                String name = offlineTask.getName() + "-" + i;
                ObjectMeta metadata = deployment.getMetadata();
                metadata.setName(name);
                metadata.setNamespace(namespace);
                if (TaskTypeEnum.NN_MPC.getName().equals(offlineTask.getTaskType())) {
                    metadata.getLabels().put("app", name);
                    deployment.getSpec().getSelector().getMatchLabels().put("app", name);
                    deployment.getSpec().getTemplate().getMetadata().getLabels().put("app", name);
                }
                container.setName(name);
                // 填充容器
                this.fillContainer(container, i, clusterId, offlineTask);
                // 创建pod
                de.create(deployment);
                log.info("启动任务pod:{}", name);
            }
        });
    }

    private void fillPod(PodSpec spec, OfflineTask offlineTask) {
        spec.setNodeSelector(null);
        Container container = spec.getContainers().get(0);
        Map<String, Quantity> requests = container.getResources().getRequests();
        if (offlineTask.getCpu() != null) {
            requests.get("cpu").setAmount(String.valueOf(offlineTask.getCpu()));
        } else {
            offlineTask.setCpu(Integer.valueOf(requests.get("cpu").getAmount()));
        }
        if (offlineTask.getMemory() != null) {
            requests.get("memory").setAmount(String.valueOf(offlineTask.getMemory()));
        } else {
            offlineTask.setMemory(Integer.valueOf(requests.get("memory").getAmount()));
        }
        Map<String, Quantity> limits = container.getResources().getLimits();
        if (offlineTask.getCpu() != null) {
            limits.get("cpu").setAmount(String.valueOf(offlineTask.getCpu()));
        }
        if (offlineTask.getMemory() != null) {
            limits.get("memory").setAmount(String.valueOf(offlineTask.getMemory()));
        }
        // 小规模场景部署专用,变换资源配置单位
        if (offlineTask.getParameters().containsKey("test-unit")) {
            requests.get("memory").setAmount(String.valueOf(offlineTask.getMemory()));
            limits.get("memory").setAmount(String.valueOf(offlineTask.getMemory()));
            requests.get("memory").setFormat("Mi");
            limits.get("memory").setFormat("Mi");
            limits.get("cpu").setAmount(String.valueOf(offlineTask.getCpu()));
            requests.get("cpu").setAmount(String.valueOf(offlineTask.getCpu()));
            requests.get("cpu").setFormat("m");
            limits.get("cpu").setFormat("m");
        }
        List<String> args = container.getArgs();
        // 除了nn所用的mpc其他的增加两个参数
        if (!offlineTask.getTaskType().equals(TaskTypeEnum.NN_MPC.getName())) {
            args.add("--url=" + offlineTask.getUrl());
            args.add("--workdir=" + offlineTask.getWorkDir());
        }
        offlineTask.getParameters().forEach((k, v) -> {
            if (StringUtils.isNotBlank(v)) {
                args.add("--" + k + "=" + v);
            }
        });
        // offlineTask.getParameters().forEach((k, v) -> args.add("--" + k + "=" + v));
        container.setArgs(args);
        if (offlineTask.getImage() != null) {
            container.setImage(offlineTask.getImage());
        }
        if (CollectionUtils.isNotEmpty(offlineTask.getCommands())) {
            container.setCommand(offlineTask.getCommands());
        }
        // TODO 即将交由业务方测试,临时添加的逻辑,后续应该修复
        if (TaskTypeEnum.LR.equals(TaskTypeEnum.getByValue(offlineTask.getTaskType())) && DeploymentPathConstant.TRAIN_BASE.equals(offlineTask.getDeploymentPath())) {
            container.setWorkingDir(offlineTask.getWorkDir());
        }
        log.info("offlineTaskArgs-" + offlineTask.getId() + ":" + Arrays.toString(args.toArray()));
    }


    private void addVolume(PodSpec spec,String volumeName,String hostPath,String mountPath){
        List<VolumeMount> volumeMounts = spec.getContainers().get(0).getVolumeMounts();
        List<Volume> volumes = spec.getVolumes();
        VolumeMount volumeMount = new VolumeMount();
        volumeMount.setName(volumeName);
        volumeMount.setMountPath(mountPath);
        volumeMounts.add(volumeMount);
        Volume volume = new Volume();
        volume.setName(volumeName);
        volume.setHostPath(new HostPathVolumeSource(hostPath,"DirectoryOrCreate"));
        volumes.add(volume);
    }

    private void replaceEnv(PodSpec spec, String env) {
        spec.getContainers().forEach(container -> {
            String image = container.getImage();
        });
    }

    private void fillContainer(Container container, int index, String clusterId, OfflineTask offlineTask) {
        // 设置环境变量
        List<EnvVar> envList = container.getEnv() == null ? new ArrayList<>() : container.getEnv();

        if (offlineTask.getParameters().get("app-id") != null) {
            EnvVar appIdEnv = new EnvVar();
            appIdEnv.setName("APP_ID");
            appIdEnv.setValue(offlineTask.getParameters().get("app-id"));
            envList.add(appIdEnv);
        } else if (offlineTask.getParameters().get("application-id") != null) {
            EnvVar appIdEnv = new EnvVar();
            appIdEnv.setName("APP_ID");
            appIdEnv.setValue(offlineTask.getParameters().get("application-id"));
            envList.add(appIdEnv);
        }

        EnvVar cpuEnv = new EnvVar();
        cpuEnv.setName("CPU");
        cpuEnv.setValue(String.valueOf(offlineTask.getCpu()));
        envList.add(cpuEnv);

        EnvVar memEnv = new EnvVar();
        memEnv.setName("MEMORY");
        memEnv.setValue(String.valueOf(offlineTask.getMemory()));
        envList.add(memEnv);

        EnvVar clusterIdEnv = new EnvVar();
        clusterIdEnv.setName("CLUSTER_ID");
        clusterIdEnv.setValue(clusterId);
        envList.add(clusterIdEnv);

        String nodeId = offlineTask.getTaskIndex().intValue() == 0 ? String.valueOf(index) : (offlineTask.getTaskIndex().toString() + String.format("%03d", index));
        EnvVar nodeIdEnv = new EnvVar();
        nodeIdEnv.setName("NODE_ID");
        nodeIdEnv.setValue(nodeId);
        envList.add(nodeIdEnv);

        EnvVar taskIdEnv = new EnvVar();
        taskIdEnv.setName("TASK_ID");
        taskIdEnv.setValue(offlineTask.getId());
        envList.add(taskIdEnv);

        EnvVar redisHostEnv = new EnvVar();
        redisHostEnv.setName("REDIS_HOST");
        redisHostEnv.setValue(offlineTask.getRedis_server().split(":")[0]);
        envList.add(redisHostEnv);

        EnvVar redisPortEnv = new EnvVar();
        redisPortEnv.setName("REDIS_PORT");
        redisPortEnv.setValue(offlineTask.getRedis_server().split(":")[1]);
        envList.add(redisPortEnv);

        EnvVar redisPassword = new EnvVar();
        redisPassword.setName("REDIS_PASSWORD");
        redisPassword.setValue(offlineTask.getRedis_password());
        envList.add(redisPassword);

        EnvVar localDomain = new EnvVar();
        localDomain.setName("LOCAL_DOMAIN");
        localDomain.setValue(offlineTask.getTarget());
        envList.add(localDomain);

        if (!offlineTask.getTaskType().equals(TaskTypeEnum.NN_MPC.getName())) {
            container.getArgs().add("--node-id=" + index);
            container.getArgs().add("--node-total=" + offlineTask.getPodNum());
        }
        Map<String, List<EnvVar>> envMap = envList.stream().collect(Collectors.groupingBy(EnvVar::getName));
        Map<String, String> extParameters = offlineTask.getExtParameters();
        if (extParameters != null) {
            extParameters.forEach((k, v) -> {
                List<EnvVar> envVars = envMap.get(k);
                if (envVars == null) {
                    EnvVar newEnvVar = new EnvVar();
                    newEnvVar.setName(k);
                    newEnvVar.setValue(v);
                    envList.add(newEnvVar);
                } else {
                    EnvVar oldEnvVar = envVars.get(0);
                    oldEnvVar.setValue(v);
                }
            });
            container.setEnv(envList);
        }
    }

    private void fillWorkerNum(JsonObject rayWorker, OfflineTask offlineTask) {
        rayWorker.addProperty("minWorkers", Integer.parseInt(offlineTask.getParameters().get("worker-nums")));
        rayWorker.addProperty("maxWorkers", Integer.parseInt(offlineTask.getParameters().get("worker-nums")));
    }

    // 填充挂载
    private void fillVolumes(JsonObject podType, String env) {
        JsonObject spec = podType.get("podConfig").getAsJsonObject().get("spec").getAsJsonObject();
        spec.getAsJsonObject("nodeSelector").remove("test-jd");
        if (Objects.equals("test", env)) {
            spec.remove("nodeSelector");
        }
        for (int i = 0; i < spec.getAsJsonArray("volumes").size(); i++) {
            JsonObject volume = spec.getAsJsonArray("volumes").get(i).getAsJsonObject();
            volume.getAsJsonObject("hostPath").addProperty("path", volume.get("hostPath").getAsJsonObject().get("path").getAsString() + "/" + namespace + "/" + volume.get("name").getAsString());
        }

    }

    private void fillHeadEnv(JsonObject rayHead, OfflineTask offlineTask) {
        JsonArray env = new JsonArray();
        JsonObject appIdEnv = new JsonObject();
        appIdEnv.addProperty("name", "APP_ID");
        appIdEnv.addProperty("value", offlineTask.getParameters().get("application-id"));
        env.add(appIdEnv);
        JsonObject cpuEnv = new JsonObject();
        cpuEnv.addProperty("name", "CPU");
        cpuEnv.addProperty("value", offlineTask.getCpu().toString());
        env.add(cpuEnv);
        JsonObject memoryEnv = new JsonObject();
        memoryEnv.addProperty("name", "MEMORY");
        memoryEnv.addProperty("value", offlineTask.getMemory().toString());
        env.add(memoryEnv);
        JsonObject clusterIdEnv = new JsonObject();
        clusterIdEnv.addProperty("name", "CLUSTER_ID");
        clusterIdEnv.addProperty("value", offlineTask.getId() + "-" + String.valueOf(offlineTask.getSubId()) + "-" + offlineTask.getTaskIndex().toString());
        env.add(clusterIdEnv);
        JsonObject localDomainEnv = new JsonObject();
        localDomainEnv.addProperty("name", "LOCAL_DOMAIN");
        localDomainEnv.addProperty("value", offlineTask.getTarget());
        env.add(localDomainEnv);
        String nodeId = offlineTask.getTaskIndex().intValue() == 0 ? String.valueOf(0) : (offlineTask.getTaskIndex().toString() + String.format("%03d", 0));
        JsonObject nodeIdEnv = new JsonObject();
        nodeIdEnv.addProperty("name", "NODE_ID");
        nodeIdEnv.addProperty("value", nodeId);
        env.add(nodeIdEnv);
        JsonObject taskIdEnv = new JsonObject();
        taskIdEnv.addProperty("name", "TASK_ID");
        taskIdEnv.addProperty("value", offlineTask.getId());
        env.add(taskIdEnv);
        JsonObject redisHostEnv = new JsonObject();
        redisHostEnv.addProperty("name", "REDIS_HOST");
        redisHostEnv.addProperty("value", offlineTask.getRedis_server().split(":")[0]);
        env.add(redisHostEnv);
        JsonObject redisPortEnv = new JsonObject();
        redisPortEnv.addProperty("name", "REDIS_PORT");
        redisPortEnv.addProperty("value", offlineTask.getRedis_server().split(":")[1]);
        env.add(redisPortEnv);
        JsonObject redisPwdEnv = new JsonObject();
        redisPwdEnv.addProperty("name", "REDIS_PASSWORD");
        redisPwdEnv.addProperty("value", offlineTask.getRedis_password());
        env.add(redisPwdEnv);

        JsonObject containers = rayHead.getAsJsonObject("podConfig").getAsJsonObject("spec").getAsJsonArray("containers").get(0).getAsJsonObject();
        containers.add("env", env);
    }

    private void fillTfReplicaSpecs(String name, JsonObject tfReplicaSpecs, OfflineTask offlineTask, String env) {
        JsonObject replicaSpecs = tfReplicaSpecs.getAsJsonObject(name);
        if (name.equals("Worker")) {
            replicaSpecs.addProperty("replicas", Integer.parseInt(offlineTask.getParameters().get("worker-nums")) - 1);
        }
        if (name.equals("PS")) {
            replicaSpecs.addProperty("replicas", Integer.parseInt(offlineTask.getParameters().get("ps-nums")));
        }
        JsonObject spec = replicaSpecs.getAsJsonObject("template").getAsJsonObject("spec");
        if (!Objects.equals("test", env)) {
            spec.getAsJsonObject("nodeSelector").remove("mpc");
            spec.remove("serviceAccountName");
        } else {
            spec.getAsJsonObject("nodeSelector").remove("mpc");
        }
        spec.remove("nodeSelector");

        JsonObject container = spec.getAsJsonArray("containers").get(0).getAsJsonObject();
        JsonArray volumes = spec.getAsJsonArray("volumes");
        for (int i = 0; i < volumes.size(); i++) {
            JsonObject volume = volumes.get(i).getAsJsonObject();
            if (CommonConstant.K8S_DATA_VOLUME.equals(volume.get("name").getAsString())){
                volume.getAsJsonObject("hostPath").addProperty("path",CommonUtils.genVolumeHostPath(CommonConstant.K8S_DATA_VOLUME));
            }else if (CommonConstant.K8S_LOG_VOLUME.equals(volume.get("name").getAsString())){
                volume.getAsJsonObject("hostPath").addProperty("path",CommonUtils.genVolumeHostPath(CommonConstant.K8S_LOG_VOLUME));
            }
        }
        try {

            if(offlineTask.getNnImage() != null){
                container.addProperty("image",offlineTask.getNnImage());
            }
            if (offlineTask.getMemory() != null) {
                container.getAsJsonObject("resources").getAsJsonObject("limits").addProperty("memory", String.valueOf(offlineTask.getMemory()) + "Gi");
                container.getAsJsonObject("resources").getAsJsonObject("requests").addProperty("memory", String.valueOf(offlineTask.getMemory()) + "Gi");
            }
            if (offlineTask.getCpu() != null) {
                container.getAsJsonObject("resources").getAsJsonObject("limits").addProperty("cpu", String.valueOf(offlineTask.getCpu()));
                container.getAsJsonObject("resources").getAsJsonObject("requests").addProperty("cpu", String.valueOf(offlineTask.getCpu()));
            }
        } catch (Exception e) {

        }
        JsonArray args = new JsonArray();
        args.add("--url=" + offlineTask.getUrl());
        args.add("--workdir=" + offlineTask.getWorkDir());
        if (Objects.equals(offlineTask.getRole(), "leader")) {
            args.add(offlineTask.getRole());
        } else {
            args.add(offlineTask.getRole() + offlineTask.getExtParameters().get("role_id"));
        }
        offlineTask.getParameters().forEach((k, v) -> args.add("--" + k + "=" + v));
        container.add("args", args);
        JsonArray envs = container.getAsJsonArray("env");
        offlineTask.getExtParameters().put("NODE_ID", "0");
        offlineTask.getExtParameters().put("TASK_ID", offlineTask.getId());
        offlineTask.getExtParameters().put("CLUSTER_ID", offlineTask.getId() + "-" + String.valueOf(offlineTask.getSubId()) + "-" + offlineTask.getTaskIndex().toString());
        offlineTask.getExtParameters().put("LOCAL_DOMAIN", offlineTask.getTarget());
        offlineTask.getExtParameters().put("CPU",String.valueOf(offlineTask.getCpu()));
        offlineTask.getExtParameters().put("MEMORY", String.valueOf(offlineTask.getMemory()));

        offlineTask.getExtParameters().forEach((k, v) -> {
            for (JsonElement jsonElement : envs) {
                if (Objects.equals(jsonElement.getAsJsonObject().getAsJsonPrimitive("name").getAsString(), k)) {
                    jsonElement.getAsJsonObject().addProperty("value", v);
                }
            }
        });
    }

}
