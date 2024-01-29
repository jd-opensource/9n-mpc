package com.jd.mpc.domain.online;

import lombok.Data;

/**
 * json子任务对象【实时】
 *
 * 
 * @date 2021/11/8 6:09 下午
 */
@Data
public class OnlineSubTask {

    /**
     * 任务类型
     */
    private String type;

    /**
     * 超时毫秒数
     */
    private Integer timeout;

    /**
     * 远端回调地址
     */
    private String url;

    /**
     * 调用方式
     */
    private String method;

    /**
     * 请求header
     */
    private String header;

    /**
     * 请求参数
     */
    private String parameters;

    /**
     * redis 服务地址
     */
    private String redis_server;

    /**
     * redis 服务密码
     */
    private String redis_password;

    /**
     * redis 中存储任务信息的key
     */
    private String redis_key;
}
