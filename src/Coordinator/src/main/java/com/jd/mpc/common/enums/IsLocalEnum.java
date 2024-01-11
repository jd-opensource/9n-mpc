package com.jd.mpc.common.enums;

import lombok.Getter;

/**
 * 
 * @date 2022-04-02 11:53
 */
@Getter
public enum IsLocalEnum {
    /**
     * 非本地任务
     */
    FALSE((byte) 0),

    /**
     * 本地任务
     */
    TRUE((byte) 1);


    private final Byte status;

    IsLocalEnum(byte status) {
        this.status = status;
    }
}
