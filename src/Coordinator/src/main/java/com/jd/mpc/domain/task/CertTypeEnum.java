package com.jd.mpc.domain.task;

/**
 * @Description: 证书类型
 * 
 * @Date: 2022/9/18
 */
public enum CertTypeEnum {
    ROOT("ROOT"),
    AUTH("AUTH"),
    WORKER("WORKER"),
    ;
    private final String desc;

    CertTypeEnum(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}
