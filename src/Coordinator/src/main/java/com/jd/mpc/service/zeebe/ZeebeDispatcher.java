package com.jd.mpc.service.zeebe;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import com.jd.mpc.common.enums.TaskStatusEnum;
import com.jd.mpc.common.enums.TaskTypeEnum;
import com.jd.mpc.common.response.CommonException;
import com.jd.mpc.common.util.HttpUtil;
import com.jd.mpc.domain.offline.commons.OfflineTask;
import com.jd.mpc.domain.offline.commons.SubTask;
import com.jd.mpc.service.K8sService;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * @Description: dispatch zeebe job
 * @Author: feiguodong
 * @Date: 2022/10/17
 */
@Slf4j
@Service
public class ZeebeDispatcher {

    @Resource
    private List<IZeebeService> services;
    @Resource
    private K8sService k8sService;

    /**
     * dispatch
     */
    public void doDispatch(final JobClient client, final ActivatedJob job, TaskTypeEnum taskType){
        log.info("[{}] [{}] [zeebeParam]:{}",taskType,job.getVariablesAsMap().get("InputVariable_id"),job.getVariables());
        List<OfflineTask> tasks = null;
        for (IZeebeService service : services) {
            if (service.match(taskType)){
                tasks = service.compile(client, job);
                break;
            }
        }
        if (tasks == null){
            throw new CommonException("service not match");
        }
        // start pod
        SubTask subTask = SubTask.builder()
                .id(tasks.get(0).getId()).subId(0)
                .status(TaskStatusEnum.NEW.getStatus())
                .tasks(tasks)
                .build();
        k8sService.commit(subTask);
        // callback zeebe
        client.newCompleteCommand(job.getKey())
                .variables("{}")
                .send()
                .exceptionally(throwable -> {
                    throw new RuntimeException("Could not complete job " + job, throwable);
                });
    }

    /**
     * error cleaner
     * @param client
     * @param job
     */
    public void handleErrorCleaner(final JobClient client, final ActivatedJob job){
        log.info("[error-cleaner] [{}] [zeebeParam]:{}",job.getVariablesAsMap().get("InputVariable_id"),job.getVariables());
        Map<String, Object> variables = job.getVariablesAsMap();
        String runID = String.valueOf(variables.get("InputVariable_run_id"));
        k8sService.delDeployLike(runID);
        // callback zeebe
        client.newCompleteCommand(job.getKey())
                .variables("{}")
                .send()
                .exceptionally(throwable -> {
                    throw new RuntimeException("Could not complete job " + job, throwable);
                });
    }

    /**
     * send http request
     * @param client
     * @param job
     */
    public void handleHttpCall(final JobClient client,final ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        log.info("[http-json-client] [{}] [zeebeParam]:{}", variables.get("InputVariable_id"),job.getVariables());
        String method = String.valueOf(variables.getOrDefault("InputVariable_method", "POST"));
        if (!"GET".equals(method)){
            method = "POST";
        }
        if (variables.containsKey("InputVariable_method")){
            String inputVariable_method = (String)variables.get("InputVariable_method");
            if (!"POST".equals(inputVariable_method)){
                method = inputVariable_method;
            }
        }
        Map<String,String> headers = (Map<String, String>) variables.get("InputVariable_header");
        Map<String, Object> body = (Map<String, Object>)variables.get("InputVariable_data");
        String url = String.valueOf(variables.get("InputVariable_url"));
        log.info("[http-json-client] [{}] [zeebeUrl]:{}",variables.get("InputVariable_id"),url);
        String response;
        if (method.equals("GET")){
            response = HttpUtil.get(url);
        }else{
            String reqBody = JSONObject.toJSONString(body);
            log.info("[http-json-client] [{}] [zeebeRequest]:{}",variables.get("InputVariable_id"), reqBody);
            response = HttpUtil.post(url, JSONObject.toJSONString(body), null, headers);
        }
        JSONObject jsonObject;
        if (!StringUtils.hasText(response)){
            jsonObject = new JSONObject();
            jsonObject.put("code",500);
            jsonObject.put("errMsg","call failed");
        }
        jsonObject = JSONObject.parseObject(response);
        // callback zeebe
        Map<String,Object> responseMap = Maps.newHashMap();
        log.info("[http-json-client] [{}] [zeebeResponse]:{}",variables.get("InputVariable_id"),response);
        responseMap.put("OutputVariable_data",jsonObject);
        client.newCompleteCommand(job.getKey())
                .variables(responseMap)
                .send()
                .exceptionally(throwable -> {
                    throw new RuntimeException("Could not complete job " + job, throwable);
                });
    }
}
