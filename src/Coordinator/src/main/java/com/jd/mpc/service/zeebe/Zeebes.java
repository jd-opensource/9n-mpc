package com.jd.mpc.service.zeebe;

import cn.hutool.core.lang.UUID;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: zeebe 工具类
 * 
 * @Date: 2022/10/17
 */
@Service
public class Zeebes {

    @Resource
    private ZeebeClient client;
    @Value("${target}")
    private String target;

    public void sendDoneMsg(String taskId,String result,String msg) {
        HashMap<Object, Object> map = Maps.newHashMap();
        map.put("OutputVariable_result", JSONObject.parseObject(result));
        map.put("OutputVariable_message",msg);
        String msgName = target;
        client.newPublishMessageCommand()
                .messageName(msgName)
                .correlationKey(taskId)
                .messageId(UUID.randomUUID().toString())
                .variables(map)
                .send()
                .join();
    }

    public void sendErrorMsg(String runID,String result,String msg){
        HashMap<Object, Object> map = Maps.newHashMap();
        map.put("OutputVariable_result",JSONObject.parseObject(result));
        map.put("OutputVariable_message",msg);
        client.newPublishMessageCommand()
                .messageName("Message_global_error")
                .correlationKey(runID)
                .messageId(UUID.randomUUID().toString())
                .variables(map)
                .send()
                .join();
    }

    public void sendResultMsg(String processID, String instanceID, String msgID, List<Object> data){
        HashMap<Object, Object> map = Maps.newHashMap();
        map.put("OutputVariable_data",data);
        client.newPublishMessageCommand()
                .messageName("Message_"+processID+"_"+msgID)
                .correlationKey(instanceID)
                .messageId(UUID.randomUUID().toString())
                .variables(map)
                .send()
                .join();
    }

    public ProcessInstanceEvent createInstance(String processID,Integer version, Map<String,Object> map){
        if (version != null){
            return client.newCreateInstanceCommand()
                    .bpmnProcessId(processID)
                    .version(version)
                    .variables(map)
                    .send()
                    .join();
        }
        return client.newCreateInstanceCommand()
                .bpmnProcessId(processID)
                .latestVersion()
                .variables(map)
                .send()
                .join();
    }
}
