package com.jd.mpc.common.util;

import cn.hutool.extra.spring.SpringUtil;
import com.google.common.collect.Lists;
import com.jd.mpc.domain.offline.commons.OfflineTask;
import com.jd.mpc.service.K8sService;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 通用工具类
 *
 * 
 * @date 2021/9/28 5:46 下午
 */
public class CommonUtils {


    /**
     * list 判空
     *
     * @param list list
     * @param <T>  泛型
     * @return list
     */
    public static <T> List<T> isEmpty(List<T> list) {
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }
        return list;

    }


    /**
     * 生成pod名称
     *
     * @param offlineTask
     * @return
     */
    public static String genPodName(OfflineTask offlineTask, String str) {
        ArrayList<String> list = Lists.newArrayList(
                SpringUtil.getProperty("k8s.name.prefix"),
                offlineTask.getTaskType());
        if (StringUtils.hasText(str)){
            list.add(str);
        }
        list.add(offlineTask.getId());
        list.add(String.valueOf(offlineTask.getSubId()));
        list.add(String.valueOf(offlineTask.getTaskIndex()));
        K8sService k8sService = SpringUtil.getBean(K8sService.class);
        return k8sService.appName2K8sName(String.join("-",list));
    }

    /**
     * 生成自定义路径
     * @param offlineTask
     * @param str
     * @return
     */
    public static String genS3aPath(OfflineTask offlineTask,String str){
        return SpringUtil.getProperty("mount.s3a.data.path")+"/"+offlineTask.getTaskType()+"/"+offlineTask.getId()+(StringUtils.hasText(str)?"/"+str:"");
    }

    /**
     * 生成自定义路径
     * @param offlineTask
     * @param str
     * @return
     */
    public static String genPath(OfflineTask offlineTask,String str){
        return SpringUtil.getProperty("mount.data.path")+"/"+offlineTask.getTaskType()+"/"+offlineTask.getId()+(StringUtils.hasText(str)?"/"+str:"");
    }

    public static String genVolumeHostPath(String volumeName){
        return SpringUtil.getProperty("cfs.node-path.prefix")+"/storage/"+volumeName+"/"+SpringUtil.getProperty("k8s.namespace");
    }

    /**
     * 获得target
     * @param tasks
     * @return
     */
    public static String getTarget(List<OfflineTask> tasks){
        for (OfflineTask task : tasks) {
            if (task.getRole().equals("leader")){
                return task.getTarget();
            }
        }
        return null;
    }

    /**
     * 从字符串中提取数字
     * @param str
     * @return
     */
    public static String getNumFromStr(String str){
        String str2="";
        for(int i=0;i<str.length();i++) {
            if (str.charAt(i) >= 48 && str.charAt(i) <= 57) {
                str2 += str.charAt(i);
            }
        }
        return str2;
    }

    public static String getStringOrDefault(Map<String,Object> map, String str, String defaultValue){
        if (map.containsKey(str)) {
            String value = (String) map.get(str);
            if (StringUtils.hasText(value)){
                return value;
            }
        }
        return defaultValue;
    }

    public static boolean getBooleanOrDefault(Map<String,Object> map, String str, boolean defaultValue){
        if (map.containsKey(str)) {
            Boolean value = (Boolean) map.get(str);
            if (value != null){
                return value;
            }
        }
        return defaultValue;
    }

    public static Integer getPositiveIntegerOrDefault(Map<String,Object> map, String str, Integer defaultValue){
        if (map.containsKey(str)) {
            Integer value =(Integer) map.get(str);
            if (value != null && value > 0){
                return value;
            }
        }
        return defaultValue;
    }

}
