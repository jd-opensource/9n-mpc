package com.jd.mpc.common.enums;

import lombok.Getter;

/**
 * 任务类型枚举
 *
 * @author luoyuyufei1
 * @date 2021/9/26 8:48 下午
 */
@Getter
public enum IsDeletedEnum {

    /**
     * 未删除
     */
    FALSE((byte) 0),

    /**
     * 已删除
     */
    TRUE((byte) 1);


    private final Byte status;

    IsDeletedEnum(byte status) {
        this.status = status;
    }


}
