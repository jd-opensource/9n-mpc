package com.jd.mpc.domain.online;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * json任务对象【实时】
 *
 * 
 * @date 2021/9/26 2:33 下午
 */
@EqualsAndHashCode
@Data
public class OnlineJob {
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
    private List<OnlineSubTask> tasks;


}
