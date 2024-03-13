package com.jd.mpc.service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import jakarta.annotation.Resource;

import com.alibaba.nacos.common.utils.MD5Utils;
import com.google.common.collect.Lists;
import com.jd.mpc.storage.OfflineTaskMapHolder;
import io.fabric8.kubernetes.api.model.*;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.security.MD5Encoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.jd.mpc.common.enums.TaskStatusEnum;
import com.jd.mpc.domain.offline.commons.SubTask;
import com.jd.mpc.domain.vo.ResourcesInfo;

import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.NodeMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.NodeMetricsList;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import lombok.extern.slf4j.Slf4j;

/**
 * k8s服务类
 *
 * 
 * @date 2021/9/24 12:32 下午
 */
@Slf4j
@Service
public class K8sService {

    /**
     * k8s客户端
     */
    private static KubernetesClient k8sClient;
    @Resource
    private PodFactory podFactory;
    @Resource
    private TaskPersistenceService taskPersistenceService;
    @Value("${k8s.namespace}")
    private String namespace;
    @Value("${k8s.name.prefix}")
    private String prefix;
    @Resource
    private OfflineTaskMapHolder offlineTaskMap;

    @PostConstruct
    public void init() throws IOException {
        /**
         * worker yaml输入流
         */
        InputStream inputStream = new FileInputStream("/home/config/k8sconfig.yaml");
        String adminConfData = IOUtils.toString(Objects.requireNonNull(inputStream));
        Config config = Config.fromKubeconfig(adminConfData);
        config.setNamespace(namespace);
        k8sClient = new KubernetesClientBuilder().withConfig(config).build();
    }

    /**
     * 包含name的deployment
     * @param name
     * @return
     */
    public List<Deployment> getDeploymentByName(String name){
        DeploymentList list = k8sClient.apps().deployments().inNamespace(namespace).list();
        return list.getItems().stream()
                .filter(deployment -> deployment.getMetadata().getName().contains(name))
                .collect(Collectors.toList());
    }

    /**
     * k8s提交离线任务
     *
     * @param subTask 子任务
     */
    @Transactional(rollbackFor = Exception.class)
    public void commit(SubTask subTask) {
        log.info("commit subTask：{}-{}", subTask.getId(), subTask.getSubId());
        // 1.启动pod
        podFactory.createPodList(subTask, k8sClient);
        // 2.任务状态持久化
        subTask.setStatus(TaskStatusEnum.RUNNING.getStatus());
        // modify by feiguodong1 for coor's HA
        offlineTaskMap.put(subTask);
        if (subTask.getSubId() == 0) {
            taskPersistenceService.updateParentTaskStatus(subTask.getId(),
                    TaskStatusEnum.RUNNING.getStatus());
        }
    }

    /**
     * 根据父任务id删除 deployment
     *
     * @param id 父任务id
     */
    public void deleteDeploymentById(String id) {
        List<Deployment> deploymentList = getDeploymentList(id);
        deleteDeploymentList(deploymentList);
    }

    /**
     * 根据任务id查找deployment
     *
     * @param id 任务id
     * @return deployment
     */
    public List<Deployment> getDeploymentList(String id) {
        DeploymentList list = k8sClient.apps().deployments().inNamespace(namespace).list();
        if (list == null) {
            return Lists.newArrayList();
        }
        return list.getItems().stream().filter(deployment -> {
            // 例：yili-callback-psi-psi-worker-63-1-1-0
            String name = k8sName2AppName(deployment.getMetadata().getName());
            String[] split = name.split("-");
            if (split.length < 4) {
                return false;
            }
            String[] idSplit = id.split("-");
            String[] idArr = new String[idSplit.length];
            if ((split.length-3-idSplit.length) < 0){
                return false;
            }
            System.arraycopy(split,split.length-3-idSplit.length,idArr,0,idSplit.length);
            return Objects.equals(split[0], prefix) && Objects.equals(id, String.join("-",idArr));
        }).collect(Collectors.toList());
    }

    /**
     * deploy name like %pattern%
     * @param pattern
     * @return
     */
    public List<Deployment> likeDeployList(String pattern){
        DeploymentList list = k8sClient.apps().deployments().inNamespace(namespace).list();
        if (list == null) {
            return Lists.newArrayList();
        }
        return list.getItems().stream().filter(deployment -> {
            String name = k8sName2AppName(deployment.getMetadata().getName());
            String[] split = name.split("-");
            if (split.length < 4) {
                return false;
            }
            return Objects.equals(split[0], prefix) && name.contains(pattern);
        }).collect(Collectors.toList());
    }

    /**
     * 模糊匹配删除pod
     * @param pattern
     */
    public void delDeployLike(String pattern){
        List<Deployment> deployments = this.likeDeployList(pattern);
        try {
            k8sClient.resourceList(deployments).delete();
        }
        catch (Exception e) {

        }
    }

    /**
     * deployName: k8s's name
     * appName: coor's name
     */
    public String k8sName2AppName(String deployName){
        return deployName.replace("--",";");
    }

    /**
     * deployName: k8s's name
     * appName: coor's name
     */
    public String appName2K8sName(String appName){
        return appName.replace(";","--");
    }

    /**
     * 根据任务id查找pod
     *
     * @param id 任务id
     * @return pod
     */
    public List<Pod> getPodList(String id) {
        PodList list = k8sClient.pods().inNamespace(namespace).list();
        if (list == null) {
            return Lists.newArrayList();
        }
        return list.getItems().stream().filter(pod -> {
            // 例：yili-callback-psi-psi-worker-63-1-1-0-aaa-bbb
            String name = k8sName2AppName(pod.getMetadata().getName());
            String[] split = name.split("-");
            if (split.length < 6) {
                return false;
            }
            return Objects.equals(split[0], prefix) && Objects.equals(id, split[split.length - 6]);
        }).collect(Collectors.toList());
    }

    /**
     * 根据任务id查找pod
     *
     * @param pattern 匹配模式
     * @return pod
     */
    public List<Pod> getPodListByName(String pattern) {
        PodList list = k8sClient.pods().inNamespace(namespace).list();
        if (list == null || StringUtils.isBlank(pattern)) {
            return Lists.newArrayList();
        }
        return list.getItems().stream().filter(pod -> {
            String name = k8sName2AppName(pod.getMetadata().getName());
            return name.startsWith(prefix) && (Arrays.asList(name.split("-")).contains(pattern));
        }).collect(Collectors.toList());
    }

    /**
     * 根据父任务id 子任务id删除 deployment
     *
     * @param id 父任务id
     * @param subId 子任务id
     */
    public void deleteDeploymentById(String id, Integer subId) {
        List<Deployment> deploymentList = getDeploymentList(id);
        try {
            k8sClient.resourceList(deploymentList).delete();
        }
        catch (Exception e) {

        }
        log.info("删除任务：{}", id);
    }

    /**
     * 根据父任务id 查找已占用资源量
     *
     * @param id 父任务id
     */
    public ResourcesInfo getUsedResources(String id) {
        List<Deployment> deploymentList = getDeploymentList(id);
        ResourcesInfo resourcesInfo = new ResourcesInfo();
        resourcesInfo.setId(id);
        resourcesInfo.setCpu(0);
        resourcesInfo.setMemory(0);
        deploymentList.forEach(deployment -> {
            Map<String, Quantity> limits = deployment.getSpec().getTemplate().getSpec()
                    .getContainers().get(0).getResources().getLimits();
            resourcesInfo.setCpu(
                    resourcesInfo.getCpu() + Integer.parseInt(limits.get("cpu").getAmount()));
            resourcesInfo.setMemory(
                    resourcesInfo.getMemory() + Integer.parseInt(limits.get("memory").getAmount()));
        });
        return resourcesInfo;
    }

    public void deleteDeploymentForCrd(String id, Integer subId, String nnMpcPodNameStr) {
        DeploymentList list = k8sClient.apps().deployments().inNamespace(namespace).list();
        List<Deployment> deploymentList = list.getItems().stream().filter(deployment -> {
            // 例：yili-callback-psi-psi-worker-63-1-1-0
            // 筛选出父id deployment
            String name = deployment.getMetadata().getName();

            return name.startsWith(prefix + nnMpcPodNameStr + id);
        }).collect(Collectors.toList());
        if (!deploymentList.isEmpty()) {
            deleteDeploymentList(deploymentList);
            log.info("删除任务：{}", id);
        }
    }

    private void deleteDeploymentList(List<Deployment> deploymentList) {
        try {
            k8sClient.resourceList(deploymentList).delete();
        }
        catch (Exception e) {
        }
    }
    /**
     * 删除 nn产生的service
     * 
     * @param id
     * @param subId
     * @param nnMpcPodNameStr
     */
    public void deleteServiceForCrd(String id, Integer subId, String nnMpcPodNameStr) {
        ServiceList list = k8sClient.services().inNamespace(namespace).list();
        List<io.fabric8.kubernetes.api.model.Service> serviceList = list.getItems().stream()
                .filter(service -> {
                    String name = service.getMetadata().getName();
                    return name.startsWith(prefix + nnMpcPodNameStr + id);
                }).collect(Collectors.toList());
        if (!serviceList.isEmpty()) {
            try {
                k8sClient.resourceList(serviceList).delete();
            }
            catch (Exception e) {
            }
            log.info("删除service：{}", id);
        }
    }

    public void closeJupyterPods(String instanceTag) {
        PodList list = k8sClient.pods().inNamespace(namespace).list();
        List<Pod> podList = list.getItems().stream().filter(pod -> {
            // 例：jupyter-a7a881822f0e76c6988ff3662574d8f7
            String name = pod.getMetadata().getName();
            try {
                return  name.startsWith("jupyter-"+ MD5Encoder.encode(instanceTag.getBytes()).toLowerCase());
            }catch(Exception e) {
                return false;
            }
        }).collect(Collectors.toList());
        if (!podList.isEmpty()) {
            try {
                k8sClient.resourceList(podList).delete();
            }
            catch (Exception e) {
            }
            log.info("删除交互式分析实例：{}", instanceTag);
        }
    }

}
