package com.jd.mpc.service;

import java.io.StringReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.jd.mpc.common.config.MpcConfigService;
import jakarta.annotation.Resource;

import com.alibaba.nacos.api.annotation.NacosInjected;
import com.alibaba.nacos.api.config.ConfigService;
import com.google.common.collect.Maps;
import com.jd.mpc.common.enums.StoreTypeEnum;
import com.jd.mpc.service.task.ITaskService;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson.JSONArray;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.jd.mpc.common.constant.CommonConstant;
import com.jd.mpc.common.constant.DeploymentPathConstant;
import com.jd.mpc.common.enums.K8sResourceTypeEnum;
import com.jd.mpc.common.enums.TaskStatusEnum;
import com.jd.mpc.common.enums.TaskTypeEnum;
import com.jd.mpc.common.response.CommonException;
import com.jd.mpc.common.util.CommonUtils;
import com.jd.mpc.common.util.GsonUtil;
import com.jd.mpc.common.util.ParameterParseUtil;
import com.jd.mpc.domain.config.ResourceLimitPolicy;
import com.jd.mpc.domain.offline.commons.Job;
import com.jd.mpc.domain.offline.commons.OfflineTask;
import com.jd.mpc.domain.offline.commons.PreJob;
import com.jd.mpc.domain.offline.commons.SubTask;
import com.jd.mpc.redis.RedisService;

import cn.hutool.extra.spring.SpringUtil;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @date 2022/1/13 11:01 上午
 */
@Component
@Slf4j
public class ParaCompiler {

    @Resource
    private RedisService redisService;

    @Value("${spring.redis.host}")
    private String host;

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbName;

    @Value("${spring.datasource.password}")
    private String dbPwd;

    @Value("${spring.redis.port}")
    private int port;

    @Value("${spring.redis.password}")
    private String password;

    @Value("${grpc.proxy.host}")
    private String proxyHost;

    @Value("${grpc.proxy.port}")
    private String proxyPort;

    @Value("${grpc.proxy.local-port}")
    private String proxyLocalPort;

    @Value("${target}")
    private String localTarget;

    @Value("${mount.data.path:/mnt/data/}")
    private String mountDataPath="/mnt/data/";

    @Value("${k8s.namespace}")
    private String nameSpace;

    @Value("${portal.url}")
    String portalUrl;

    @Resource
    private FileService fileService;

    @Value("${node.ip}")
    private String nodeIp;

    @Value("${node.port}")
    private String nodePort;

    @Autowired
    private List<ITaskService> taskServices;
    @Resource
    private MpcConfigService mpcConfigService;

    public Job compileList(List<PreJob> preJobs) {
        Job job;
        // 默认取第一个处理id，类型
        final PreJob preJob = preJobs.get(0);
        // 此批任务全为同一个类型
        switch (TaskTypeEnum.getByValue(preJobs.get(0).getType())) {
            default:
                job = compileDefault(preJobs);
                break;
        }
        if (StringUtils.isNotBlank(preJob.getEnv()) && job != null) {
            job.getSubTaskList().forEach(subTask -> subTask.setEnv(preJob.getEnv()));
        }
        return job;

    }

    public Job compile(PreJob preJob) {
        Job job;
        TaskTypeEnum taskType = TaskTypeEnum.getByValue(preJob.getType());
        if (taskType == null){
            job = compileDefault(preJob);
        }else {
            switch (taskType) {
                case LR:
                    job = this.compileLr(preJob);
                    break;
                case SHAPLEY_VALUE:
                    job = this.compileShapleyValue(preJob);
                    break;
                case FEATURE:
                    job = this.compileFeature(preJob);
                    break;
                case FEATURE_FL:
                    job = this.compileFeatureFl(preJob);
                    break;
                case HRZ_FL:
                    job = this.compileHrzFl(preJob);
                    break;
                case PSI:
                    job = this.compilePsi(preJob);
                    break;
                case PREDICT:
                    job = this.compilePredict(preJob);
                    break;
                case TREE_XGB:
                case TREE_TRAIN_RF:
                    job = this.compileTree(preJob);
                    break;
                case XGBOOST:
                    job = this.compileXGBoost(preJob);
                    break;
                case HRZ_FL_PREDICT:
                    job = this.compileHrzFlPredict(preJob);
                    break;
                case NN_EVALUATE:
                    job = this.compileNNEvaluate(preJob);
                    break;
                case CUT_DATAFRAME:
                    job = this.compileNewCutDataframe(preJob);
                    break;
                case NN:
                    job = this.compileNN(preJob);
                    break;
                case VIF:
                    job = this.compileVif(preJob);
                    break;
                case STABILITY_INDEX:
                    job = this.compileStabilityIndex(preJob);
                    break;
                case LOCAL_SQL:
                    job = this.compileLocalSql(preJob);
                    break;
                case MPC:
                    job = this.compileMpc(preJob);
                    break;
                case LINEAR_EVALUATE:
                    job = this.compileLinearEvaluate(preJob);
                    break;
                case SCORE_CARD:
                    job = this.compileScoreCard(preJob);
                    break;
                case SPEARMANMPC:
                    job = this.compileSpearmanMpc(preJob);
                    break;
                case LOCAL_WORKER:
                    job = this.compileLocalWorker(preJob);
                    break;
                default:
                {
                    ITaskService matchTaskService = null;
                    for (ITaskService taskService : taskServices) {
                        if (taskService.match(preJob)){
                            matchTaskService = taskService;
                            break;
                        }
                    }
                    if (matchTaskService == null){
                        throw new CommonException("TaskService not found!");
                    }
                    job = matchTaskService.compile(preJob);
                }
                break;
            }
        }
        if (StringUtils.isNotEmpty(preJob.getEnv()) && job != null) {
            job.getSubTaskList().forEach(subTask -> subTask.setEnv(preJob.getEnv()));
        }
        return job;
    }

    /****
     *
     * @param customerId
     * @param projectId
     * @param bdpAccount
     * @return
     */
    private String getHDFSBasePath(String customerId, String projectId, String bdpAccount,String outputPrefix) {
        if(StringUtils.isNotBlank(outputPrefix) && !outputPrefix.endsWith("/")){
            outputPrefix = outputPrefix+"/";
        }
        return String.format("hdfs://ns22013/user/%s/%s%s/%s/%s/",bdpAccount,outputPrefix,customerId,projectId,"test/federal");
    }


    /**
     * @param customerId
     * @param projectId
     * @param bdpAccount
     * @return
     */
    private String getHDFSBasePath(String customerId, Integer projectId, String bdpAccount,String outputPrefix) {
        return getHDFSBasePath(customerId, String.valueOf(projectId), bdpAccount,outputPrefix);
    }

    /**
     * @param customerId
     * @param offlineTask
     * @return
     */
    private String getHDFSPath(String customerId, OfflineTask offlineTask, String suffixSeg,String outputPrefix) {
        String prefix = getHDFSBasePath(customerId, offlineTask.getProjectId(), offlineTask.getBdpAccount(),outputPrefix);
        return String.format("%s/%s/%s/%s", prefix, offlineTask.getTaskType(), offlineTask.getId(), suffixSeg);
    }

    /**
     *
     * @param customerId
     * @param offlineTask
     * @param suffixSeg
     * @return
     */
    private String getOutputPath(String customerId, OfflineTask offlineTask, String suffixSeg,String outputPrefix) {
        if(StoreTypeEnum.HDFS.equals(offlineTask.getStoreType())) {
            return getHDFSPath(customerId, offlineTask, suffixSeg,outputPrefix);
        }
        else {
            return CommonUtils.genPath(offlineTask, suffixSeg);
        }
    }

    private Job compileSpearmanMpc(PreJob preJob) {
        // 初始化
        log.info(GsonUtil.createGsonString(preJob));
        String workDir = "/app/feature_engineering";
        OfflineTask oriTask = this.initOriTask(preJob);
        Map<String, String> parameters = oriTask.getParameters();
        List<OfflineTask> tasks = new ArrayList<>();
        Map<String, String> paramMap = new HashMap<>(parameters);
        oriTask.setTaskIndex(0);
        oriTask.setName(CommonUtils.genPodName(oriTask, null));
        // 构建任务
        OfflineTask spearmanTask = this.initOriTask(preJob);
        String base64Str = null;
        try {
            if ("leader".equals(oriTask.getRole())) {

                paramMap.put("beaver_tee_server", "pk-mpc-worker:20002");
                paramMap.put("target", "JXZ_T");
            }
            else {
                paramMap.put("target", "JXZ_R");
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        String clusterId = spearmanTask.getId() + "-" + spearmanTask.getSubId() + "-"
                + spearmanTask.getTaskIndex();
        paramMap.put("cluster-id", clusterId);
        paramMap.put("log-level", "DEBUG");
        paramMap.put("proxy-listen", "0.0.0.0:20001");
        paramMap.put("self-domain", localTarget);
        paramMap.put("triple_provider", "tee");
        paramMap.put("beaver_tee_domain", "JXZ_R");
        paramMap.put("beaver_tee_id", "tee-beaver-jd1");
        paramMap.put("status_server", nodeIp + ":" + nodePort);
        spearmanTask.setWorkDir(workDir);
        spearmanTask.setParameters(paramMap);
        spearmanTask.setDeploymentPath(DeploymentPathConstant.SPEARMANMPC);
        tasks.add(spearmanTask);
        // 填充subTask
        SubTask subTask = SubTask.builder().id(preJob.getId()).subId(0)
                .status(TaskStatusEnum.NEW.getStatus()).tasks(tasks).build();
        // 填充job
        return Job.builder().id(preJob.getId()).type(preJob.getType())
                .subTaskList(Collections.singletonList(subTask)).build();
    }



    private Job compileNNEvaluate(PreJob preJob) {
        OfflineTask oriTask = this.initOriTask(preJob);
        Map<String, String> parameters = oriTask.getParameters();
        List<OfflineTask> tasks = new ArrayList<>();
        int index = 0;
        // 构建server
        OfflineTask workerTask = new OfflineTask();
        BeanUtils.copyProperties(oriTask, workerTask);
        workerTask.setTaskIndex(index++);
        workerTask.setName(CommonUtils.genPodName(workerTask, null));
        workerTask.setUrl("");
        workerTask.setWorkDir("/app/fl_evaluator");
        Map<String, String> paramMap = new HashMap<>(parameters);
        String outputPrefix = parameters.containsKey("outputPrefix") ? parameters.get("outputPrefix") : "";
        String path = getOutputPath(nameSpace, workerTask, "output-dir",outputPrefix);
        List<String> madeDirs = fileService.mkListDirs(Collections.singletonList(path), workerTask.getBdpAccount(), workerTask.getStoreType());
        path = madeDirs.get(0);
        paramMap.put("output-dir", path);
        workerTask.setParameters(paramMap);
        workerTask.setDeploymentPath(DeploymentPathConstant.NN_EVALUATE);
        tasks.add(workerTask);
        // 填充subTask
        SubTask subTask = SubTask.builder().id(preJob.getId()).subId(0)
                .status(TaskStatusEnum.NEW.getStatus()).tasks(tasks).build();
        // 填充job
        return Job.builder().id(preJob.getId()).type(preJob.getType())
                .subTaskList(Collections.singletonList(subTask)).build();
    }

    /**
     * local_sql
     */
    private Job compileLocalSql(PreJob preJob) {
        // 默认使用第一个解析,填充公共值
        OfflineTask oriTask = this.initOriTask(preJob);
        Map<String, String> parameters = oriTask.getParameters();
        List<OfflineTask> tasks = new ArrayList<>();
        int index = 0;
        // 构建server
        OfflineTask workerTask = new OfflineTask();
        BeanUtils.copyProperties(oriTask, workerTask);
        workerTask.setTaskIndex(index++);
        workerTask.setName(CommonUtils.genPodName(workerTask, null));
        workerTask.setUrl("");
        workerTask.setWorkDir("");
        Map<String, String> paramMap = new HashMap<>(parameters);
        if (workerTask.getCpu() == null || workerTask.getCpu().intValue() == 0) {
            workerTask.setCpu(4);
            workerTask.setMemory(8);
        }
        paramMap.put("target", oriTask.getTarget());
        // 处理 input-table
        try {
            final String inputTable = paramMap.get("input-table");
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
            JsonNode df = mapper.readValue(inputTable, JsonNode.class);
            paramMap.put("input-table", df.toString());
        }
        catch (Exception e) {
            log.error("deal with input-table error", e);
        }
        final String output = paramMap.get("output");
        // 如果是目录，mkdir
        if (!StringUtils.contains(output, ".")) {
            fileService.mkdir(Collections.singletonList(output));
        }

        workerTask.setParameters(paramMap);
        workerTask.setDeploymentPath(DeploymentPathConstant.JXZ_LOCAL);

        tasks.add(workerTask);
        // 填充subTask
        SubTask subTask = SubTask.builder().id(preJob.getId()).subId(0)
                .status(TaskStatusEnum.NEW.getStatus()).tasks(tasks).build();
        // 填充job
        return Job.builder().id(preJob.getId()).type(preJob.getType())
                .subTaskList(Collections.singletonList(subTask)).build();
    }

    /**
     * mpc
     */
    private Job compileMpc(PreJob preJob) {
        // 默认使用第一个解析,填充公共值
        OfflineTask oriTask = this.initOriTask(preJob);
        Map<String, String> parameters = oriTask.getParameters();
        List<OfflineTask> tasks = new ArrayList<>();
        int index = 0;
        // 构建server
        OfflineTask workerTask = new OfflineTask();
        BeanUtils.copyProperties(oriTask, workerTask);
        workerTask.setTaskIndex(index++);
        workerTask.setName(CommonUtils.genPodName(workerTask, null));
        workerTask.setUrl("");
        workerTask.setWorkDir("");
        Map<String, String> paramMap = new HashMap<>(parameters);
        if (workerTask.getCpu() == null || workerTask.getCpu().intValue() == 0) {
            workerTask.setCpu(8);
            workerTask.setMemory(16);
        }

        // 处理outputDir
        String groupOutput = paramMap.get("group-output");
        String aggOutput = paramMap.get("agg-output");
        String outputPath = "/home/jovyan/work";
        if (groupOutput.contains(localTarget)) {
            final String group = groupOutput.substring(groupOutput.lastIndexOf("/"));
            groupOutput = outputPath + group;
            paramMap.put("group-output", groupOutput);
        }
        if (aggOutput.contains(localTarget)) {
            final String agg = aggOutput.substring(aggOutput.lastIndexOf("/"));
            aggOutput = outputPath + agg;
            paramMap.put("agg-output", aggOutput);
        }

        workerTask.setParameters(paramMap);
        workerTask.setDeploymentPath(DeploymentPathConstant.JXZ_MPC);

        tasks.add(workerTask);
        // 填充subTask
        SubTask subTask = SubTask.builder().id(preJob.getId()).subId(0)
                .status(TaskStatusEnum.NEW.getStatus()).tasks(tasks).build();
        // 填充job
        return Job.builder().id(preJob.getId()).type(preJob.getType())
                .subTaskList(Collections.singletonList(subTask)).build();
    }

    /**
     * 线性回归评估
     */
    private Job compileLinearEvaluate(PreJob preJob) {
        // 默认使用第一个解析,填充公共值
        OfflineTask oriTask = this.initOriTask(preJob);
        Map<String, String> parameters = oriTask.getParameters();
        final Map<String, String> extParameters = oriTask.getExtParameters();
        List<OfflineTask> tasks = new ArrayList<>();
        int index = 0;
        // 构建server
        OfflineTask workerTask = new OfflineTask();
        BeanUtils.copyProperties(oriTask, workerTask);
        workerTask.setTaskIndex(index++);
        workerTask.setName(CommonUtils.genPodName(workerTask, null));
        workerTask.setUrl("");
        workerTask.setWorkDir("/app/fl_evaluator");
        Map<String, String> paramMap = new HashMap<>(parameters);
        if (workerTask.getCpu() == null || workerTask.getCpu().intValue() == 0) {
            workerTask.setCpu(8);
            workerTask.setMemory(16);
        }
        final String target = extParameters.get("target");
        final List<String> targets = Arrays.asList(target.split(","));
        List<String> localPort = new ArrayList<>();
        int port = 50053;
        for (int i = 0; i < targets.size(); i++) {
            localPort.add(String.valueOf(port + i));
        }
        paramMap.put("local-port", String.join(",", localPort));
        paramMap.put("follower-id", extParameters.get("follower-id"));
        paramMap.put("target-list", extParameters.get("target-list"));
        paramMap.put("target", target);
        paramMap.put("role", extParameters.get("role"));
        String outputPrefix = parameters.containsKey("outputPrefix") ? parameters.get("outputPrefix") : "";
        String outputPath = getOutputPath(nameSpace, oriTask, "output-path",outputPrefix);
        List<String> madeDirs = fileService.mkListDirs(Collections.singletonList(outputPath), oriTask.getBdpAccount(), oriTask.getStoreType());
        outputPath = madeDirs.get(0);
        paramMap.put("output-dir", outputPath);

        workerTask.setParameters(paramMap);
        workerTask.setDeploymentPath(DeploymentPathConstant.LINEAR_EVALUATE);

        tasks.add(workerTask);
        // 填充subTask
        SubTask subTask = SubTask.builder().id(preJob.getId()).subId(0)
                .status(TaskStatusEnum.NEW.getStatus()).tasks(tasks).build();
        // 填充job
        return Job.builder().id(preJob.getId()).type(preJob.getType())
                .subTaskList(Collections.singletonList(subTask)).build();
    }

    private Job compileNN(PreJob preJob) {
        SubTask cutTask = null;
        int subId = 0;
        Map<String, String> taskParams = preJob.getTasks().get(0).getParameters();
        // 当train_data_source_name未指明 且 raw-data-dir-nn 不存在时，才进行数据切分
        if (StringUtils.isEmpty(taskParams.get("train_data_source_name"))
                && !taskParams.containsKey("raw-data-dir-nn")) {
            cutTask = contructCutDataSubTask(preJob);
            cutTask.setSubId(0);
            subId++;
        }

        String workDir = "/app/fl_nn_demo";
        OfflineTask dcTask = this.initOriTask(preJob);
        String workerUrl = taskParams.containsKey("workerUrl")
                ? taskParams.get("workerUrl")
                : "http://storage.jd.local/file-store/fl_nn_mpc_3pc.tar.gz";
        dcTask.setUrl(workerUrl);
        dcTask.setWorkDir("/app/vert_nn");
        Map<String, String> extParameters = dcTask.getExtParameters();
        extParameters.put("proxy_addr", proxyHost + ":" + proxyPort);
        extParameters.put("REDIS_HOST", host);
        extParameters.put("REDIS_PORT", Integer.toString(port));
        extParameters.put("REDIS_PASSWORD", password);
        extParameters.put("AUTH_SERVER", nodeIp+":"+nodePort);
        dcTask.setName(CommonUtils.genPodName(dcTask, "dc"));
        dcTask.setDeploymentPath(DeploymentPathConstant.NN_DC);

        OfflineTask trainerTask = new OfflineTask();
        BeanUtils.copyProperties(dcTask, trainerTask);
        String outputPrefix = taskParams.containsKey("outputPrefix") ? taskParams.get("outputPrefix") : "";
        String modelDir = getOutputPath(nameSpace, trainerTask, "model_dir",outputPrefix);
        String exportDir = getOutputPath(nameSpace, trainerTask, "export_dir",outputPrefix);
        List<String> madeDirs = fileService.mkListDirs(Arrays.asList(modelDir, exportDir), trainerTask.getBdpAccount(), trainerTask.getStoreType());
        modelDir = madeDirs.get(0);
        exportDir = madeDirs.get(1);
        trainerTask.getExtParameters().put("model_dir", modelDir);
        trainerTask.getExtParameters().put("export_dir", exportDir);
        trainerTask.getExtParameters().put("beaver_tee_server", trainerTask.getParameters().getOrDefault("beaver_tee_server", "mpc-worker-v3:20002"));
        trainerTask.getExtParameters().put("beaver_tee_id",trainerTask.getParameters().getOrDefault("beaver_tee_id","tee-beaver-jd1-v3"));
        List<OfflineTask> tasks = new ArrayList<>();
        dcTask.setSubId(subId);

        if (trainerTask.getCpu() == null || trainerTask.getCpu().intValue() == 0) {
            trainerTask.setCpu(4);
            trainerTask.setMemory(16);
        }
        trainerTask.setTaskIndex(1);
        if (cutTask != null) {
            dcTask.getParameters().put("train_data_source_name", preJob.getId());
            trainerTask.getParameters().put("train_data_start", "0");
            trainerTask.getParameters().put("train_data_end", "99999999999");
            dcTask.getParameters().put("train_data_start", "0");
            dcTask.getParameters().put("train_data_end", "99999999999");
        }
        else {
            dcTask.getParameters().put("train_data_source_name",
                    dcTask.getParameters().get("train_data_source_name"));
            trainerTask.getParameters().put("train_data_source_name",
                    dcTask.getParameters().get("train_data_source_name"));
            dcTask.getParameters().put("train_data_start",
                    dcTask.getParameters().get("train_data_start"));
            dcTask.getParameters().put("train_data_end",
                    dcTask.getParameters().get("train_data_end"));
            trainerTask.getParameters().put("train_data_start",
                    dcTask.getParameters().get("train_data_start"));
            trainerTask.getParameters().put("train_data_end",
                    dcTask.getParameters().get("train_data_end"));
        }

        if ("mpc-2pc".equals(trainerTask.getParameters().get("encrypt_type"))
                || "lr-mpc-2pc".equals(trainerTask.getParameters().get("encrypt_type"))) {
            // 构建mpc服务
            // OfflineTask mpcWorkerTask = new OfflineTask();
            // BeanUtils.copyProperties(dcTask, mpcWorkerTask);
            // mpcWorkerTask.setSubId(1);
            // String mpcWorkerName = SpringUtil.getProperty("k8s.name.prefix")
            // + CommonConstant.NN_MPC_POD_NAME_STR + preJob.getId();
            // mpcWorkerTask.setParameters(new HashMap<>());
            // mpcWorkerTask.setTaskType(TaskTypeEnum.NN_MPC.getName());
            // mpcWorkerTask.setName(mpcWorkerName);
            // mpcWorkerTask.setDeploymentPath(DeploymentPathConstant.NN_MPC);
            // mpcWorkerTask.getParameters().put("interaction_remote",
            // mpcWorkerTask.getProxy_remote());
            // mpcWorkerTask.setTaskIndex(0);
            // String leaderTarget = extParameters.get("target").split(",")[0];
            // if (dcTask.getRole().equals("leader")) {
            // mpcWorkerTask.getParameters().put("beaver_tee_server", "pk-mpc-worker:20002");
            // mpcWorkerTask.getParameters().put("interaction_domain", localTarget);
            // mpcWorkerTask.getParameters().put("beaver_tee_domain", localTarget);
            // }
            // else {
            // mpcWorkerTask.getParameters().put("interaction_domain", localTarget);
            // mpcWorkerTask.getParameters().put("beaver_tee_domain", leaderTarget);
            // mpcWorkerTask.getParameters().put("beaver_tee_id", "tee-beaver-jd1");
            //
            // }
            // mpcWorkerTask.setCpu(4);
            // mpcWorkerTask.setMemory(8);
            // mpcWorkerTask.getParameters().put("redis_server", mpcWorkerTask.getRedis_server());
            // mpcWorkerTask.getParameters().put("redis_password",
            // mpcWorkerTask.getRedis_password());
            // mpcWorkerTask.setServiceName(mpcWorkerName + "-0");
            // mpcWorkerTask.setServicePath(ServicePathConstant.MPC_NN_SERVICE);
            // trainerTask.getParameters().put("mpc_addr",
            // mpcWorkerName + "-0" + ":20000;" + mpcWorkerName + "-0" + ":20000");
            // trainerTask.getParameters().put("mpc_target",
            // trainerTask.getExtParameters().get("mpc_target"));
            // trainerTask.getExtParameters().put("mpc_addr",
            // mpcWorkerName + "-0" + ":20000;" + mpcWorkerName + "-0" + ":20000");
            // // trainerTask.getParameters().put("mpc_addr",
            // // "pk-mpc-nn-worker:20000;pk-mpc-nn-worker:20000");
            // // trainerTask.getParameters().put("mpc_target",
            // // trainerTask.getExtParameters().get("mpc_target"));
            // // trainerTask.getExtParameters().put("mpc_addr",
            // // "pk-mpc-nn-worker:20000;pk-mpc-nn-worker:20000");
            // tasks.add(mpcWorkerTask);
            // dcTask.setTaskIndex(1);
            // trainerTask.setTaskIndex(2);
        }
        if (trainerTask.getParameters().containsKey("raw-data-dir-nn")) {
            trainerTask.getExtParameters().put("raw_data_dir",
                    trainerTask.getParameters().get("raw-data-dir-nn"));
        }
        tasks.add(dcTask);
        tasks.add(trainerTask);
        trainerTask.getParameters().put("worker-nums",
                trainerTask.getParameters().containsKey("worker-nums")
                        ? trainerTask.getParameters().get("worker-nums")
                        : "2");
        trainerTask.getParameters().put("ps-nums",
                trainerTask.getParameters().containsKey("ps-nums")
                        ? trainerTask.getParameters().get("ps-nums")
                        : "1");
        trainerTask.setDeploymentPath(DeploymentPathConstant.NN_TRAINER);
        trainerTask.setResourcesType(K8sResourceTypeEnum.CRD.getName());

        trainerTask.setSubId(subId);
        trainerTask.setCrdName("tfjobs.kubeflow.org");
        trainerTask.setName(CommonUtils.genPodName(trainerTask, "trainer"));

        SubTask subTask = SubTask.builder().id(trainerTask.getId()).subId(subId)
                .status(TaskStatusEnum.NEW.getStatus()).tasks(tasks).build();
        subTask.setSubId(subId);
        List<SubTask> list = new ArrayList<>();
        if (cutTask != null) {
            list.add(cutTask);
        }
        list.add(subTask);
        return Job.builder().id(preJob.getId()).type(TaskTypeEnum.NN.getName()).subTaskList(list)
                .build();
    }

    private Job compileNewCutDataframe(PreJob preJob) {
        SubTask subTask = contructCutDataSubTask(preJob);
        // 填充job
        return Job.builder().id(preJob.getId()).type(preJob.getType())
                .subTaskList(Collections.singletonList(subTask)).build();
    }

    public SubTask contructCutDataSubTask(PreJob preJob) {
        // 默认使用第一个解析,填充公共值
        OfflineTask oriTask = this.initOriTask(preJob);
        Map<String, String> parameters = oriTask.getParameters();
        String outputPrefix = parameters.containsKey("outputPrefix") ? parameters.get("outputPrefix") : "";
        String resultPath = getOutputPath(nameSpace, oriTask, "resultpath",outputPrefix);
        List<String> madeDirs = fileService.mkListDirs(Collections.singletonList(resultPath), oriTask.getBdpAccount(), oriTask.getStoreType());
        resultPath = madeDirs.get(0);
        parameters.put("role", oriTask.getRole());
        Map<String, Object> jobMap = new HashMap<>();
        Map<String, Object> cutDataframeMap = new HashMap<>();
        Map<String, Object> headMap = new HashMap<>();

        cutDataframeMap.put("result_path", resultPath);
        cutDataframeMap.put("data_source_name", preJob.getId());
        cutDataframeMap.put("url", dbUrl);
        cutDataframeMap.put("block_size", parameters.get("block_size"));
        cutDataframeMap.put("db_url", "mysql+pymysql://" + dbName + ":" + dbPwd + "@"
                + dbUrl.substring(dbUrl.indexOf("//") + 2));
        List<String> ignoreFileds = new ArrayList<>();
        ignoreFileds.add(parameters.get("example_id"));
        if (parameters.containsKey("ignore_fileds")) {
            ignoreFileds.addAll(Arrays.asList(parameters.get("ignore_fileds").split(",")));
        }
        cutDataframeMap.put("ignore_fileds", ignoreFileds);
        cutDataframeMap.put("is_feature_concate", parameters.get("is_feature_concate"));
        cutDataframeMap.put("table", parameters.get("table"));

        jobMap.put("CutDataframe", cutDataframeMap);
        headMap.put("example_id", parameters.get("example_id"));
        headMap.put("label", parameters.get("label"));
        parameters.put("heads", GsonUtil.createGsonString(headMap));
        String workerUrl = "";
        String workDir = "/app/feature_engineering";
        List<OfflineTask> tasks = new ArrayList<>();
        // 构建worker
        Map<String, String> paramMap = new HashMap<>(parameters);
        if (!paramMap.containsKey("jobs")) {
            paramMap.put("jobs", GsonUtil.createGsonString(jobMap));
        }
        OfflineTask workerTask = new OfflineTask();
        BeanUtils.copyProperties(oriTask, workerTask);
        workerTask.setParameters(paramMap);
        workerTask.setTaskIndex(0);
        workerTask.setDeploymentPath(DeploymentPathConstant.CUT_DATAFRAME_BASE);
        workerTask.setName(CommonUtils.genPodName(workerTask, "cut"));
        workerTask.setUrl(workerUrl);
        workerTask.setWorkDir(workDir);
        workerTask.setCpu(16);
        workerTask.setMemory(32);
        tasks.add(workerTask);
        // 填充subTask
        return SubTask.builder().id(preJob.getId()).subId(0)
                .status(TaskStatusEnum.NEW.getStatus()).tasks(tasks).build();
    }

    private Job compileHrzFlPredict(PreJob preJob) {
        // 默认使用第一个解析,填充公共值
        OfflineTask oriTask = this.initOriTask(preJob);
        Map<String, String> parameters = oriTask.getParameters();
        Map<String, String> extParameters = oriTask.getExtParameters();
        List<OfflineTask> tasks;
        String outputPrefix = parameters.containsKey("outputPrefix") ? parameters.get("outputPrefix") : "";
        String resultPath = getOutputPath(nameSpace, oriTask, "resultpath",outputPrefix);
        List<String> madeDirs = fileService.mkListDirs(Collections.singletonList(resultPath), oriTask.getBdpAccount(), oriTask.getStoreType());
        resultPath = madeDirs.get(0);
        parameters.put("result-path", resultPath);
        if (oriTask.getCpu() == null || oriTask.getCpu().intValue() == 0) {
            oriTask.setCpu(8);
            oriTask.setMemory(16);
        }
        if (parameters.get("model-type").equals("xgboost")) {
            tasks = this.assembleHrzFlPredictXgb(oriTask, parameters, extParameters,
                    preJob.getPrefix());
        }
        else {
            tasks = this.assembleHrzFlPredictNnLr(oriTask, parameters, extParameters,
                    preJob.getPrefix());
        }
        // 填充subTask
        SubTask subTask = SubTask.builder().id(preJob.getId()).subId(0)
                .status(TaskStatusEnum.NEW.getStatus()).tasks(tasks).build();
        // 填充job
        return Job.builder().id(preJob.getId()).type(preJob.getType())
                .subTaskList(Collections.singletonList(subTask)).build();
    }

    private List<OfflineTask> assembleHrzFlPredictNnLr(OfflineTask oriTask,
            Map<String, String> parameters, Map<String, String> extParameters, String prefix) {
        String workerUrl = "";
        String workDir = "/app/horizontal_federated";
        List<OfflineTask> tasks = new ArrayList<>();
        // 构建worker
        Map<String, String> paramMap = new HashMap<>(parameters);
        OfflineTask workerTask = new OfflineTask();
        BeanUtils.copyProperties(oriTask, workerTask);
        workerTask.setParameters(paramMap);
        workerTask.setTaskIndex(0);
        workerTask.setDeploymentPath(DeploymentPathConstant.HRZ_FL_PREDICT_BASE);
        workerTask.setName(CommonUtils.genPodName(workerTask, "wk"));
        workerTask.setUrl(workerUrl);
        workerTask.setWorkDir(workDir);
        tasks.add(workerTask);
        return tasks;
    }

    private List<OfflineTask> assembleHrzFlPredictXgb(OfflineTask oriTask,
            Map<String, String> parameters, Map<String, String> extParameters, String prefix) {
        String workerUrl = "";
        String workDir = "/app/horizontal_federated";
        List<OfflineTask> tasks = new ArrayList<>();
        // 构建worker
        Map<String, String> paramMap = new HashMap<>(parameters);
        OfflineTask workerTask = new OfflineTask();
        BeanUtils.copyProperties(oriTask, workerTask);
        workerTask.setParameters(paramMap);
        workerTask.setTaskIndex(0);
        workerTask.setDeploymentPath(DeploymentPathConstant.HRZ_FL_PREDICT_BASE);
        workerTask.setName(CommonUtils.genPodName(workerTask, "wk"));
        workerTask.setUrl(workerUrl);
        workerTask.setWorkDir(workDir);
        tasks.add(workerTask);
        return tasks;
    }

    private Job compilePsi(PreJob preJob) {
        OfflineTask offlineTask = this.initOriTask(preJob);
        Map<String, String> parameters = offlineTask.getParameters();
        offlineTask.setTaskType(TaskTypeEnum.PSI.getName());
        offlineTask.setName(CommonUtils.genPodName(offlineTask, null));
        offlineTask.setDeploymentPath(DeploymentPathConstant.PSI);
        parameters.put("status-server", nodeIp + ":" + nodePort);
        String clusterId = offlineTask.getId() + "-" + offlineTask.getSubId() + "-"
                + offlineTask.getTaskIndex();
        parameters.put("cluster-id", clusterId);

        parameters.put("party-id", localTarget);

        final String output = parameters.get("output");
        String format = parameters.get("format");
        String outputPrefix = parameters.containsKey("outputPrefix") ? parameters.get("outputPrefix") : "";
        String outputSuffix = parameters.containsKey("outputSuffix") ? parameters.get("outputSuffix") : "";
        if (StringUtils.isBlank(output)) {
            String input = parameters.get("input");
            String prefix = "/mnt/data/";
            if (StoreTypeEnum.HDFS.equals(offlineTask.getStoreType())){
                prefix = getHDFSBasePath(nameSpace, offlineTask.getProjectId(), offlineTask.getBdpAccount(),outputPrefix);
            }
            // 输入是目录，填充目录
            if (input.endsWith("/") || input.substring(input.lastIndexOf("/")).indexOf(".") == -1) {
                prefix = prefix + preJob.getType() + "/" + preJob.getId();
                if(StringUtils.isNotBlank(outputSuffix)){
                    prefix = prefix+"/"+outputSuffix;
                }
                List<String> madeDirs = fileService.mkListDirs(Collections.singletonList(prefix), offlineTask.getBdpAccount(),
                        offlineTask.getStoreType());
                prefix = madeDirs.get(0);
                parameters.put("output", prefix);
            }
            // 输入是文件，填充文件
            else {
                // 提前生成目录
                prefix = prefix + preJob.getType();
                if(StringUtils.isNotBlank(outputSuffix)){
                    prefix = prefix+"/"+outputSuffix;
                }
                List<String> madeDirs = fileService.mkListDirs(Collections.singletonList(prefix), offlineTask.getBdpAccount(),
                        offlineTask.getStoreType());
                prefix = madeDirs.get(0);
                // 填充文件信息
                parameters.put("output", prefix + "/" + preJob.getId() + ("1".equals(format) ? ".csv" : ".orc"));
            }

            // 判断output，single-random 输出单个文件，按照京享值的需求，应该是传 bucket-sort 吧，或者不传。 输出是一个目录多个文件
            if (parameters.get("output").contains(".")) {
                // 文件路径，传标记参数
                if (!parameters.containsKey("supplier")){
                    parameters.put("supplier", "single-sort");
                }
            }
        }
        else {
            // 判断output，single-random 输出单个文件，按照京享值的需求，应该是传 bucket-sort 吧，或者不传。 输出是一个目录多个文件
            if (parameters.get("output").contains(".")) {
                // 文件路径，传标记参数
                if (!parameters.containsKey("supplier")){
                    parameters.put("supplier", "single-sort");
                }
            }
            else {
                // 默认目录路径，创建目录
                List<String> madeDirs = fileService.mkListDirs(Collections.singletonList(parameters.get("output")), offlineTask.getBdpAccount(),
                        offlineTask.getStoreType());
                parameters.put("output", madeDirs.get(0));
            }
        }

        Job job = new Job();
        job.setId(offlineTask.getId());
        job.setType(TaskTypeEnum.PSI.getName());
        SubTask subTask = SubTask.builder().id(offlineTask.getId()).subId(0)
                .status(TaskStatusEnum.NEW.getStatus())
                .tasks(Collections.singletonList(offlineTask)).build();
        job.setSubTaskList(Collections.singletonList(subTask));
        return job;
    }

    private Job compileLocalWorker(PreJob preJob) {
        OfflineTask offlineTask = this.initOriTask(preJob);
        Map<String, String> parameters = offlineTask.getParameters();
        offlineTask.setName(CommonUtils.genPodName(offlineTask, null));
        offlineTask.setDeploymentPath(offlineTask.getTaskType()+".yaml");
        parameters.put("status-server", nodeIp + ":" + nodePort);
        String clusterId = offlineTask.getId() + "-" + offlineTask.getSubId() + "-"
                + offlineTask.getTaskIndex();
        parameters.put("cluster-id", clusterId);
        parameters.put("party-id", localTarget);
        String outputPrefix = parameters.containsKey("outputPrefix") ? parameters.get("outputPrefix") : "";
        String outputPath = getOutputPath(nameSpace, offlineTask, "output-path",outputPrefix);
        List<String> madeDirs = fileService.mkListDirs(Arrays.asList(outputPath), offlineTask.getBdpAccount(), offlineTask.getStoreType());
        // add output-path in args
        String localArgs = parameters.get("args");
        JSONArray localArgsArray = JSONArray.parseArray(localArgs);
        if(null != localArgsArray) {
            localArgsArray.add(String.format("--output-path=%s", outputPath));
        }
        parameters.put("args", localArgsArray.toJSONString());

        Job job = new Job();
        job.setId(offlineTask.getId());
        job.setType(offlineTask.getTaskType());
        SubTask subTask = SubTask.builder().id(offlineTask.getId()).subId(0)
                .status(TaskStatusEnum.NEW.getStatus())
                .tasks(Collections.singletonList(offlineTask)).build();
        job.setSubTaskList(Collections.singletonList(subTask));
        return job;
    }
   private static List<String> dmpTaskTypes = new ArrayList<String>() {
       {
           add("transferdata");
           add("extractdata");
       }
   };

    private Job compileDefault(PreJob preJob) {
        OfflineTask offlineTask = this.initOriTask(preJob);
        Map<String, String> parameters = offlineTask.getParameters();
        offlineTask.setName(CommonUtils.genPodName(offlineTask, null));
        offlineTask.setDeploymentPath(offlineTask.getTaskType()+".yaml");
        parameters.put("status-server", nodeIp + ":" + nodePort);
        String clusterId = offlineTask.getId() + "-" + offlineTask.getSubId() + "-"
                + offlineTask.getTaskIndex();
        parameters.put("cluster-id", clusterId);
        parameters.put("party-id", localTarget);
        Map<String, String> extParam = offlineTask.getExtParameters() == null? new HashMap<>():offlineTask.getExtParameters();
        extParam.put("INTERACTIVE_EXE_ENV",offlineTask.getParameters().get("systemEnv"));
        extParam.put("BEE_USER",offlineTask.getParameters().get("BEE_USER"));
        extParam.put("BEE_SOURCE",offlineTask.getParameters().get("BEE_SOURCE"));
        extParam.put("INTERACTIVE_URL",portalUrl);
//        if(dmpTaskTypes.contains(offlineTask.getTaskType())) {
//            String outPathPath = parameters.get("output-path");
//            outPathPath = String.format("hdfs://ns22013/user/%s/%s%s/%s/%s/%s/%s", offlineTask.getBdpAccount(), "", nameSpace, offlineTask.getProjectId(), offlineTask.getTaskType(), offlineTask.getId(), outPathPath);
//            List<String> madeDirs = fileService.mkListDirs(Collections.singletonList(outPathPath), offlineTask.getBdpAccount(), offlineTask.getStoreType());
//            parameters.put("output-path", outPathPath);
//        }
        offlineTask.setExtParameters(extParam);
        Job job = new Job();
        job.setId(offlineTask.getId());
        job.setType(offlineTask.getTaskType());
        SubTask subTask = SubTask.builder().id(offlineTask.getId()).subId(0)
                .status(TaskStatusEnum.NEW.getStatus())
                .tasks(Collections.singletonList(offlineTask)).build();
        job.setSubTaskList(Collections.singletonList(subTask));
        return job;
    }

    private Job compileDefault(List<PreJob> preJobs) {
        return null;
    }
    public Job compileHrzFl(PreJob preJob) {
        // 默认使用第一个解析,填充公共值
        OfflineTask oriTask = this.initOriTask(preJob);
        Map<String, String> parameters = oriTask.getParameters();
        Map<String, String> extParameters = oriTask.getExtParameters();
        List<OfflineTask> tasks;
        if (parameters.get("model-type").equals("xgboost")) {
            tasks = this.assembleHrzFlXgb(oriTask, parameters, extParameters, preJob.getPrefix());
        }
        else {
            tasks = this.assembleHrzFlNn(oriTask, parameters, extParameters, preJob.getPrefix());
        }
        // 填充subTask
        SubTask subTask = SubTask.builder().id(preJob.getId()).subId(0)
                .resourceLimitPolicy(new ResourceLimitPolicy())
                .status(TaskStatusEnum.NEW.getStatus()).tasks(tasks).build();
        // 填充job
        return Job.builder().id(preJob.getId()).type(preJob.getType())
                .subTaskList(Collections.singletonList(subTask)).build();
    }

    private List<OfflineTask> assembleHrzFlXgb(OfflineTask oriTask, Map<String, String> parameters,
            Map<String, String> extParameters, String prefix) {
        String workerUrl = "";
        String workDir = "/app/horizontal_federated";
        if (StringUtils.isNotBlank(prefix)) {
            workerUrl = "";
        }
        List<OfflineTask> tasks = new ArrayList<>();
        // 构建worker
        Map<String, String> paramMap = new HashMap<>(parameters);
        if (StringUtils.isBlank(paramMap.get("test-data"))) {
            paramMap.put("test-data", paramMap.get("train-data"));
        }
        paramMap.put("party-id", extParameters.get("party-id"));
        paramMap.put("party-number", extParameters.get("party-number"));
        paramMap.put("target", extParameters.get("target"));
        // model-save-path
        if (StringUtils.isBlank(paramMap.get("model-save-path"))) {
            String outputPrefix = parameters.containsKey("outputPrefix") ? parameters.get("outputPrefix") : "";
            String modelSavePath = getOutputPath(nameSpace, oriTask, "model-save-path",outputPrefix);
            List<String> madeDirs = fileService.mkListDirs(Collections.singletonList(modelSavePath),oriTask.getBdpAccount(),oriTask.getStoreType());
            modelSavePath = madeDirs.get(0);
            paramMap.put("model-save-path", modelSavePath);
        }

        OfflineTask workerTask = new OfflineTask();
        BeanUtils.copyProperties(oriTask, workerTask);
        workerTask.setParameters(paramMap);
        workerTask.setTaskIndex(0);
        workerTask.setDeploymentPath(DeploymentPathConstant.HRZ_FL_BASE);
        workerTask.setName(CommonUtils.genPodName(workerTask, "wk"));
        workerTask.setUrl(workerUrl);
        // 小规模场景部署专用,变换资源配置单位
        if(parameters.containsKey("test-unit")){
            workerTask.setCpu(700);
            workerTask.setMemory(1200);
        }
        workerTask.setWorkDir(workDir);
        if (workerTask.getCpu() == null || workerTask.getCpu().intValue() == 0) {
            workerTask.setCpu(4);
            workerTask.setMemory(8);
        }
        tasks.add(workerTask);
        return tasks;
    }

    private List<OfflineTask> assembleHrzFlNn(OfflineTask oriTask, Map<String, String> parameters,
            Map<String, String> extParameters, String prefix) {
        String serverUrl = "";
        String workerUrl = "";
        String workDir = "/app/horizontal_federated";
        if (StringUtils.isNotBlank(prefix)) {
            serverUrl = "";
            workerUrl = "";
        }
        List<OfflineTask> tasks = new ArrayList<>();
        int index = 0;
        if (extParameters.get("role").equals("leader")) {
            // 构建server
            OfflineTask serverTask = new OfflineTask();
            BeanUtils.copyProperties(oriTask, serverTask);
            serverTask.setTaskIndex(index++);
            serverTask.setDeploymentPath(DeploymentPathConstant.HRZ_FL_BASE);
            serverTask.setName(CommonUtils.genPodName(serverTask, "sv"));
            serverTask.setUrl(serverUrl);
            serverTask.setWorkDir(workDir);
            Map<String, String> paramMap = new HashMap<>(parameters);
            paramMap.put("service-port", "8080");
            paramMap.put("redis-queue-size", "300");
            paramMap.put("create-task", "true");
            paramMap.put("train-type", "sync");
            paramMap.put("total-client-num", extParameters.get("clientNum"));
            paramMap.put("least-client-num", extParameters.get("clientNum"));
            paramMap.put("feature-jobs", "");
            paramMap.put("model-class-name", "FederatedModel");
            serverTask.setParameters(paramMap);
            serverTask.setCpu(2);
            serverTask.setMemory(4);
            if (parameters.containsKey("s-cpu")) {
                serverTask.setCpu(Integer.parseInt(parameters.get("s-cpu")));
                serverTask.setMemory(Integer.parseInt(parameters.get("s-memory")));
            }
            // 小规模场景部署专用,变换资源配置单位
            if(parameters.containsKey("test-unit")){
                serverTask.setCpu(2000);
                serverTask.setMemory(4000);
            }
            tasks.add(serverTask);
        }
        // 构建worker
        Map<String, String> paramMap = new HashMap<>(parameters);
        paramMap.put("feature-jobs", "");
        paramMap.put("target", extParameters.get("target"));
        if (StringUtils.isBlank(paramMap.get("model-save-path"))) {
            String outputPrefix = parameters.containsKey("outputPrefix") ? parameters.get("outputPrefix") : "";
            String modelSavePath = getOutputPath(nameSpace, oriTask, "model-save-path",outputPrefix);
            List<String> madeDirs = fileService.mkListDirs(Collections.singletonList(modelSavePath),oriTask.getBdpAccount(),oriTask.getStoreType());
            modelSavePath = madeDirs.get(0);
            paramMap.put("model-save-path", modelSavePath);
        }
        OfflineTask workerTask = new OfflineTask();
        BeanUtils.copyProperties(oriTask, workerTask);
        workerTask.setParameters(paramMap);
        workerTask.setTaskIndex(index++);
        workerTask.setDeploymentPath(DeploymentPathConstant.HRZ_FL_BASE);
        workerTask.setName(CommonUtils.genPodName(workerTask, "wk"));
        workerTask.setUrl(workerUrl);
        workerTask.setWorkDir(workDir);
        if (workerTask.getCpu() == null || workerTask.getCpu().intValue() == 0) {
            workerTask.setCpu(4);
            workerTask.setMemory(8);
        }
        // 小规模场景部署专用,变换资源配置单位
        if(parameters.containsKey("test-unit")){
            if(extParameters.get("role").equals("leader")) {
                workerTask.setCpu(700);
                workerTask.setMemory(1000);
            }else{
                workerTask.setCpu(700);
                workerTask.setMemory(1000);
            }
        }
        tasks.add(workerTask);
        return tasks;
    }

    public Job compileTree(PreJob preJob) {
        String workerUrl = "";
        String workDir = "TreeTrainer";
        // 默认使用第一个解析,填充公共值
        OfflineTask oriTask = this.initOriTask(preJob);
        Map<String, String> parameters = oriTask.getParameters();
        String useFastMode = parameters.getOrDefault("use-fast-mode", "0");
        if (Objects.equals("1", useFastMode)) {
            workerUrl = "";
        }
        if (StringUtils.isNotBlank(preJob.getPrefix())) {
            workerUrl = "" + preJob.getPrefix()
                    + "TreeTrainer-pch.tar.gz";
        }
        Map<String, String> extParameters = oriTask.getExtParameters();
        int multiSize = Integer.parseInt(extParameters.get("multi-size"));
        String[] multiArr = new String[multiSize];
        for (int j = 0; j < multiSize; j++) {
            multiArr[j] = parameters.get("proxy-remote");
        }
        String parterAddr = String.join(",", multiArr);
        List<OfflineTask> tasks = new ArrayList<>();
        String logPath = "/mnt/logs";
        String outputPrefix = parameters.containsKey("outputPrefix") ? parameters.get("outputPrefix") : "";
        String outputPath = getOutputPath(nameSpace, oriTask, "output-path",outputPrefix);
        String checkPointPath = getOutputPath(nameSpace, oriTask, "checkpoint-path",outputPrefix);;
        String exportPath = getOutputPath(nameSpace, oriTask, "export-path",outputPrefix);
        List<String> madeDirs = fileService.mkListDirs(Arrays.asList(outputPath, checkPointPath, exportPath),oriTask.getBdpAccount(),oriTask.getStoreType());
        outputPath = madeDirs.get(0);
        checkPointPath = madeDirs.get(1);
        exportPath =madeDirs.get(2);
        // 构造worker
        int workerNum = Integer.parseInt(parameters.get("num-workers"));
        for (int i = 0; i < workerNum; i++) {
            OfflineTask workerTask = new OfflineTask();
            BeanUtils.copyProperties(oriTask, workerTask);
            Map<String, String> paramMap = new HashMap<>(parameters);
            if (extParameters.get("role").equals("leader")) {
                paramMap.put("send_metrics_to_follower", "1");
                if (i == 0) {
                    int cpu_master_rate = 1;
                    int memory_master_rate = 1;
                    if (parameters.containsKey("cpu-master-rate")) {
                        cpu_master_rate = Integer.parseInt(parameters.get("cpu-master-rate"));
                    }
                    if (parameters.containsKey("memory-master-rate")) {
                        memory_master_rate = Integer.parseInt(parameters.get("memory-master-rate"));
                    }
                    workerTask.setCpu(workerTask.getCpu() * cpu_master_rate);
                    workerTask.setMemory(workerTask.getMemory() * memory_master_rate);
                }
            }
            paramMap.put("role", extParameters.get("role"));
            paramMap.put("part-id", localTarget);
            paramMap.put("parter-list", extParameters.get("parter-list"));
            paramMap.put("parter-addr", parterAddr);
            paramMap.put("worker-rank", String.valueOf(i));
            paramMap.put("peer-addr", parameters.get("proxy-remote"));
            paramMap.put("start-time", extParameters.get("start-time"));
            paramMap.put("output-path", outputPath);
            paramMap.put("checkpoint-path", checkPointPath);
            paramMap.put("log-path", logPath);
            paramMap.put("export-path", exportPath);

            workerTask.setParameters(paramMap);

            TaskTypeEnum taskType = TaskTypeEnum.getByValue(preJob.getType());
            // 增加随机森林
            if(taskType.equals(TaskTypeEnum.TREE_TRAIN_RF)) {
                workerTask.setDeploymentPath(DeploymentPathConstant.TREE_TRAIN_RF);
            }
            else {
                workerTask.setDeploymentPath(DeploymentPathConstant.TREE_TRAIN_XGB);
            }
            workerTask.setTaskIndex(i);
            workerTask.setName(CommonUtils.genPodName(workerTask, "wk"));
            workerTask.setUrl(workerUrl);
            workerTask.setWorkDir(workDir);
            tasks.add(workerTask);
        }
        // 填充subTask
        SubTask subTask = SubTask.builder().id(preJob.getId()).subId(0)
                .status(TaskStatusEnum.NEW.getStatus()).tasks(tasks).build();
        // 填充job
        return Job.builder().id(preJob.getId()).type(preJob.getType())
                .subTaskList(Collections.singletonList(subTask)).build();
    }

    public Job compileLr(PreJob preJob) {
        // 默认使用第一个解析,填充公共值
        OfflineTask oriTask = this.initOriTask(preJob);
        Map<String, String> parameters = oriTask.getParameters();
        String workDir = "/app/LrTrainer";
        if ("mpc".equals(parameters.get("key-nbits"))) {
            workDir = "/app/LrTrainerMpc";
            parameters.put("key-nbits", "0");
        }
        parameters.put("linear-model-type", "LogisticRegerssion");
        if (parameters.get("regression-type").equals("linear")) {
            parameters.put("linear-model-type", "LinearRegerssion");
        }
        Map<String, String> extParameters = oriTask.getExtParameters();
        List<OfflineTask> tasks = new ArrayList<>();
        if ("local".equals(parameters.get("role"))) {
            // worker启动参数
            Map<String, String> paramMap = new HashMap<>(parameters);
            // worker0 才需要验证集
            paramMap.put("role", "local");
            paramMap.put("worker-rank", "0");
            paramMap.put("num-workers", "1");
            paramMap.put("local-port", extParameters.get("local-port"));
            if (StringUtils.isBlank(paramMap.get("validation-data-path"))) {
                paramMap.put("validation-data-path", paramMap.get("data-path"));
            }
            String outputPrefix = parameters.containsKey("outputPrefix") ? parameters.get("outputPrefix") : "";
            String checkPointPath = getOutputPath(nameSpace, oriTask, "checkpoint-path",outputPrefix);
            List<String> madeDirs = fileService.mkListDirs(Arrays.asList(checkPointPath), oriTask.getBdpAccount(), oriTask.getStoreType());
            checkPointPath = madeDirs.get(0);
            paramMap.put("checkpoint-path", checkPointPath);
            OfflineTask workerTask = new OfflineTask();
            BeanUtils.copyProperties(oriTask, workerTask);
            workerTask.setParameters(paramMap);
            workerTask.setDeploymentPath(DeploymentPathConstant.TRAIN_BASE);
            workerTask.setTaskIndex(0);
            workerTask.setWorkDir(workDir);
            workerTask.setName(CommonUtils.genPodName(workerTask, "wk"));
            workerTask.setCommands(Arrays.asList(CommonConstant.BIN_BASH, "entry_worker.sh"));
            tasks.add(workerTask);
        }
        else {
            // 构造ps
            int index = 0;
            OfflineTask psTask = new OfflineTask();
            BeanUtils.copyProperties(oriTask, psTask);
            psTask.setWorkDir(workDir);
            psTask.setTaskIndex(index++);
            psTask.setName(CommonUtils.genPodName(psTask, "ps"));
            psTask.setCommands(Arrays.asList(CommonConstant.BIN_BASH, "entry_ps.sh"));
            psTask.setDeploymentPath(DeploymentPathConstant.TRAIN_BASE);
            parameters.put("role-id", extParameters.get("role-id"));
            tasks.add(psTask);
            // 构造worker
            String outputPrefix = parameters.containsKey("outputPrefix") ? parameters.get("outputPrefix") : "";
            String checkPointPath = getOutputPath(nameSpace, oriTask, "checkpoint-path",outputPrefix);
            List<String> madeDirs = fileService.mkListDirs(Arrays.asList(checkPointPath),oriTask.getBdpAccount(), oriTask.getStoreType());
            checkPointPath = madeDirs.get(0);
            int workerNum = Integer.parseInt(parameters.get("worker-nums"));
            for (int i = 0; i < workerNum; i++) {
                // worker启动参数
                Map<String, String> paramMap = new HashMap<>(parameters);
                if (StringUtils.isBlank(paramMap.get("validation-data-path"))) {
                    paramMap.put("validation-data-path", paramMap.get("data-path"));
                }
                paramMap.put("checkpoint-path", checkPointPath);
                if (i != 0) {
                    // worker0 才需要验证集
                    paramMap.remove("validation-data-path");
                }
                paramMap.put("role", extParameters.get("role"));
                paramMap.put("worker-rank", String.valueOf(i));
                paramMap.put("num-workers", String.valueOf(workerNum));
                paramMap.put("target", extParameters.get("target"));
                if (extParameters.get("follower-id") != null) {
                    paramMap.put("follower-id", extParameters.get("follower-id"));
                }
                paramMap.put("local-port", extParameters.get("local-port"));

                OfflineTask workerTask = new OfflineTask();
                BeanUtils.copyProperties(oriTask, workerTask);
                workerTask.setWorkDir(workDir);
                workerTask.setParameters(paramMap);
                workerTask.setDeploymentPath(DeploymentPathConstant.TRAIN_BASE);
                workerTask.setTaskIndex(index++);
                workerTask.setName(CommonUtils.genPodName(workerTask, "wk"));
                workerTask.setCommands(Arrays.asList(CommonConstant.BIN_BASH, "entry_worker.sh"));
                tasks.add(workerTask);
            }
        }
        // 填充subTask
        SubTask subTask = SubTask.builder().id(preJob.getId()).subId(0)
                .status(TaskStatusEnum.NEW.getStatus()).tasks(tasks).build();
        // 填充job
        return Job.builder().id(preJob.getId()).type(preJob.getType())
                .subTaskList(Collections.singletonList(subTask)).build();
    }



    private Job compilePredict(PreJob preJob) {
        // 默认使用第一个解析,填充公共值
        OfflineTask oriTask = this.initOriTask(preJob);
        Map<String, String> parameters = oriTask.getParameters();
        Map<String, String> extParameters = oriTask.getExtParameters();
        List<OfflineTask> tasks = new ArrayList<>();
        int index = 0;
        // 构建server
        OfflineTask workerTask = new OfflineTask();
        BeanUtils.copyProperties(oriTask, workerTask);
        workerTask.setTaskIndex(index++);
        workerTask.setUrl("");
        workerTask.setWorkDir("");
        Map<String, String> paramMap = new HashMap<>(parameters);
        String outputPrefix = parameters.containsKey("outputPrefix") ? parameters.get("outputPrefix") : "";
        String outputPath = getOutputPath(nameSpace, oriTask,"output-path",outputPrefix);
        List<String> madeDirs = fileService.mkListDirs(Arrays.asList(outputPath), oriTask.getBdpAccount(), oriTask.getStoreType());
        fileService.mkListDirs(Arrays.asList("/mnt/logs/json"), oriTask.getBdpAccount(), StoreTypeEnum.CFS);
        outputPath = madeDirs.get(0);
        paramMap.put("target", extParameters.get("target"));
        paramMap.put("domain", extParameters.get("domain"));
        paramMap.put("output", outputPath);
        parameters.put("status-server", nodeIp + ":" + nodePort);
        if (workerTask.getCpu() == null || workerTask.getCpu().intValue() == 0) {
            workerTask.setCpu(8);
            workerTask.setMemory(16);
        }
        workerTask.setParameters(paramMap);
        if ("estimate".equals(paramMap.get("mode"))) {
            workerTask.setDeploymentPath(DeploymentPathConstant.PREDICT_EVAL);
            workerTask.setName(CommonUtils.genPodName(workerTask, "eval"));
        }
        else {
            if ("horizontal".equals(paramMap.get("predict-way"))) {
                workerTask.setDeploymentPath(DeploymentPathConstant.PREDICT_HORIZONTAL);
                workerTask.setName(CommonUtils.genPodName(workerTask, "horizontal"));
            }
            else {
                workerTask.setDeploymentPath(DeploymentPathConstant.PREDICT_VERTICAL);
                workerTask.setName(CommonUtils.genPodName(workerTask, "vertical"));
            }
        }
        // nn预测
        if ("NN".equals(workerTask.getParameters().get("type").toUpperCase())
                && "offline".equals(paramMap.get("mode"))) {
            workerTask.setDeploymentPath(DeploymentPathConstant.PREDICT_NN);
        }
        tasks.add(workerTask);
        // 填充subTask
        SubTask subTask = SubTask.builder().id(preJob.getId()).subId(0)
                .status(TaskStatusEnum.NEW.getStatus()).tasks(tasks).build();
        // 填充job
        return Job.builder().id(preJob.getId()).type(preJob.getType())
                .subTaskList(Collections.singletonList(subTask)).build();
    }

    /**
     * compile: shapley-value
     *
     * @return
     */
    public Job compileShapleyValue(PreJob preJob) {
        // 默认使用第一个解析,填充公共值
        OfflineTask oriTask = this.initOriTask(preJob);
        Map<String, String> parameters = oriTask.getParameters();
        Map<String, String> extParameters = oriTask.getExtParameters();
        List<OfflineTask> tasks = new ArrayList<>();

        String workDir = "/app/fl_evaluator";
        // 构造worker
        int workerNum = MapUtils.getInteger(parameters, "worker-nums", 1);
        for (int i = 0; i < workerNum; i++) {
            // worker启动参数
            Map<String, String> paramMap = new HashMap<>(parameters);
            String workerRole = extParameters.get("role");

            // 通用参数配置
            paramMap.put("role", workerRole);
            // [leader/follower]取值必须相同
            paramMap.putIfAbsent("application-id", preJob.getId());

            paramMap.put("target", extParameters.get("target"));
            paramMap.put("local-port", extParameters.get("local-port"));
            paramMap.put("host", extParameters.get("host"));
            paramMap.put("target-list", extParameters.get("target-list"));
            // 算子侧需要，等于proxy-remote
            paramMap.put("remote", proxyHost + ":" + proxyPort);
            String outputPrefix = parameters.containsKey("outputPrefix") ? parameters.get("outputPrefix") : "";
            if (StringUtils.isBlank(paramMap.get("model-save-path"))) {
                String checkpointPath = getOutputPath(nameSpace, oriTask, "model-save-path",outputPrefix);
                List<String> madeDirs = fileService.mkListDirs(Collections.singletonList(checkpointPath), oriTask.getBdpAccount(), oriTask.getStoreType());
                checkpointPath = madeDirs.get(0);
                paramMap.put("model-save-path", checkpointPath);
            }
            // 输出路径
            if (StringUtils.isBlank(paramMap.get("output-dir"))) {
                String processedDataDir = getOutputPath(nameSpace, oriTask, "output-dir",outputPrefix);
                List<String> madeDirs = fileService.mkListDirs(Collections.singletonList(processedDataDir), oriTask.getBdpAccount(), oriTask.getStoreType());
                processedDataDir = madeDirs.get(0);
                paramMap.put("output-dir", processedDataDir);
            }

            // leader参数
            if ("leader".equalsIgnoreCase(workerRole)) {
                // --label-name=label \
                // --task-type=regression \
                // --score-col-name=prediction_score \
                // --pre-col-name=prediction_label \
            }

            if (extParameters.get("follower-id") != null) {
                paramMap.put("follower-id", extParameters.get("follower-id"));
            }

            OfflineTask workerTask = new OfflineTask();
            BeanUtils.copyProperties(oriTask, workerTask);
            workerTask.setParameters(paramMap);
            workerTask.setDeploymentPath(DeploymentPathConstant.SHAPLEY_VALUE_EVALUATE);
            workerTask.setTaskIndex(i);
            workerTask.setName(CommonUtils.genPodName(workerTask, null).toLowerCase());
            workerTask.setWorkDir(workDir);
            workerTask.setUrl(null);
            tasks.add(workerTask);
        }
        // 填充subTask
        SubTask subTask = SubTask.builder().id(preJob.getId()).subId(0)
                .status(TaskStatusEnum.NEW.getStatus()).tasks(tasks).build();
        // 填充job
        return Job.builder().id(preJob.getId()).type(preJob.getType())
                .subTaskList(Collections.singletonList(subTask)).build();
    }

    /**
     * compile: feature 特征工程
     *
     * @return
     */
    public Job compileFeature(PreJob preJob) {
        int index = 0;
        List<OfflineTask> tasks = new ArrayList<>();
        for (OfflineTask offlineTask : preJob.getTasks()) {
            offlineTask.setId(preJob.getId());
            offlineTask.setSubId(0);
            offlineTask.setTaskIndex(index);
            offlineTask.setStatus(TaskStatusEnum.NEW.getStatus());
            offlineTask.setRedis_server(host + ":" + port);
            offlineTask.setRedis_password(password);
            offlineTask.setProxy_remote(proxyHost + ":" + proxyPort);

            Map<String,String> parameters = Maps.newHashMap();
            // init ParamMap with config of nacos
            Properties properties = functorGroup.get(TaskTypeEnum.getByValue(preJob.getType()));
            for (Enumeration<?> e = properties.propertyNames(); e.hasMoreElements(); ) {
                String key = (String) e.nextElement();
                parameters.put(key, properties.getProperty(key));
            }
            // replace paramMap with config of rest api
            parameters.putAll(offlineTask.getParameters());
            this.assembleParamMap(parameters);
            offlineTask.setParameters(parameters);

            Map<String, String> extParameters = offlineTask.getExtParameters();
            String outputPrefix = parameters.containsKey("outputPrefix") ? parameters.get("outputPrefix") : "";
            String resultPath = getOutputPath(nameSpace, offlineTask, "output",outputPrefix);
            List<String> madeDirs = fileService.mkListDirs(Collections.singletonList(resultPath), offlineTask.getBdpAccount(), offlineTask.getStoreType());
            resultPath = madeDirs.get(0);

            offlineTask.getParameters().put("processed-data-dir", resultPath);

            // 构造worker
            int workerNum = Integer.parseInt(parameters.get("worker-nums"));
            for (int i = 0; i < workerNum; i++) {
                // worker启动参数
                Map<String, String> paramMap = new HashMap<>(parameters);

                paramMap.put("role", extParameters.get("role"));
                paramMap.put("follower-id", extParameters.get("follower-id"));
                paramMap.put("internal-proxy", proxyHost + ":" + proxyPort);
                paramMap.put("target", extParameters.get("target")); // 对端地址
                paramMap.put("local-target", offlineTask.getTarget());// 本侧地址

                OfflineTask workerTask = new OfflineTask();
                BeanUtils.copyProperties(offlineTask, workerTask);
                workerTask.setParameters(paramMap);

                String deploymentPath = DeploymentPathConstant.FEATURE;
                if (paramMap != null && paramMap.containsKey("deployment-path")) {
                    workerTask.setDeploymentPath(paramMap.get("deployment-path"));
                }
                else {
                    workerTask.setDeploymentPath(deploymentPath);
                }

                workerTask.setTaskIndex(index);
                index++;
                workerTask.setName(CommonUtils.genPodName(workerTask, null));
                workerTask.setWorkDir("/app/feature_engineering");
                tasks.add(workerTask);
            }
        }

        // 填充subTask
        SubTask subTask = SubTask.builder().id(preJob.getId()).subId(0)
                .status(TaskStatusEnum.NEW.getStatus()).tasks(tasks).build();
        // 填充job
        return Job.builder().id(preJob.getId()).type(preJob.getType())
                .subTaskList(Collections.singletonList(subTask)).build();
    }

    /**
     * compile: feature-fl 横向特征工程
     *
     * @return
     */
    public Job compileFeatureFl(PreJob preJob) {
        // 默认使用第一个解析,填充公共值
        OfflineTask oriTask = this.initOriTask(preJob);
        Map<String, String> parameters = oriTask.getParameters();
        Map<String, String> extParameters = oriTask.getExtParameters();
        List<OfflineTask> tasks = new ArrayList<>();
        String dataName = parameters.get("data-name");

        Map<String, String> paramMap = new HashMap<>(parameters);
        paramMap.put("role", extParameters.get("role"));
        paramMap.put("target", extParameters.get("target"));
        paramMap.put("client-num", extParameters.get("client-num"));
        paramMap.put("app-id", parameters.get("application-id"));
        String outputPrefix = parameters.containsKey("outputPrefix") ? parameters.get("outputPrefix") : "";
        String resultPath = getOutputPath(nameSpace, oriTask, "resultpath",outputPrefix);
        List<String> madeDirs = fileService.mkListDirs(Collections.singletonList(resultPath), oriTask.getBdpAccount(), oriTask.getStoreType());
        resultPath = madeDirs.get(0);
        paramMap.put("result_path", resultPath);

        if (dataName != null && dataName.trim().length() > 0) {
            paramMap.put("data-path", dataName);
        }

        OfflineTask workerTask = new OfflineTask();
        BeanUtils.copyProperties(oriTask, workerTask);
        workerTask.setParameters(paramMap);
        workerTask.setTaskType(TaskTypeEnum.FEATURE_FL.getName());
        workerTask.setDeploymentPath(DeploymentPathConstant.FEATURE_FL);
        workerTask.setTaskIndex(0);
        workerTask.setName(CommonUtils.genPodName(workerTask, null));
        workerTask.setWorkDir("/app/feature_engineering/horizontal/");
        tasks.add(workerTask);

        /**
         * server端多启一个runner
         */
        if (extParameters.get("role") != null && extParameters.get("role").equals("server")) {
            log.info("add server runner!");
            OfflineTask runnerTask = new OfflineTask();
            BeanUtils.copyProperties(workerTask, runnerTask);
            Map<String, String> runnerMap = new HashMap<>(paramMap);
            runnerMap.remove("client-num");
            runnerMap.put("role", "runner");
            runnerMap.put("target", workerTask.getTarget());
            Map<String, String> copyExtParameters = runnerTask.getExtParameters();
            if (copyExtParameters != null && copyExtParameters.size() > 0) {
                copyExtParameters.put("role", "runner");
                copyExtParameters.put("target", workerTask.getTarget());
            }
            runnerTask.setParameters(runnerMap);
            runnerTask.setRole("follower");
            runnerTask.setTaskIndex(1);
            runnerTask.setName(CommonUtils.genPodName(runnerTask, null));
            tasks.add(runnerTask);

        }

        // 填充subTask
        SubTask subTask = SubTask.builder().id(preJob.getId()).subId(0)
                .status(TaskStatusEnum.NEW.getStatus()).tasks(tasks).build();
        // 填充job
        return Job.builder().id(preJob.getId()).type(preJob.getType())
                .subTaskList(Collections.singletonList(subTask)).build();
    }

    /**
     * compile: XGBoost
     *
     * @return
     */
    public Job compileXGBoost(PreJob preJob) {
        // 默认使用第一个解析,填充公共值

        OfflineTask oriTask = this.initOriTask(preJob);

        Map<String, String> parameters = oriTask.getParameters();
        Map<String, String> extParameters = oriTask.getExtParameters();
        List<OfflineTask> tasks = new ArrayList<>();

        String workDir = "/app/boosting_tree/";
        int index = 0;

        /**
         * 构造ray
         */
        String dp = parameters.get("dp");

        // 构造worker
        int workerNum = Integer.parseInt(parameters.get("worker-nums"));
        // worker启动参数
        Map<String, String> paramMap = new HashMap<>(parameters);

        paramMap.put("role", extParameters.get("role"));
        paramMap.put("follower-id", extParameters.get("follower-id"));
        paramMap.put("target", extParameters.get("target"));
        paramMap.put("local-port", extParameters.get("local-port"));
        paramMap.put("worker-rank", String.valueOf(0));
        paramMap.put("num-workers", String.valueOf(workerNum));
        String outputPrefix = parameters.containsKey("outputPrefix") ? parameters.get("outputPrefix") : "";
        String checkPointPath = getOutputPath(nameSpace, oriTask, "checkpoint",outputPrefix);
        String outputPath = getOutputPath(nameSpace, oriTask, "output",outputPrefix);
        String exportPath = getOutputPath(nameSpace, oriTask, "export",outputPrefix);
        paramMap.put("checkpoint-path", checkPointPath);
        paramMap.put("output-path", outputPath);
        paramMap.put("export-path", exportPath);

        if (dp != null && dp.equalsIgnoreCase("true")) {
            paramMap.put("dp", "True");
        }

        OfflineTask workerTask = new OfflineTask();
        BeanUtils.copyProperties(oriTask, workerTask);
        workerTask.setParameters(paramMap);
        workerTask.setDeploymentPath(DeploymentPathConstant.XGBOOST_TRAIN_BASE);
        workerTask.setTaskIndex(index++); // workerTask.setTaskIndex(i);
        workerTask.setName(CommonUtils.genPodName(workerTask, null));
        workerTask.setWorkDir(workDir + "model"); // 解决ray执行时找不到包的问题
        tasks.add(workerTask);
        // }
        // 填充subTask
        SubTask subTask = SubTask.builder().id(preJob.getId()).subId(0)
                .status(TaskStatusEnum.NEW.getStatus()).tasks(tasks).build();
        // 填充job
        return Job.builder().id(preJob.getId()).type(preJob.getType())
                .subTaskList(Collections.singletonList(subTask)).build();
    }

    @Resource
    private Map<TaskTypeEnum, Properties> functorGroup;

    /**
     * 填充默认参数
     *
     * @param preJob
     * @return
     */
    private OfflineTask initOriTask(PreJob preJob) {
        OfflineTask oriTask = preJob.getTasks().get(0);
        oriTask.setId(preJob.getId());
        oriTask.setSubId(0);
        oriTask.setTaskIndex(0);
        oriTask.setStatus(TaskStatusEnum.NEW.getStatus());
        /** k8s环境参数配置 */
        oriTask.setRedis_server(host + ":" + port);
        oriTask.setRedis_password(password);
        oriTask.setProxy_remote(proxyHost + ":" + proxyPort);
        /** 算子侧参数配置 */
        Map<String,String> paramMap = Maps.newHashMap();
        // init ParamMap with config of nacos
        Properties properties;
        TaskTypeEnum taskType = TaskTypeEnum.getByValue(preJob.getType());
        if (taskType == null){
            //为了支持调试,无需缓存
            try {
                String config = mpcConfigService.getConfig(preJob.getType() + ".properties", CommonConstant.DEFAULT_GROUP, 5000);
                if (config == null){
                    throw new CommonException("nacos default config of "+preJob.getType()+" is null!");
                }
                properties = new Properties();
                properties.load(new StringReader(config));
            }catch (Exception e){
                e.printStackTrace();
                throw new CommonException("get config error!");
            }
        }else {
            properties = functorGroup.get(taskType);
        }
        for (Enumeration<?> e = properties.propertyNames(); e.hasMoreElements(); ) {
            String key = (String) e.nextElement();
            paramMap.put(key, properties.getProperty(key));
        }
        // replace paramMap with config of rest api
        paramMap.putAll(oriTask.getParameters());
        this.assembleParamMap(paramMap);
        oriTask.setParameters(paramMap);
        return oriTask;
    }

    private void assembleParamMap(Map<String, String> map) {
        map.put("redis-host", host);
        map.put("redis-port", String.valueOf(port));
        map.put("redis-pwd", password);
        map.put("redis-server", host + ":" + port);
        map.put("auth-server", nodeIp+":"+nodePort);
        map.put("redis-password", password);
        map.put("proxy-remote", proxyHost + ":" + proxyPort);
        map.put("redis_host", host);
        map.put("redis_port", String.valueOf(port));
        map.put("redis_password", password);
        map.put("redis_max_connections", "100");
        map.put("auth_server", nodeIp+":"+nodePort);
    }

    public PreJob preCompileVif(PreJob preJob) {
        OfflineTask task = preJob.getTasks().get(0);

        // 解析compiler json
        PreJob newJob = new PreJob();
        newJob.setId(preJob.getId());
        newJob.setType(TaskTypeEnum.VIF.getName());
        newJob.setPrefix(preJob.getPrefix());

        String leaderTarget = CommonUtils.getTarget(preJob.getTasks());
        Map<String, List<OfflineTask>> listMap = preJob.getTasks().stream()
                .collect(Collectors.groupingBy(OfflineTask::getTarget));
        Set<String> targets = new HashSet<>(listMap.keySet());
        List<String> ports = new ArrayList<>();
        for (int j = 0; j < targets.size() - 1; j++) {
            ports.add(String.valueOf(50000 + j));
        }

        List<OfflineTask> list = new ArrayList<>();
        AtomicInteger subId = new AtomicInteger(0);
        Set<String> setColumns = new HashSet<>();
        String label = "";
        for (OfflineTask offlineTask : preJob.getTasks()) {
            List<String> listColumn = GsonUtil
                    .changeGsonToList(offlineTask.getParameters().get("columns"), String.class);
            setColumns.addAll(listColumn);
            if (offlineTask.getParameters().get("label") != null) {
                label = offlineTask.getParameters().get("label");
            }
        }

        Map<String, String> columnTargetMap = new HashMap<>();
        for (String column : setColumns) {
            List<String> targetList = new ArrayList<>();
            for (OfflineTask offlineTask : preJob.getTasks()) {
                if (offlineTask.getParameters().get("features").contains(column)) {
                    targetList.add(offlineTask.getTarget());
                    break;
                }
            }
            for (OfflineTask offlineTask : preJob.getTasks()) {
                if (!targetList.get(0).equals(offlineTask.getTarget())) {
                    targetList.add(offlineTask.getTarget());
                }
            }
            columnTargetMap.put(column, String.join(",", targetList));
        }

        final String labelValue = label;

        // 线性回归任务，根据web端传递几个特征值， 发起几个线性回归计算任务
        /*        List<String> listColumn = GsonUtil.changeGsonToList(task.getParameters().get("columns"),
                String.class);*/
        String path = SpringUtil.getProperty("mount.data.path") + "/" + TaskTypeEnum.LR.getName()
                + "/" + preJob.getId() + "/" + "temp-result/";
        setColumns.forEach(column -> {
            AtomicInteger taskIndex = new AtomicInteger(0);
            AtomicInteger followerIndex = new AtomicInteger(0);
            String[] targetArr = columnTargetMap.get(column).split(",");
            preJob.getTasks().forEach(offlineTask -> {
                OfflineTask offlineTaskNew = new OfflineTask();
                offlineTaskNew.setId(preJob.getId());
                offlineTaskNew.setSubId(subId.get());
                int taskIndexNum = taskIndex.getAndIncrement();
                offlineTaskNew.setTaskIndex(taskIndexNum);
                offlineTaskNew.setStatus(TaskStatusEnum.NEW.getStatus());
                offlineTaskNew.setCpu(task.getCpu());
                offlineTaskNew.setMemory(task.getMemory());
                offlineTaskNew.setTarget(offlineTask.getTarget());
                offlineTaskNew.setTaskType(TaskTypeEnum.LR.getName());
                offlineTaskNew.setStoreType(offlineTask.getStoreType());
                Map<String, String> paramsCopy = GsonUtil
                        .changeGsonToMaps(GsonUtil.mapToJson(offlineTask.getParameters()));
                paramsCopy.put("label", column);
                paramsCopy.put("ignore_fileds", labelValue);
                paramsCopy.put("metrics-path", path);

                offlineTaskNew.setParameters(paramsCopy);

                Map<String, String> extParam = new HashMap<>();
                // 适用于非local
                extParam.put("target", columnTargetMap.get(column));
                extParam.put("local-port", String.join(",", ports));
                // 如果包含了特征列，则为leader
                if (offlineTask.getTarget().equals(targetArr[0])) {
                    paramsCopy.put("feature-dim", String.valueOf(
                            Integer.parseInt(offlineTask.getParameters().get("feature-dim")) - 1));
                    extParam.put("role-id", "leader");
                    extParam.put("role", "leader");
                }
                else {
                    extParam.put("role", "follower");
                    int followerId = followerIndex.getAndIncrement();
                    extParam.put("follower-id", String.valueOf(followerId));
                    extParam.put("role-id", "follower" + followerId);
                }
                offlineTaskNew.setExtParameters(extParam);
                list.add(offlineTaskNew);
            });
            subId.incrementAndGet();
        });

        // 在线性回归任务都计算完成后，发起特征工程vif任务
        preJob.getTasks().forEach(offlineTask -> {
            AtomicInteger taskIndex = new AtomicInteger(0);
            OfflineTask offlineTaskNew = new OfflineTask();
            offlineTaskNew.setId(preJob.getId());
            offlineTaskNew.setSubId(subId.get());
            int taskIndexNum = taskIndex.getAndIncrement();
            offlineTaskNew.setTaskIndex(taskIndexNum);
            offlineTaskNew.setStatus(TaskStatusEnum.NEW.getStatus());
            offlineTaskNew.setCpu(task.getCpu());
            offlineTaskNew.setMemory(task.getMemory());
            offlineTaskNew.setTarget(offlineTask.getTarget());
            offlineTaskNew.setTaskType(TaskTypeEnum.VIF.getName());
            offlineTaskNew.setStoreType(offlineTask.getStoreType());
            Map<String, String> paramsCopy = GsonUtil
                    .changeGsonToMaps(GsonUtil.mapToJson(offlineTask.getParameters()));
            paramsCopy.put("vif_path", path);
            paramsCopy.put("example_id", paramsCopy.get("example-id"));
            Map<String, String> extParam = new HashMap<>();
            if (offlineTask.getTarget().equals(leaderTarget)) {
                extParam.put("role", "leader");
                extParam.put("follower-id", "0");
                extParam.put("target", String.join(",", targets));
            }
            else {
                extParam.put("role", "follower");
                extParam.put("follower-id", String.valueOf(taskIndexNum));
                extParam.put("target", leaderTarget);
            }
            offlineTaskNew.setParameters(paramsCopy);
            ParameterParseUtil.parseFeatureParameters(offlineTaskNew);
            offlineTaskNew.setExtParameters(extParam);
            list.add(offlineTaskNew);
        });
        newJob.setTasks(list);
        return newJob;
    }

    public Job compileVif(PreJob preJob) {

        String workerUrl = "";
        String serverUrl = "";
        String workDir = "/app/Regression";
        List<OfflineTask> tasks = new ArrayList<>();
        preJob.getTasks().forEach(oriTask -> {
            Map<String, String> parameters = oriTask.getParameters();
            Map<String, String> extParameters = oriTask.getExtParameters();
            this.assembleParamMap(oriTask.getParameters());
            oriTask.setRedis_server(host + ":" + port);
            oriTask.setRedis_password(password);
            oriTask.setProxy_remote(proxyHost + ":" + proxyPort);
            String outputPrefix = parameters.containsKey("outputPrefix") ? parameters.get("outputPrefix") : "";
            String resultPath = getOutputPath(nameSpace, oriTask, "output",outputPrefix);
            List<String> madeDirs = fileService.mkListDirs(Collections.singletonList(resultPath), oriTask.getBdpAccount(), oriTask.getStoreType());
            resultPath = madeDirs.get(0);

            if (oriTask.getTaskType().equals("lr")) {
                if ("local".equals(parameters.get("role"))) {
                    // worker启动参数
                    Map<String, String> paramMap = new HashMap<>(parameters);
                    // worker0 才需要验证集
                    paramMap.put("role", "local");
                    paramMap.put("worker-rank", "0");
                    paramMap.put("num-workers", "1");
                    paramMap.put("local-port", extParameters.get("local-port"));
                    if (StringUtils.isBlank(paramMap.get("validation-data-path"))) {
                        paramMap.put("validation-data-path", paramMap.get("data-path"));
                    }
                    String checkpointPath = getOutputPath(nameSpace, oriTask, "checkpoint-path",outputPrefix);
                    List<String> madeDirs1 = fileService.mkListDirs(Collections.singletonList(checkpointPath), oriTask.getBdpAccount(), oriTask.getStoreType());
                    checkpointPath = madeDirs1.get(0);
                    paramMap.put("checkpoint-path", checkpointPath);
                    OfflineTask workerTask = new OfflineTask();
                    BeanUtils.copyProperties(oriTask, workerTask);
                    workerTask.setRole(extParameters.get("role"));
                    workerTask.setTaskType("lr");
                    workerTask.setParameters(paramMap);
                    workerTask.setDeploymentPath(DeploymentPathConstant.TRAIN_BASE_VIF);
                    workerTask.setTaskIndex(0);
                    workerTask.setSubId(oriTask.getSubId());
                    workerTask.setName(CommonUtils.genPodName(workerTask, "wk"));
                    workerTask.setUrl(workerUrl);
                    workerTask.setWorkDir(workDir);
                    tasks.add(workerTask);
                }
                else {
                    // 构造ps
                    int index = 0;
                    OfflineTask psTask = new OfflineTask();
                    BeanUtils.copyProperties(oriTask, psTask);
                    psTask.setRole(extParameters.get("role"));
                    psTask.setTaskType("lr");
                    psTask.setTaskIndex(index++);
                    psTask.setSubId(oriTask.getSubId());
                    psTask.setName(CommonUtils.genPodName(psTask, "ps"));
                    psTask.setUrl(serverUrl);
                    psTask.setWorkDir(workDir);
                    psTask.setDeploymentPath(DeploymentPathConstant.TRAIN_BASE_VIF);
                    parameters.put("role-id", extParameters.get("role-id"));
                    tasks.add(psTask);
                    // 构造worker
                    String checkpointPath = "";
                    if(StoreTypeEnum.HDFS.equals(oriTask.getStoreType())) {
                        String prefix = getHDFSBasePath(nameSpace, oriTask.getProjectId(), oriTask.getBdpAccount(),outputPrefix);
                        checkpointPath = String.format("%s/%s/%s/%s", prefix, "lr", oriTask.getId(), "checkpoint-path");
                    }
                    else {
                        checkpointPath = mountDataPath + "/lr/" + oriTask.getId()
                                + "/checkpoint-path";
                    }
                    List<String> madeDirs1 = fileService.mkListDirs(Collections.singletonList(checkpointPath), oriTask.getBdpAccount(), oriTask.getStoreType());
                    checkpointPath = madeDirs1.get(0);
                    int workerNum = Integer.parseInt(parameters.get("worker-nums"));
                    for (int i = 0; i < workerNum; i++) {
                        // worker启动参数
                        Map<String, String> paramMap = new HashMap<>(parameters);
                        if (StringUtils.isBlank(paramMap.get("validation-data-path"))) {
                            paramMap.put("validation-data-path", paramMap.get("data-path"));
                        }
                        paramMap.put("checkpoint-path", checkpointPath);
                        if (i != 0) {
                            // worker0 才需要验证集
                            paramMap.remove("validation-data-path");
                        }
                        paramMap.put("role", extParameters.get("role"));
                        paramMap.put("worker-rank", String.valueOf(i));
                        paramMap.put("num-workers", String.valueOf(workerNum));
                        paramMap.put("target", extParameters.get("target"));
                        if (extParameters.get("follower-id") != null) {
                            paramMap.put("follower-id", extParameters.get("follower-id"));
                        }
                        paramMap.put("local-port", extParameters.get("local-port"));

                        OfflineTask workerTask = new OfflineTask();
                        BeanUtils.copyProperties(oriTask, workerTask);
                        workerTask.setRole(extParameters.get("role"));
                        workerTask.setTaskType("lr");
                        workerTask.setParameters(paramMap);
                        workerTask.setDeploymentPath(DeploymentPathConstant.TRAIN_BASE_VIF);
                        workerTask.setTaskIndex(index++);
                        workerTask.setSubId(oriTask.getSubId());
                        workerTask.setName(CommonUtils.genPodName(workerTask, "wk"));
                        workerTask.setUrl(workerUrl);
                        workerTask.setWorkDir(workDir);
                        tasks.add(workerTask);
                    }
                }
            }
            else {
                parameters.put("processed-data-dir", resultPath);
                // 构造worker
                int workerNum = Integer.parseInt(parameters.get("worker-nums"));
                for (int i = 0; i < workerNum; i++) {
                    // worker启动参数
                    Map<String, String> paramMap = new HashMap<>(parameters);

                    paramMap.put("role", extParameters.get("role"));
                    paramMap.put("follower-id", extParameters.get("follower-id"));
                    paramMap.put("internal-proxy", proxyHost + ":" + proxyPort);
                    paramMap.put("target", extParameters.get("target"));
                    paramMap.put("server-port", "8081");

                    OfflineTask workerTask = new OfflineTask();
                    BeanUtils.copyProperties(oriTask, workerTask);
                    workerTask.setTaskType(TaskTypeEnum.FEATURE.getName());
                    workerTask.setRole(extParameters.get("role"));
                    workerTask.setParameters(paramMap);

                    String deploymentPath = DeploymentPathConstant.FEATURE;
                    if (paramMap != null && paramMap.containsKey("deployment-path")) {
                        workerTask.setDeploymentPath(paramMap.get("deployment-path"));
                    }
                    else {
                        workerTask.setDeploymentPath(deploymentPath);
                    }

                    workerTask.setTaskIndex(i);
                    workerTask.setSubId(oriTask.getSubId());
                    workerTask.setName(CommonUtils.genPodName(workerTask, null));
                    workerTask.setWorkDir("/app/feature_engineering");
                    tasks.add(workerTask);
                }
            }

        });

        List<SubTask> subTaskList = tasks.stream()
                .collect(Collectors.groupingBy(OfflineTask::getSubId)).entrySet().stream()
                .map(entry -> {
                    List<OfflineTask> offlineTaskList = entry.getValue().stream()
                            .sorted(Comparator.comparingInt(OfflineTask::getTaskIndex))
                            .collect(Collectors.toList());
                    return SubTask.builder().id(preJob.getId()).subId(entry.getKey())
                            .status(TaskStatusEnum.NEW.getStatus()).tasks(offlineTaskList).build();
                }).collect(Collectors.toList());
        String id = preJob.getId();
        Job job = new Job();
        job.setId(id);
        job.setType(TaskTypeEnum.VIF.getName());
        job.setSubTaskList(subTaskList);
        return job;
    }

    /**
     * 样本稳定性指标评估
     * https://joyspace.jd.com/page/S3se2hxFMnxAg9HZ7Q9S
     * local算子
     * 关键入参：
     * actual-path 实际分布的文件
     * expect-path 预期分布的文件
     * processed-data-dir 结果输出的路径
     *
     * @param preJob
     * @return
     */
    public Job compileStabilityIndex(PreJob preJob) {
        OfflineTask offlineTask = this.initOriTask(preJob);
        Map<String, String> parameters = offlineTask.getParameters();
        offlineTask.setDeploymentPath(DeploymentPathConstant.STABILITY_INDEX);
        offlineTask.setTaskType(TaskTypeEnum.STABILITY_INDEX.getName());
        offlineTask.setName(CommonUtils.genPodName(offlineTask, "psi-evaluator"));
        offlineTask.setWorkDir("/app/fl_evaluator");
        offlineTask.setUrl(null);

        parameters.put("role", offlineTask.getRole());
        parameters.put("target", offlineTask.getTarget());
        String outputPrefix = parameters.containsKey("outputPrefix") ? parameters.get("outputPrefix") : "";
        // 输出路径
        if (StringUtils.isEmpty(parameters.get("output-dir"))) {
            String processedDataDir = getOutputPath(nameSpace, offlineTask, "output-dir",outputPrefix);
            List<String> madeDirs = fileService.mkListDirs(Collections.singletonList(processedDataDir), offlineTask.getBdpAccount(), offlineTask.getStoreType());
            processedDataDir = madeDirs.get(0);
            parameters.put("output-dir", processedDataDir);
        }
        // jobs参数 按路径处理。如果按json处理则放开下面
        String actualPath = parameters.get("actual-path");
        String expectPath = parameters.get("expect-path");
        Preconditions.checkArgument(StringUtils.isNotBlank(actualPath),
                String.format("参数actual-path不能为空 preJobId: %s", preJob.getId()));
        Preconditions.checkArgument(StringUtils.isNotBlank(expectPath),
                String.format("expect-path不能为空 preJobId: %s", preJob.getId()));

        // leader/follower要求相同
        parameters.putIfAbsent("application-id", preJob.getId());

        offlineTask.setExtParameters(new HashMap<>());
        Job job = new Job();
        job.setId(offlineTask.getId());
        job.setType(TaskTypeEnum.STABILITY_INDEX.getName());

        SubTask subTask = SubTask.builder().id(offlineTask.getId()).subId(0)
                .status(TaskStatusEnum.NEW.getStatus())
                .tasks(Collections.singletonList(offlineTask)).build();
        job.setSubTaskList(Collections.singletonList(subTask));
        return job;
    }

    /**
     * SCORE_CARD Job
     * 
     * @param preJob
     * @return
     */
    public Job compileScoreCard(PreJob preJob) {
        // 默认使用第一个解析,填充公共值
        OfflineTask oriTask = this.initOriTask(preJob);
        Map<String, String> parameters = oriTask.getParameters();
        Map<String, String> extParameters = oriTask.getExtParameters();
        List<OfflineTask> tasks = new ArrayList<>();
        String workDir = parameters.getOrDefault("workDir", "/app/fl_scorecard");
        // 构造worker
        int workerNum = MapUtils.getInteger(parameters, "worker-nums", 1);
        for (int i = 0; i < workerNum; i++) {
            String dataPath = parameters.get("data-path");
            Preconditions.checkArgument(StringUtils.isNotBlank(dataPath),
                    String.format("data-path can't be empty, jobId: %s", preJob.getId()));
            // worker启动参数
            Map<String, String> paramMap = new HashMap<>(parameters);

            // 通用参数配置
            // [leader/follower]取值必须相同
            paramMap.putIfAbsent("application-id", preJob.getId());
            paramMap.put("target", extParameters.get("target"));
            String outputPrefix = parameters.containsKey("outputPrefix") ? parameters.get("outputPrefix") : "";
            if (StringUtils.isBlank(paramMap.get("model-save-path"))) {
                String checkpointPath = getOutputPath(nameSpace, oriTask, "model-save-path",outputPrefix);
                List<String> madeDirs = fileService.mkListDirs(Collections.singletonList(checkpointPath), oriTask.getBdpAccount(), oriTask.getStoreType());
                checkpointPath = madeDirs.get(0);
                paramMap.put("model-save-path", checkpointPath);
            }
            // 输出路径
            if (StringUtils.isBlank(paramMap.get("output-dir"))) {
                String processedDataDir = getOutputPath(nameSpace, oriTask, "output-dir",outputPrefix);
                List<String> madeDirs = fileService.mkListDirs(Collections.singletonList(processedDataDir), oriTask.getBdpAccount(), oriTask.getStoreType());
                processedDataDir = madeDirs.get(0);
                paramMap.put("output-dir", processedDataDir);
            }

            OfflineTask workerTask = new OfflineTask();
            BeanUtils.copyProperties(oriTask, workerTask);
            workerTask.setParameters(paramMap);
            workerTask.setDeploymentPath(DeploymentPathConstant.SCORE_CARD);
            workerTask.setTaskIndex(i);
            workerTask.setName(CommonUtils.genPodName(workerTask, "sc").toLowerCase());
            workerTask.setWorkDir(workDir);
            workerTask.setUrl(null);
            tasks.add(workerTask);
        }
        // 填充subTask
        SubTask subTask = SubTask.builder().id(preJob.getId()).subId(0)
                .status(TaskStatusEnum.NEW.getStatus()).tasks(tasks).build();
        // 填充job
        return Job.builder().id(preJob.getId()).type(preJob.getType())
                .subTaskList(Collections.singletonList(subTask)).build();
    }
}
