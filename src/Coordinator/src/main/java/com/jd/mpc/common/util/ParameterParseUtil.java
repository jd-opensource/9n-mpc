package com.jd.mpc.common.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.alibaba.fastjson.JSONObject;
import com.jd.mpc.common.response.CommonException;
import com.jd.mpc.domain.offline.commons.OfflineTask;
import com.jd.mpc.domain.offline.commons.PreJob;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ParameterParseUtil {
    public static List<String> needSpecialTasks = Arrays.asList("FeatureSelectionCorrelation",
            "FilterCorrelation");

    /**
     * 解析特征工程参数转换
     */
    public static void parse(PreJob preJob) {
        handleCorrelation(preJob);
        for (OfflineTask task : preJob.getTasks()) {
            parseFeatureParameters(task);
        }
    }

    private static void handleCorrelation(PreJob preJob) {
        if (needSpecialTasks.contains(preJob.getTasks().get(0).getTaskType())
//                && preJob.getTasks()
//                .get(0).getParameters().get("is_label").equalsIgnoreCase("feature")
        ) {
            Boolean isLocal = preJob.getTasks().get(0).getParameters().get("is_local")
                    .equals("local") ? true : false;
            if (isLocal) {
                handleCorrelationLocal(preJob);
            }
            else {
                handleCorrelationNotLocal(preJob);
            }
        }
    }

    /**
     * 处理correlation本地任务拆分
     * 
     * @param preJob
     */
    private static void handleCorrelationLocal(PreJob preJob) {
        OfflineTask leaderTask = preJob.getTasks().get(0);
        List<OfflineTask> parsedTasks = new ArrayList<>();
        List<String> joinList = GsonUtil.changeGsonToList(leaderTask.getParameters().get("columns"),
                String.class);
        // for (String c : joinList) {
        OfflineTask newLeaderTask = GsonUtil.changeGsonToBean(GsonUtil.createGsonString(leaderTask),
                OfflineTask.class);
        newLeaderTask.getParameters().put("correlate_feature", "Null");
        List<String> exceptList = GsonUtil
                .changeGsonToList(newLeaderTask.getParameters().get("columns"), String.class);
        newLeaderTask.getParameters().put("columns", GsonUtil.createGsonString(exceptList));
        newLeaderTask.setSubId(0);
        newLeaderTask.setId(preJob.getId());
        parsedTasks.add(newLeaderTask);
        // }
        preJob.setTasks(parsedTasks);
    }

    /**
     * 处理correlation联邦任务拆分
     * 
     * @param preJob
     */
    private static void handleCorrelationNotLocal(PreJob preJob) {
        OfflineTask leaderTask = null;
        OfflineTask followerTask = null;
        for (OfflineTask task : preJob.getTasks()) {

            if (task.getRole().equals("leader")) {
                leaderTask = task;
            }
            else if (task.getRole().equals("follower")) {
                followerTask = task;
            }
        }

        List<OfflineTask> parsedTask = new ArrayList<OfflineTask>();
        String appId = leaderTask.getParameters().get("application-id");
        List<String> joinList = GsonUtil.changeGsonToList(leaderTask.getParameters().get("columns"),
                String.class);
        for (String c : joinList) {
            String applicationId = String.format("%s-%s", appId, c);
            OfflineTask newLeaderTask = GsonUtil
                    .changeGsonToBean(GsonUtil.createGsonString(leaderTask), OfflineTask.class);
            OfflineTask newFollowerTask = GsonUtil
                    .changeGsonToBean(GsonUtil.createGsonString(followerTask), OfflineTask.class);
            newLeaderTask.setSubId(0);
            newLeaderTask.setId(preJob.getId());
            newFollowerTask.setSubId(0);
            newFollowerTask.setId(preJob.getId());
            newLeaderTask.getParameters().put("is_local", "feaderal");
            newFollowerTask.getParameters().put("is_local", "feaderal");
            newLeaderTask.getParameters().put("application-id", applicationId);
            newFollowerTask.getParameters().put("application-id", applicationId);
            newLeaderTask.getParameters().put("columns", "NUll");
            newLeaderTask.getParameters().put("correlate_feature",
                    GsonUtil.createGsonString(Arrays.asList(c)));

            parsedTask.add(newLeaderTask);
            parsedTask.add(newFollowerTask);
        }
        leaderTask.getParameters().put("application-id", String.format("%s-%s", appId, "leader"));
        followerTask.getParameters().put("application-id",
                String.format("%s-%s", appId, "follower"));
        leaderTask.setId(preJob.getId());
        followerTask.setId(preJob.getId());
        leaderTask.getParameters().put("is_local", "local");
        leaderTask.setSubId(0);
        followerTask.setSubId(0);
        followerTask.getParameters().put("is_local", "local");
        parsedTask.add(leaderTask);
        parsedTask.add(followerTask);
        preJob.setTasks(parsedTask);
    }

    public static void parseFeatureParameters(OfflineTask offlineTask) {
        // 新增算子参数测试用
        if (offlineTask.getParameters().containsKey("isDebugger")) {
            return;
        }
        Map<String, String> parameters = new HashMap<>();
        Map<String, String> originalParams = new HashMap<>(offlineTask.getParameters());
        String taskType = offlineTask.getTaskType();
        offlineTask.setTaskType("feature");
        Map<String, Object> jobs = new HashMap<>();
        Map<String, Object> jobDetails = new HashMap<>();
        switch (taskType) {
            case "AverageFilling":
            case "ModeFilling":
                jobDetails.put("columns",
                        GsonUtil.changeGsonToList(originalParams.get("columns"), String.class));
                break;
            case "NullFilling":
                jobDetails.put("columns",
                        GsonUtil.changeGsonToList(originalParams.get("columns"), String.class));
                jobDetails.put("method", originalParams.get("method"));
                jobDetails.put("fillvalue", "Null");
                if ("custom".equals(originalParams.get("method"))) {
                    jobDetails.put("fillvalue",
                            GsonUtil.changeGsonToBean(originalParams.get("fillvalue"), Map.class));
                }
                break;
            case "FrequencyEncoding":
            case "TransferLog10":
            case "MinMaxScaling":
            case "StandardScaling":
            case "TransferLog":
            case "TransferSqrt":
            case "TransferLog2":
            case "TransferAbs":
            case "FeatureSelectionChiSquaredTest":
            case "FeatureCorrelation":
            case "OneHotEncoding":
                jobDetails.put("columns",
                        GsonUtil.changeGsonToList(originalParams.get("columns"), String.class));
                jobDetails.put("is_replace", originalParams.get("is_replace"));
                /**
                 * 增加了数据拆分
                 */
                if (originalParams.get("percent") != null) {
                    Map<String, Object> splitDetails = new HashMap<>();
                    splitDetails.put("percent", Double.parseDouble(originalParams.get("percent")));
                    jobs.put("SplitData", splitDetails);
                }
                if (taskType.equalsIgnoreCase("FeatureCorrelation")) {
                    taskType = "FeatureSelectionCorrelation";
                }
                break;
            case "FilterIsnullrate":
            case "FilterVar":
            case "FilterUnique":
                jobDetails.put("columns",
                        GsonUtil.changeGsonToList(originalParams.get("columns"), String.class));
                jobDetails.put("threshold", Double.valueOf(originalParams.get("threshold")));
                break;
            case "TransferDatatype":
                jobDetails.put("columns",
                        GsonUtil.changeGsonToList(originalParams.get("columns"), String.class));
                jobDetails.put("datatype",
                        GsonUtil.changeGsonToBean(originalParams.get("dataType"), Map.class));
                jobDetails.put("fillvalue",
                        GsonUtil.changeGsonToBean(originalParams.get("fillValue"), Map.class));
                break;
            case "FeatureSelectionWOE":
            case "FeatureSelectionIV":
                buildWoeJobs(originalParams, jobDetails, offlineTask);
                break;
            case "FeatureSelectionCorrelation":
                buildFeatureSectionCorrelationJobs(originalParams, jobDetails, offlineTask);
                break;
            case "FilterCorrelation":
                buildFilterSectionCorrelationJobs(originalParams, jobDetails, offlineTask);
                break;
            case "FilterVIF":
                buildVifSectionCorrelationJobs(originalParams, jobDetails, offlineTask);
                break;
            case "FilterIV":
                buildFilterIVSectionCorrelationJobs(originalParams, jobDetails, offlineTask);
                break;
            case "splitdata":
                taskType = "SplitData";
                jobDetails.put("percent", Double.parseDouble(originalParams.get("percent")));
                break;
            case "FeatureBinning":
                buildFeatureBinningJobs(originalParams, jobDetails);
                break;
            case "FilterNa":
                buildFilterNa(originalParams, jobDetails);
                break;
            case "FeatureDescribe":// 纵向特征统计值
                buildVevrticalFeatureDescribe(originalParams, jobDetails);
                break;
            case "Describe":// 横向特征统计值
                buildHzFeatorureDescribe(originalParams, jobDetails);
                break;

        }
        jobs.put(taskType, jobDetails);
        parameters.put("jobs", GsonUtil.createGsonString(jobs));
        parameters.put("parallel-num", originalParams.getOrDefault("parallel-num", "10"));
        parameters.put("role", offlineTask.getRole());
        parameters.put("batch-num", originalParams.getOrDefault("batch-num", "5"));
        parameters.put("tfrecord-num", originalParams.getOrDefault("tfrecord-num", "1"));
        parameters.put("processed-data-name", "result");
        parameters.put("worker-nums", originalParams.getOrDefault("worker-nums", "1"));
        parameters.put("application-id", originalParams.get("application-id"));
        parameters.put("remote-target", originalParams.get("remote-target"));
        parameters.put("raw-data-dir", originalParams.get("raw-data-dir"));
        parameters.put("data-name", originalParams.get("data-name"));
        parameters.put("data-path", originalParams.get("data-path"));
        parameters.put("processed-data-dir", originalParams.getOrDefault("processed-data-dir",
                "/app/feature_engineering/test_datas/leader/shortdata/processed"));

        parameters.put("heads", GsonUtil.createGsonString(buildParametersHeads(originalParams)));
        offlineTask.setParameters(parameters);
    }

    private static void buildFilterNa(Map<String, String> originalParams,
            Map<String, Object> jobDetails) {
        jobDetails.put("columns", "Null");
        jobDetails.put("ratio", Double.valueOf(originalParams.get("ratio")));
        jobDetails.put("is_local", Boolean.valueOf(originalParams.get("is_local")));
    }

    private static void buildVevrticalFeatureDescribe(Map<String, String> originalParams,
            Map<String, Object> jobDetails) {

        if (StringUtils.isNotBlank(originalParams.get("columns"))) {
            jobDetails.put("columns",
                    GsonUtil.changeGsonToList(originalParams.get("columns"), String.class));
        }
        if (StringUtils.isNotBlank(originalParams.get("value_columns"))) {
            jobDetails.put("value_columns",
                    GsonUtil.changeGsonToList(originalParams.get("value_columns"), String.class));
        }

        if (StringUtils.isNotBlank(originalParams.get("discrete_columns"))) {
            jobDetails.put("discrete_columns", GsonUtil
                    .changeGsonToList(originalParams.get("discrete_columns"), String.class));
        }
    }

    private static void buildHzFeatorureDescribe(Map<String, String> originalParams,
            Map<String, Object> jobDetails) {
        if (StringUtils.isNotBlank(originalParams.get("numerical_cols_name"))) {
            jobDetails.put("numerical_cols_name", GsonUtil
                    .changeGsonToList(originalParams.get("numerical_cols_name"), String.class));
        }

        if (StringUtils.isNotBlank(originalParams.get("discrete_cols_name"))) {
            jobDetails.put("discrete_cols_name", GsonUtil
                    .changeGsonToList(originalParams.get("discrete_cols_name"), String.class));
        }

    }

    private static void buildFeatureBinningJobs(Map<String, String> originalParams,
            Map<String, Object> jobDetails) {
        String method = originalParams.get("method");
        List<String> columns = GsonUtil.changeGsonToList(originalParams.get("columns"),
                String.class);
        jobDetails.put("method", method);
        jobDetails.put("columns", columns);
        jobDetails.put("counting-flag", "True");
        switch (method) {
            case "given":
                final List<List> quantiles = GsonUtil
                        .changeGsonToList(originalParams.get("quantiles"), List.class);
                log.info("buildFeatureBinningJobs quantiles:{}",
                        JSONObject.toJSONString(quantiles));
                jobDetails.put("quantiles", quantiles);
                break;
            case "global-distance":
            case "local-distance":
            case "local-frequency":
                final List<Integer> binningNumbers = GsonUtil
                        .changeGsonToList(originalParams.get("binning-numbers"), Integer.class);
                jobDetails.put("binning-numbers", binningNumbers);
                break;
        }
    }

    private static void buildFilterSectionCorrelationJobs(Map<String, String> originalParams,
            Map<String, Object> jobDetails, OfflineTask offlineTask) {
        Boolean isLocal = originalParams.get("is_local").equals("local") ? true : false;
        String method = originalParams.get("method");
        jobDetails.put("columns", "Null");
        jobDetails.put("is_label", originalParams.get("is_label"));
        jobDetails.put("method", method);
        jobDetails.put("threshold", originalParams.get("threshold"));
        Map<String, Object> allbininfo = GsonUtil.changeGsonToBean(originalParams.get("allbininfo"),
                Map.class);
        List<String> columns = GsonUtil.changeGsonToList(originalParams.get("columns"),
                String.class);
        offlineTask.setCpu(4);
        offlineTask.setMemory(8);
        if (isLocal) {
            jobDetails.put("columns", columns);
            List<String> correlateFeatures = GsonUtil
                    .changeGsonToList(originalParams.get("correlate_feature"), String.class);
            jobDetails.put("correlate_feature", correlateFeatures);
            jobDetails.put("peer_choice_features", "Null");
            jobDetails.put("is_local", "local");

        }
        else {
            jobDetails.put("is_local", "feaderal");
            if ("leader".equals(offlineTask.getRole())) {
                List<String> correlateFeatures = GsonUtil
                        .changeGsonToList(originalParams.get("correlate_feature"), String.class);
                jobDetails.put("correlate_feature", correlateFeatures);
                if (correlateFeatures != null && !correlateFeatures.isEmpty()) {
                    jobDetails.put("columns", "Null");
                }
                else {
                    jobDetails.put("columns", columns);
                }

                if ("spearman".equals(method)) {
                    List<String> peerChoiceReatures = GsonUtil.changeGsonToList(
                            originalParams.get("peer_choice_features"), String.class);
                    // 放入follower的columns 先不放 后面分好jobs再放入
                    jobDetails.put("peer_choice_features",
                            (peerChoiceReatures == null || peerChoiceReatures.isEmpty()) ? "NULL"
                                    : peerChoiceReatures);
                }

            }
            else if ("follower".equals(offlineTask.getRole())) {
                jobDetails.put("columns", columns);
                jobDetails.put("correlate_feature", "Null");
            }
        }
        jobDetails.put("allbininfo", allbininfo);

    }

    private static void buildVifSectionCorrelationJobs(Map<String, String> originalParams,
            Map<String, Object> jobDetails, OfflineTask offlineTask) {
        // Boolean isLocal = originalParams.containsKey("peer_choice_features");
        List<String> columns = GsonUtil.changeGsonToList(originalParams.get("columns"),
                String.class);
        jobDetails.put("col_names", columns);
        jobDetails.put("is_local", originalParams.getOrDefault("is_local", "local"));
        jobDetails.put("threshold", Double.valueOf(originalParams.get("threshold")));

        if (originalParams.containsKey("vif_path")) {
            jobDetails.put("vif_path", originalParams.get("vif_path"));
        }

    }

    private static void buildFilterIVSectionCorrelationJobs(Map<String, String> originalParams,
            Map<String, Object> jobDetails, OfflineTask offlineTask) {
        String isLocal = originalParams.getOrDefault("is_local", "local");
        List<String> columns = GsonUtil.changeGsonToList(originalParams.get("columns"),
                String.class);
        jobDetails.put("columns", columns);
        jobDetails.put("is_local", isLocal);
        String threshold = originalParams.get("threshold");
        String topK = originalParams.get("topk");

        // 需要解析 threshold和TopK, 如果选择了其中一个，另一个值为Null
        if (threshold != null && !"Null".equals(threshold)) {
            jobDetails.put("threshold", Double.valueOf(threshold));
            jobDetails.put("TopK", "Null");
        }
        else if (topK != null && !"Null".equals(topK)) {
            jobDetails.put("threshold", "Null");
            jobDetails.put("TopK", Integer.valueOf(topK));
        }
        else {
            throw new CommonException("FilterIV 参数解析错误，请检查threshold、TopK参数值");
        }

        Map<String, Object> allbininfo = "Null".equals(originalParams.get("allbininfo"))
                ? new HashMap<>()
                : GsonUtil.changeGsonToBean(originalParams.get("allbininfo"), Map.class);
        Map<String, Object> allbinInfoNew = new HashMap<>();
        Iterator iterator = allbininfo.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry entry = (Map.Entry) iterator.next();
            String key = entry.getKey().toString();
            Map value = (Map) entry.getValue();
            Map<String, Object> newValue = new HashMap<>();
            Iterator i1 = value.entrySet().iterator();
            while (i1.hasNext()) {
                Map.Entry valueEntry = (Map.Entry) i1.next();
                String valueKey = valueEntry.getKey().toString();
                if (valueKey.equalsIgnoreCase("cut")) {
                    List<Double> vList = GsonUtil.changeGsonToList(valueEntry.getValue().toString(),
                            Double.class);
                    newValue.put(valueKey, vList);
                }
                else {
                    Integer valueValue = Double.valueOf(valueEntry.getValue().toString())
                            .intValue();
                    newValue.put(valueKey, valueValue);
                }
            }
            allbinInfoNew.put(key, newValue);
        }
        if (allbinInfoNew.isEmpty()) {
            jobDetails.put("allbininfo", "Null");
        }
        else {
            jobDetails.put("allbininfo", allbinInfoNew);
        }
        if (!"local".equals(isLocal) && "leader".equals(offlineTask.getRole())) {
            jobDetails.replace("columns", "Null");
        }
    }

    private static void buildWoeJobs(Map<String, String> originalParams,
            Map<String, Object> jobDetails, OfflineTask offlineTask) {
        String isLocal = originalParams.getOrDefault("is_local", "local");
        List<String> columns = GsonUtil.changeGsonToList(originalParams.get("columns"),
                String.class);
        jobDetails.put("columns", columns);
        jobDetails.put("is_local", isLocal);
        Map<String, Object> allbininfo = "Null".equals(originalParams.get("allbininfo"))
                ? new HashMap<>()
                : GsonUtil.changeGsonToBean(originalParams.get("allbininfo"), Map.class);
        Map<String, Object> allbinInfoNew = new HashMap<>();
        Iterator iterator = allbininfo.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry entry = (Map.Entry) iterator.next();
            String key = entry.getKey().toString();
            Map value = (Map) entry.getValue();
            Map<String, Object> newValue = new HashMap<>();
            Iterator i1 = value.entrySet().iterator();
            while (i1.hasNext()) {
                Map.Entry valueEntry = (Map.Entry) i1.next();
                String valueKey = valueEntry.getKey().toString();
                if (valueKey.equalsIgnoreCase("cut")) {
                    List<Double> vList = GsonUtil.changeGsonToList(valueEntry.getValue().toString(),
                            Double.class);
                    newValue.put(valueKey, vList);
                }
                else {
                    Integer valueValue = Double.valueOf(valueEntry.getValue().toString())
                            .intValue();
                    newValue.put(valueKey, valueValue);
                }
            }
            allbinInfoNew.put(key, newValue);
        }
        if (allbinInfoNew.isEmpty()) {
            jobDetails.put("allbininfo", "Null");
        }
        else {
            jobDetails.put("allbininfo", allbinInfoNew);
        }
        if (!"local".equals(isLocal) && "leader".equals(offlineTask.getRole())) {
            jobDetails.replace("columns", "Null");
        }
    }

    private static void buildFeatureSectionCorrelationJobs(Map<String, String> originalParams,
            Map<String, Object> jobDetails, OfflineTask offlineTask) {

        jobDetails.put("is_label", originalParams.get("is_label"));
        jobDetails.put("is_local", originalParams.get("is_local"));
        jobDetails.put("method", originalParams.get("method"));
        if (originalParams.containsKey("columns")
                && !"Null".equalsIgnoreCase(originalParams.get("columns"))) {
            jobDetails.put("columns",
                    GsonUtil.changeGsonToList(originalParams.get("columns"), String.class));
        }
        else {
            jobDetails.put("columns", "Null");
        }
        if (originalParams.containsKey("correlate_feature")
                && !"Null".equalsIgnoreCase(originalParams.get("correlate_feature"))) {
            jobDetails.put("correlate_feature", GsonUtil
                    .changeGsonToList(originalParams.get("correlate_feature"), String.class));
        }
        else {
            jobDetails.put("correlate_feature", "Null");
        }
        log.info("buildFeatureSectionCorrelationJobs role is " + offlineTask.getRole());
        if ("leader".equalsIgnoreCase(offlineTask.getRole())) {
            String peerChoiceFeature = originalParams.getOrDefault("peer_choice_features", "Null");
            log.info(
                    "buildFeatureSectionCorrelationJobs peerChoiceFeature is " + peerChoiceFeature);
            if (!"Null".equalsIgnoreCase(peerChoiceFeature)) {
                jobDetails.put("peer_choice_features",
                        GsonUtil.changeGsonToList(peerChoiceFeature, String.class));
            }
            else {
                jobDetails.put("peer_choice_features", "Null");
            }
        }
    }

    private static Map<String, Object> buildParametersHeads(Map<String, String> originalParams) {
        Map<String, Object> heads = new HashMap<>();
        if (originalParams.containsKey("features")) {
            List<String> features = GsonUtil.changeGsonToList(originalParams.get("features"),
                    String.class);
            heads.put("features", features);
        }
        if (originalParams.containsKey("example_id")) {
            heads.put("example_id", originalParams.get("example_id"));
        }
        if (originalParams.containsKey("label")) {
            heads.put("label", originalParams.get("label"));
        }
        return heads;
    }

}
