package com.jd.mpc.service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import jakarta.annotation.Resource;

import com.jd.mpc.service.task.ITaskService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.jd.mpc.common.enums.TaskTypeEnum;
import com.jd.mpc.common.response.CommonException;
import com.jd.mpc.common.util.CommonUtils;
import com.jd.mpc.common.util.GsonUtil;
import com.jd.mpc.common.util.ParameterParseUtil;
import com.jd.mpc.domain.offline.commons.OfflineTask;
import com.jd.mpc.domain.offline.commons.PreJob;
import com.jd.mpc.grpc.GrpcOfflineClient;

import lombok.extern.slf4j.Slf4j;

/**
 * 任务工厂
 *
 * 
 * @date 2021/11/8 8:14 下午
 */
@Component
@Slf4j
public class TaskFactory {
    @Resource
    private GrpcOfflineClient offlineClient;

    @Autowired
    private List<ITaskService> taskServices;

    /**
     * @param preJob 父任务
     * @return TaskMap
     */
    public Map<String, PreJob> createTaskMap(PreJob preJob) {
        Map<String, PreJob> map;
        TaskTypeEnum taskType = TaskTypeEnum.getByValue(preJob.getType());
        if (taskType == null){
            map = createTaskMapDefault(preJob);
        }else {
            switch (taskType) {
                case LR:
                    map = this.createTaskMapLr(preJob);
                    break;
                case HRZ_FL:
                    map = this.createTaskMapHrzFl(preJob);
                    break;
                case PREDICT:
                    map = this.createTaskMapPredict(preJob);
                    break;
                case TREE_XGB:
                case TREE_TRAIN_RF:
                    map = this.createTaskMapTree(preJob);
                    break;
                case XGBOOST:
                    map = this.createTaskMapXGBoost(preJob);
                    break;
                case PSI:
                    map = this.createTaskMapPsi(preJob);
                    break;
                case SHAPLEY_VALUE:
                    map = this.createTaskMapShapleyValue(preJob);
                    break;
                case FEATURE:
                case CUT_DATAFRAME:
                    map = this.createTaskMapFeature(preJob);
                    break;
                case FEATURE_FL:
                    map = this.createTaskMapFeatureFl(preJob);
                    break;
                case HRZ_FL_PREDICT:
                    map = this.createTaskMapHRZPREDICT(preJob);
                    break;
                case NN:
                    map = this.createTaskMapNN(preJob);
                    break;
                case NN_EVALUATE:
                    map = this.createTaskMapNNEvaluate(preJob);
                    break;
                case PLUMBER:
                    map = this.createTaskMapPlumber(preJob);
                    break;
                case LINEAR_EVALUATE:
                    map = this.createTaskMapLinearEvaluate(preJob);
                    break;
                case SCORE_CARD:
                    map = this.createTaskMapScoreCard(preJob);
                    break;
                case LOCAL_WORKER:
                case JTPSI:
                case VIF:
                    map = this.createTaskMapDefault(preJob);
                    break;
                default:
                {
                    ITaskService matchTaskService = null;
                    for (ITaskService taskService:taskServices){
                        if (taskService.match(preJob)){
                            matchTaskService = taskService;
                            break;
                        }
                    }
                    if (matchTaskService == null){
                        throw new CommonException("TaskService not found!");
                    }
                    map = matchTaskService.createTaskMap(preJob);
                }
                break;
            }
        }
        map.values().forEach(preJob1 -> preJob1.setEnv(preJob.getEnv()));
        return map;
    }


    private Map<String, PreJob> createTaskMapSpearmanMpc(PreJob preJob) {
        OfflineTask localTask = preJob.getTasks().get(0);
        OfflineTask remoteTask = preJob.getTasks().get(1);
        if (remoteTask == null || localTask == null) {
            throw new CommonException("localTask,remoteTask不能为空!");
        }
        localTask.getParameters().put("opposite-target", remoteTask.getTarget());
        localTask.getParameters().put("leader-target", localTask.getTarget());

        remoteTask.getParameters().put("opposite-target", localTask.getTarget());
        remoteTask.getParameters().put("leader-target", localTask.getTarget());
        Map<String, PreJob> resultMap = new HashMap<>();
        PreJob localJob = new PreJob();
        BeanUtils.copyProperties(preJob, localJob);
        localJob.setTasks(Collections.singletonList(localTask));
        PreJob remoteJob = new PreJob();
        BeanUtils.copyProperties(preJob, remoteJob);
        remoteJob.setTasks(Collections.singletonList(remoteTask));
        resultMap.put(localTask.getTarget(), localJob);
        resultMap.put(remoteTask.getTarget(), remoteJob);
        return resultMap;
    }

    private Map<String, PreJob> createTaskMapNNEvaluate(PreJob preJob) {
        Map<String, PreJob> map = new HashMap<>();
        preJob.getTasks().stream().collect(Collectors.groupingBy(OfflineTask::getTarget))
                .forEach((k, v) -> {
                    PreJob job = new PreJob();
                    job.setId(preJob.getId());
                    job.setType(preJob.getType());
                    job.setTasks(v);
                    map.put(k, job);
                });
        return map;
    }

    private Map<String, PreJob> createTaskMapLinearEvaluate(PreJob preJob) {
        Map<String, PreJob> map = new HashMap<>();
        // 构造target-list
        List<String> targets = preJob.getTasks().stream().map(x -> x.getTarget())
                .collect(Collectors.toList());
        preJob.getTasks().forEach((task) -> {
            if ("leader".equals(task.getRole())) {
                targets.remove(task.getTarget());
                targets.add(0, task.getTarget());
            }
        });
        String targetList = String.join(",", targets);
        // 构建follower-id
        AtomicReference<Integer> i = new AtomicReference<>(0);
        String leaderTarget = CommonUtils.getTarget(preJob.getTasks());
        targets.remove(leaderTarget);
        Map<String, List<OfflineTask>> listMap = preJob.getTasks().stream()
                .collect(Collectors.groupingBy(OfflineTask::getTarget));
        listMap.forEach((k, v) -> {
            PreJob job = new PreJob();
            job.setId(preJob.getId());
            job.setType(preJob.getType());
            job.setTasks(v);
            Map<String, String> extParam = new HashMap<>();
            if (k.equals(leaderTarget)) {
                extParam.put("role", "leader");
                extParam.put("follower-id", "0");
                extParam.put("target", String.join(",", targets));
            }
            else {
                Integer index = i.getAndSet(i.get() + 1);
                extParam.put("role", "follower");
                extParam.put("follower-id", String.valueOf(index));
                extParam.put("target", leaderTarget);
            }
            extParam.put("target-list", targetList);
            v.forEach(offlineTask -> offlineTask.setExtParameters(extParam));
            map.put(k, job);
        });
        return map;
    }

    private Map<String, PreJob> createTaskMapPlumber(PreJob preJob) {
        Map<String, PreJob> map = new HashMap<>();
        preJob.getTasks().stream().collect(Collectors.groupingBy(OfflineTask::getTarget))
                .forEach((k, v) -> {
                    PreJob job = new PreJob();
                    job.setId(preJob.getId());
                    job.setType(preJob.getType());
                    v.forEach(offlineTask -> {
                        offlineTask.setId(preJob.getId());
                        offlineTask.setSubId(0);
                    });
                    job.setTasks(v);
                    map.put(k, job);
                });
        return map;
    }

    private Map<String, PreJob> createTaskMapNN(PreJob preJob) {
        Map<String, PreJob> map = new HashMap<>();
        List<OfflineTask> taskList = preJob.getTasks().stream()
                // leader排在follower之前
                .sorted(Comparator.comparing(OfflineTask::getRole).reversed())
                .collect(Collectors.toList());
        List<String> targetList = taskList.stream().map(OfflineTask::getTarget).distinct()
                .collect(Collectors.toList());
        AtomicInteger role = new AtomicInteger(1);
        taskList.stream().collect(Collectors.groupingBy(OfflineTask::getTarget)).forEach((k, v) -> {
            PreJob job = new PreJob();
            job.setId(preJob.getId());
            job.setType(preJob.getType());
            v.forEach(offlineTask -> {
                Map<String, String> extParameters = new HashMap<>();
                Map<String, String> parameters = offlineTask.getParameters();
                extParameters.put("target", String.join(",", targetList));
                extParameters.put("mpc_target", String.join(";", targetList));
                extParameters.put("app_id", preJob.getId());
                extParameters.put("all_targets", String.join(",", targetList));
                extParameters.put("model_dir", parameters.get("model_dir"));
                extParameters.put("export_dir", parameters.get("export_dir"));
                extParameters.put("role", offlineTask.getRole());
                offlineTask.setExtParameters(extParameters);
            });
            job.setTasks(v);
            map.put(k, job);
        });
        targetList.forEach(e -> {
            OfflineTask task = map.get(e).getTasks().get(0);
            if (Objects.equals(task.getRole(), "leader")) {
                task.getExtParameters().put("role_id", "0");
            }
            else {
                task.getExtParameters().put("role_id", String.valueOf(role.getAndIncrement()));
            }
        });
        return map;
    }

    private Map<String, PreJob> createTaskMapHRZPREDICT(PreJob preJob) {
        Map<String, PreJob> map = new HashMap<>();
        preJob.getTasks().stream().collect(Collectors.groupingBy(OfflineTask::getTarget))
                .forEach((k, v) -> {
                    PreJob job = new PreJob();
                    job.setId(preJob.getId());
                    job.setType(preJob.getType());
                    job.setTasks(v);
                    map.put(k, job);
                });
        return map;
    }

    private Map<String, PreJob> createTaskMapPsi(PreJob preJob) {
        Map<String, PreJob> map = new HashMap<>();
        List<String> targetList = preJob.getTasks().stream().map(OfflineTask::getTarget).distinct()
                .collect(Collectors.toList());
        preJob.getTasks().stream().collect(Collectors.groupingBy(OfflineTask::getTarget))
                .forEach((k, v) -> {
                    PreJob job = new PreJob();
                    job.setId(preJob.getId());
                    job.setType(preJob.getType());
                    v.forEach(offlineTask -> {
                        Map<String, String> parameters = offlineTask.getParameters();
                        parameters.put("party-num", Integer.toString(targetList.size()));
                        parameters.put("target", String.join(",", targetList));
                    });
                    job.setTasks(v);
                    map.put(k, job);
                });
        return map;
    }

    private Map<String, PreJob> createTaskMapHrzFl(PreJob preJob) {
        Map<String, PreJob> map = new HashMap<>();
        Map<String, List<OfflineTask>> listMap = preJob.getTasks().stream()
                .collect(Collectors.groupingBy(OfflineTask::getTarget));
        String leaderTarget = CommonUtils.getTarget(preJob.getTasks());
        Set<String> set = new HashSet<>(listMap.keySet());
        set.remove(leaderTarget);
        String[] array = set.toArray(new String[] {});
        String targets = String.join(",", array);
        Map<String, String> partyMap = new HashMap<>();
        for (int i = 0; i < array.length; i++) {
            partyMap.put(array[i], String.valueOf(i + 1));
        }
        listMap.forEach((k, v) -> {
            PreJob job = new PreJob();
            job.setId(preJob.getId());
            job.setType(preJob.getType());
            job.setTasks(v);
            job.setPrefix(preJob.getPrefix());
            v.forEach(offlineTask -> {
                Map<String, String> extParam = new HashMap<>();
                Map<String, String> parameters = offlineTask.getParameters();
                if (parameters.get("model-type").equals("xgboost")) {
                    if (k.equals(leaderTarget)) {
                        extParam.put("party-id", "0");
                        // target的顺序需要跟party-id对应
                        extParam.put("target", targets);
                    }
                    else {
                        extParam.put("party-id", partyMap.get(k));
                        extParam.put("target", leaderTarget);
                    }
                    extParam.put("party-number", String.valueOf(preJob.getTasks().size()));
                }
                else {
                    if (k.equals(leaderTarget)) {
                        extParam.put("role", "leader");
                        extParam.put("target", "");
                    }
                    else {
                        extParam.put("role", "follower");
                        extParam.put("target", leaderTarget);
                    }
                    extParam.put("clientNum", String.valueOf(preJob.getTasks().size()));
                }
                offlineTask.setExtParameters(extParam);
            });
            map.put(k, job);
        });
        return map;
    }

    private Map<String, PreJob> createTaskMapLr(PreJob preJob) {
        Map<String, PreJob> map = new HashMap<>();
        AtomicReference<Integer> i = new AtomicReference<>(0);
        String leaderTarget = CommonUtils.getTarget(preJob.getTasks());
        Map<String, List<OfflineTask>> listMap = preJob.getTasks().stream()
                .collect(Collectors.groupingBy(OfflineTask::getTarget));
        Set<String> targets = new HashSet<>(listMap.keySet());
        targets.remove(leaderTarget);
        String mpcTargets = leaderTarget + "," + String.join(",", targets);
        List<String> ports = new ArrayList<>();
        for (int j = 0; j < targets.size(); j++) {
            ports.add(String.valueOf(50000 + j));
        }
        listMap.forEach((k, v) -> {
            PreJob job = new PreJob();
            job.setId(preJob.getId());
            job.setType(preJob.getType());
            job.setTasks(v);
            job.setPrefix(preJob.getPrefix());
            Map<String, String> extParam = new HashMap<>();
            if ("mpc".equals(v.get(0).getParameters().get("key-nbits"))) {
                if (k.equals(leaderTarget)) {
                    extParam.put("role-id", "leader");
                    extParam.put("role", "leader");
                }
                else {
                    Integer index = i.getAndSet(i.get() + 1);
                    extParam.put("role", "follower");
                    extParam.put("follower-id", String.valueOf(index));
                    extParam.put("role-id", "follower" + index);
                }
                extParam.put("target", mpcTargets);
                extParam.put("local-port", "50000,50001");
            }
            else {
                // 适用于非local
                if (k.equals(leaderTarget)) {
                    extParam.put("role-id", "leader");
                    extParam.put("role", "leader");
                    extParam.put("target", String.join(",", targets));
                    extParam.put("local-port", String.join(",", ports));
                }
                else {
                    Integer index = i.getAndSet(i.get() + 1);
                    extParam.put("role", "follower");
                    extParam.put("follower-id", String.valueOf(index));
                    extParam.put("role-id", "follower" + index);
                    extParam.put("target", leaderTarget);
                    extParam.put("local-port", "50000");
                }
            }
            v.forEach(offlineTask -> offlineTask.setExtParameters(extParam));
            map.put(k, job);
        });
        return map;
    }

    /**
     * shapley-value 任务
     *
     * @return
     */
    private Map<String, PreJob> createTaskMapShapleyValue(PreJob preJob) {
        Map<String, PreJob> map = new HashMap<>();
        AtomicReference<Integer> g = new AtomicReference<>(0);
        AtomicReference<Integer> i = new AtomicReference<>(0);
        String leaderTarget = CommonUtils.getTarget(preJob.getTasks());
        Map<String, List<OfflineTask>> listMap = preJob.getTasks().stream()
                .collect(Collectors.groupingBy(OfflineTask::getTarget));
        Set<String> targets = new HashSet<>(listMap.keySet());
        targets.remove(leaderTarget);
        Map<Integer, String> allTargetsMap = new LinkedHashMap<>();

        List<String> ports = new ArrayList<>();
        for (int j = 0; j < targets.size(); j++) {
            ports.add(String.valueOf(50000 + j));
        }
        Integer host_port = 6325;
        listMap.forEach((k, v) -> {
            PreJob job = new PreJob();
            job.setId(preJob.getId());
            job.setType(preJob.getType());
            job.setTasks(v);
            Map<String, String> extParam = new HashMap<>();
            Integer gindex = g.getAndSet(g.get() + 1);
            if (k.equals(leaderTarget)) {
                extParam.put("role", "leader");
                extParam.put("target", String.join(",", targets));
                extParam.put("local-port", String.join(",", ports));
                extParam.put("host", "0.0.0.0:" + host_port);
                allTargetsMap.put(-1, k);
            }
            else {
                Integer index = i.getAndSet(i.get() + 1);
                extParam.put("role", "follower");
                extParam.put("follower-id", String.valueOf(index));
                extParam.put("target", leaderTarget);
                extParam.put("local-port", "50000");
                extParam.put("host", "0.0.0.0:" + (host_port + gindex + 1));
                allTargetsMap.put(index, k);
            }
            v.forEach(offlineTask -> offlineTask.setExtParameters(extParam));
            map.put(k, job);
        });

        /**
         * target-list: 按顺序排序
         */
        Set<String> allTargets = new LinkedHashSet<>();
        allTargetsMap.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEachOrdered(e -> allTargets.add(e.getValue()));
        map.forEach((k, v) -> {
            preJob.getTasks().forEach(offlineTask -> {
                offlineTask.getExtParameters().put("target-list", String.join(",", allTargets));
            });
        });

        return map;
    }

    /**
     * Feature 特征工程任务
     *
     * @return
     */
    private Map<String, PreJob> createTaskMapFeature(PreJob preJob) {
        Map<String, PreJob> map = new HashMap<>();
        AtomicReference<Integer> i = new AtomicReference<>(0);
        String leaderTarget = CommonUtils.getTarget(preJob.getTasks());
        TaskTypeEnum taskType = TaskTypeEnum.getByValue(preJob.getType());
        if(taskType != TaskTypeEnum.CUT_DATAFRAME) {
            ParameterParseUtil.parse(preJob);
        }
        Map<String, List<OfflineTask>> listMap = preJob.getTasks().stream()
                .collect(Collectors.groupingBy(OfflineTask::getTarget));
        Set<String> targets = new HashSet<>(listMap.keySet());
        targets.remove(leaderTarget);

        listMap.forEach((k, v) -> {
            PreJob job = new PreJob();
            job.setId(preJob.getId());
            job.setType(preJob.getType());
            job.setTasks(v);
            Map<String, String> extParam = new HashMap<>();
            if (k.equals(leaderTarget)) {
                extParam.put("role", "leader");
                extParam.put("follower-id", "0");
                extParam.put("target", String.join(",", targets));
            }
            else {
                Integer index = i.getAndSet(i.get() + 1);
                extParam.put("role", "follower");
                extParam.put("follower-id", String.valueOf(index));
                extParam.put("target", leaderTarget);
            }
            v.forEach(offlineTask -> {
                offlineTask.setExtParameters(extParam);
            });
            map.put(k, job);
        });
        return map;
    }

    /**
     * FEATURE_FL 横向特征工程任务
     * 
     * @return
     */
    private Map<String, PreJob> createTaskMapFeatureFl(PreJob preJob) {
        Map<String, PreJob> map = new HashMap<>();
        AtomicReference<Integer> i = new AtomicReference<>(0);
        String serverTarget = CommonUtils.getTarget(preJob.getTasks());
        ParameterParseUtil.parse(preJob);
        Map<String, List<OfflineTask>> listMap = preJob.getTasks().stream()
                .collect(Collectors.groupingBy(OfflineTask::getTarget));
        Set<String> targets = new HashSet<>(listMap.keySet());
        targets.remove(serverTarget);

        // String app_id = UUID.randomUUID().toString().replaceAll("-", "");

        listMap.forEach((k, v) -> {
            PreJob job = new PreJob();
            job.setId(preJob.getId());
            job.setType(preJob.getType());
            job.setTasks(v);
            Map<String, String> extParam = new HashMap<>();

            if (k.equals(serverTarget)) {
                extParam.put("role", "server");
                extParam.put("target", String.join(",", targets));
                extParam.put("client-num", String.valueOf(targets.size() + 1));
            }
            else {
                extParam.put("role", "runner");
                extParam.put("target", serverTarget);
                // extParam.put("client-num", String.valueOf(targets.size() + 1));
            }
            v.forEach(offlineTask -> offlineTask.setExtParameters(extParam));
            map.put(k, job);
        });
        return map;
    }

    /**
     * XGBoost 任务
     *
     * @return
     */
    private Map<String, PreJob> createTaskMapXGBoost(PreJob preJob) {
        Map<String, PreJob> map = new HashMap<>();
        AtomicReference<Integer> i = new AtomicReference<>(0);
        String leaderTarget = CommonUtils.getTarget(preJob.getTasks());
        Map<String, List<OfflineTask>> listMap = preJob.getTasks().stream()
                .collect(Collectors.groupingBy(OfflineTask::getTarget));
        Set<String> targets = new HashSet<>(listMap.keySet());
        targets.remove(leaderTarget);

        List<String> ports = new ArrayList<>();
        for (int j = 0; j < targets.size(); j++) {
            ports.add(String.valueOf(50000 + j));
        }
        listMap.forEach((k, v) -> {
            PreJob job = new PreJob();
            job.setId(preJob.getId());
            job.setType(preJob.getType());
            job.setTasks(v);
            Map<String, String> extParam = new HashMap<>();
            if (k.equals(leaderTarget)) {
                extParam.put("role", "leader");
                extParam.put("follower-id", "0");
                extParam.put("local-port", String.join(",", ports));
                extParam.put("target", String.join(",", targets));

                extParam.put("checkpoint-path", "tree_model/output/leader_checkpoints");
                extParam.put("output-path", "tree_model/output/output/leader");
                extParam.put("export-path", "tree_model/output/export/leader");
            }
            else {
                Integer index = i.getAndSet(i.get() + 1);
                extParam.put("role", "follower");
                extParam.put("follower-id", String.valueOf(index));
                extParam.put("local-port", "50000");
                extParam.put("target", leaderTarget);

                extParam.put("checkpoint-path", "tree_model/output/follower_checkpoints");
                extParam.put("output-path", "tree_model/output/output/follower_0");
                extParam.put("export-path", "tree_model/output/export/follower_0");
            }
            v.forEach(offlineTask -> {
                if (offlineTask.getCpu() == null && offlineTask.getCpu() <= 0) {
                    offlineTask.setCpu(4);
                }
                if (offlineTask.getMemory() == null && offlineTask.getMemory() <= 0) {
                    offlineTask.setMemory(8);
                }
                offlineTask.setExtParameters(extParam);
            });
            map.put(k, job);
        });
        return map;
    }

    /**
     * 分布式树模型
     *
     * @return
     */
    private Map<String, PreJob> createTaskMapTree(PreJob preJob) {
        Map<String, PreJob> map = new HashMap<>();
        Map<String, List<OfflineTask>> listMap = preJob.getTasks().stream()
                .collect(Collectors.groupingBy(OfflineTask::getTarget));
        String leaderTarget = CommonUtils.getTarget(preJob.getTasks());
        Set<String> set = new HashSet<>(listMap.keySet());
        set.remove(leaderTarget);
        List<String> parterList = new ArrayList<>();
        for (int i = 0; i < set.size(); i++) {
            parterList.add(leaderTarget);
        }
        long startTime = System.currentTimeMillis();
        listMap.forEach((k, v) -> {
            PreJob job = new PreJob();
            job.setId(preJob.getId());
            job.setType(preJob.getType());
            job.setTasks(v);
            job.setPrefix(preJob.getPrefix());
            Map<String, String> extParam = new HashMap<>();
            if (k.equals(leaderTarget)) {
                extParam.put("role", "leader");
                extParam.put("parter-list", String.join(",", set));
            }
            else {
                extParam.put("role", "follower");
                extParam.put("parter-list", String.join(",", parterList));
            }
            extParam.put("start-time", String.valueOf(startTime));
            extParam.put("multi-size", String.valueOf(set.size()));
            v.forEach(offlineTask -> offlineTask.setExtParameters(extParam));
            map.put(k, job);
        });
        return map;
    }

    private Map<String, PreJob> createTaskMapDefault(PreJob preJob) {
        Map<String, PreJob> map = new HashMap<>();
        preJob.getTasks().stream().collect(Collectors.groupingBy(OfflineTask::getTarget))
                .forEach((k, v) -> {
                    PreJob job = new PreJob();
                    job.setId(preJob.getId());
                    job.setType(preJob.getType());
                    job.setPrefix(preJob.getPrefix());
                    job.setTasks(v);
                    map.put(k, job);
                });
        return map;
    }

    private Map<String, PreJob> createTaskMapPredict(PreJob preJob) {
        Map<String, PreJob> map = new HashMap<>();
        List<String> targets = preJob.getTasks().stream().map(OfflineTask::getTarget)
                .collect(Collectors.toList());
        preJob.getTasks().forEach((task) -> {
            if ("leader".equals(task.getRole())) {
                targets.remove(task.getTarget());
                targets.add(0, task.getTarget());
            }
        });
        preJob.getTasks().forEach((task) -> {
            PreJob job = new PreJob();
            job.setId(preJob.getId());
            job.setType(preJob.getType());
            job.setTasks(Lists.newArrayList(task));
            Map<String, String> extParam = new HashMap<>();
            extParam.put("domain", task.getTarget());
            extParam.put("target", String.join(",", targets));
            task.setExtParameters(extParam);
            map.put(task.getTarget(), job);
        });
        return map;
    }

    /**
     * 评分卡模型
     * 
     * @param preJob
     * @return
     */
    private Map<String, PreJob> createTaskMapScoreCard(PreJob preJob) {
        Map<String, PreJob> map = new HashMap<>();
        AtomicReference<Integer> g = new AtomicReference<>(0);
        AtomicReference<Integer> i = new AtomicReference<>(0);
        String leaderTarget = CommonUtils.getTarget(preJob.getTasks());
        Map<String, List<OfflineTask>> listMap = preJob.getTasks().stream()
                .collect(Collectors.groupingBy(OfflineTask::getTarget));
        Set<String> targets = new HashSet<>(listMap.keySet());
        targets.remove(leaderTarget);
        Map<Integer, String> allTargetsMap = new LinkedHashMap<>();

        List<String> ports = new ArrayList<>();
        for (int j = 0; j < targets.size(); j++) {
            ports.add(String.valueOf(50000 + j));
        }
        Integer host_port = 6325;
        listMap.forEach((k, v) -> {
            PreJob job = new PreJob();
            job.setId(preJob.getId());
            job.setType(preJob.getType());
            job.setTasks(v);
            Map<String, String> extParam = new HashMap<>();
            Integer gindex = g.getAndSet(g.get() + 1);
            if (k.equals(leaderTarget)) {
                extParam.put("role", "leader");
                extParam.put("target", String.join(",", targets));
                extParam.put("local-port", String.join(",", ports));
                extParam.put("host", "0.0.0.0:" + host_port);
                allTargetsMap.put(-1, k);
            }
            else {
                Integer index = i.getAndSet(i.get() + 1);
                extParam.put("role", "follower");
                extParam.put("follower-id", String.valueOf(index));
                extParam.put("target", leaderTarget);
                extParam.put("local-port", "50000");
                extParam.put("host", "0.0.0.0:" + (host_port + gindex + 1));
                allTargetsMap.put(index, k);
            }
            v.forEach(offlineTask -> offlineTask.setExtParameters(extParam));
            map.put(k, job);
        });

        /**
         * target-list: 按顺序排序
         */
        Set<String> allTargets = new LinkedHashSet<>();
        allTargetsMap.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEachOrdered(e -> allTargets.add(e.getValue()));
        map.forEach((k, v) -> {
            preJob.getTasks().forEach(offlineTask -> {
                offlineTask.getExtParameters().put("target-list", String.join(",", allTargets));
            });
        });

        return map;
    }

}
