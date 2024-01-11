package com.jd.mpc.common.enums;

import lombok.Getter;

/**
 * 
 * @date 2022-04-02 11:51
 */
@Getter
public enum IsRootEnum {
    /**
     * 非根证书
     */
    FALSE((byte) 0),

    /**
     * 根证书
     */
    TRUE((byte) 1);


    private final Byte status;

    IsRootEnum(byte status) {
        this.status = status;
    }

}
