package com.jd.mpc.domain.online;

import lombok.Data;

import java.util.List;

/**
 * 返回对象【实时】
 *
 * 
 * @date 2021/11/8 6:19 下午
 */
@Data
public class OnlineResponse {
    /**
     * 全局唯一的任务ID
     */
    private String id;

    /**
     * 项目ID
     */
    private String project_id;

    /**
     * 子任务列表
     */
    private List<Result> results;
}
