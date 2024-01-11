package com.jd.mpc.domain.offline.jxz;

import lombok.Getter;

/**
 * @author luoyuyufei1
 * @date 2022/1/17 2:31 下午
 */
@Getter
public enum JxzTaskTypeEnum {
    /**
     * mpc
     */
    MPC(0, "mpc"),

    /**
     * local
     */
    LOCAL(1, "local");


    private final Integer code;
    private final String name;

    JxzTaskTypeEnum(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public static JxzTaskTypeEnum getByValue(int value) {
        for (JxzTaskTypeEnum code : values()) {
            if (code.getCode() == value) {
                return code;
            }
        }
        return JxzTaskTypeEnum.MPC;
    }
}
