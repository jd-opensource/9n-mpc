package com.jd.mpc.common.constant;

import com.jd.mpc.domain.offline.commons.SubTask;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class OfflineTaskMap extends ConcurrentHashMap<String, List<SubTask>> {

    private static volatile OfflineTaskMap taskMap = null;


    private OfflineTaskMap() {
    }

    public static OfflineTaskMap getInstance() {
        //第一次校验GlobalLock是否为空
        if (taskMap == null) {
            synchronized (OfflineTaskMap.class) {
                //第二次校验GlobalLock是否为空
                if (taskMap == null) {
                    taskMap = new OfflineTaskMap();
                }
            }
        }
        return taskMap;
    }
}
