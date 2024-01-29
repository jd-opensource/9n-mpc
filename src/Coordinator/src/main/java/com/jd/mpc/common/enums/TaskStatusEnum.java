package com.jd.mpc.common.enums;

import lombok.Getter;

/**
 * 任务状态枚举
 *
 * 
 * @date 2021/9/26 8:48 下午
 */
@Getter
public enum TaskStatusEnum {

    /**
     * 新建
     */
    NEW(0),

    /**
     * 运行中
     */
    RUNNING(1),

    /**
     * 运行结束
     */
    COMPLETED(2),

    /**
     * 运行异常
     */
    ERROR(3),

    /**
     * 运行停止
     */
    STOPPED(4);

    private final Integer status;

    TaskStatusEnum(int status) {
        this.status = status;
    }


}
