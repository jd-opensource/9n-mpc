package com.jd.mpc.domain.vo;

import lombok.Data;

import java.util.Set;

/**
 * 任务对象状态
 *
 * @author luoyuyufei1
 * @date 2021/11/23 7:30 下午
 */
@Data
public class TaskStatusInfo {

    /**
     * 任务端id
     */
    @Deprecated
    private String customerId;

    /**
     * target
     */
    private String target;

    /**
     * 任务进度百分比
     */
    private Integer percent;

    /**
     * 任务状态
     */
    private Integer taskStatus;

    /**
     * 运行时间
     */
    private Long runTime;

    /**
     * 任务错误msg
     * */
    private String errorMsg;
    /**
     * 任务错误code
     * */
    private Integer errorCode;

    private Set<String> runningWorkers;

    private Integer workerNum;
}
