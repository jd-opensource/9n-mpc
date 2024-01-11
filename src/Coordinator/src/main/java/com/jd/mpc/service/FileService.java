package com.jd.mpc.service;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.jd.mpc.common.enums.StoreTypeEnum;
import com.jd.mpc.common.response.CommonResponse;
import com.jd.mpc.common.response.ErrorStatus;
import com.jd.mpc.common.util.HttpUtil;
import com.jd.mpc.redis.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

/**
 * @Description: 文件服务
 * @Author: feiguodong
 * @Date: 2022/2/16
 */
@Slf4j
@Service
public class FileService {

    @Resource
    private RedisService redisService;

    /**
     * 批量创建文件
     * @param paths
     * @return
     */
    public String mkdir(List<String> paths){
        if(!redisService.hasKey("file-service-addr")){
            log.error("file-service-addr key of redis not exist!");
            return "";
        }
        String url = "http://" + redisService.get("file-service-addr") + "/api/file/mkdirs?path="+String.join(",",paths.toArray(new String[]{}));
        log.info("url:"+url);
        return HttpUtil.get(url);
    }
    public String mkdirs(List<String> paths, String bdpAccount, StoreTypeEnum storeType){
        String fileSvcKey = "file-service-addr";
        if (storeType.equals(StoreTypeEnum.HDFS)){
            fileSvcKey = "file-service-addr::"+bdpAccount;
        }
        if(!redisService.hasKey(fileSvcKey)){
            log.error("file-service-addr key of redis not exist!");
            return "";
        }
        String url = "http://" + redisService.get(fileSvcKey) + "/api/file/mkdirs?path="+String.join(",",paths.toArray(new String[]{}));
        log.info("url:"+url);
        return HttpUtil.get(url);
    }

    /**
     *
     * @param paths
     * @param bdpAccount
     * @param storeType
     * @return
     */
    public List<String> mkListDirs(List<String> paths, String bdpAccount, StoreTypeEnum storeType){
        String rets = mkdirs(paths, bdpAccount, storeType);
        CommonResponse<List<String>> retResp = JSONObject.parseObject(rets, new TypeReference<CommonResponse<List<String>>>() {
        });
        if (!retResp.getStatus().equals(ErrorStatus.SUCCESS)) {
            return Collections.emptyList();
        }
        return retResp.getResult();
    }
    /**
     * 查询文件列表
     * @param path
     * @return
     */
    public String ls(String path){
        String url = "http://" + redisService.get("file-service-addr") + "/api/file/ls?path="+path;
        log.info("url:"+url);
        return HttpUtil.get(url);
    }
}
