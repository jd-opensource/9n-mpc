package com.jd.mpc.service;

import com.google.gson.reflect.TypeToken;
import com.jd.mpc.common.util.GsonUtil;
import com.jd.mpc.common.util.HttpUtil;
import com.jd.mpc.domain.offline.commons.OfflineTask;
import com.jd.mpc.domain.offline.commons.PreJob;
import com.jd.mpc.domain.task.ParentTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * plumber相关的方法
 */
@Slf4j
@Service
public class PlumberService {

    @Resource
    private TaskPersistenceService taskPersistenceService;

    public Map<String, Object> getConfigById(String id) {
        Map<String,Object> resultMap = new HashMap<>();
        resultMap.put("code",0);
        resultMap.put("message","处理成功");
        return resultMap;
    }


    public boolean callback(String id) {
        return true;
    }
}
