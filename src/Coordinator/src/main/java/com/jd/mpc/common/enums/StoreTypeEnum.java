package com.jd.mpc.common.enums;

/**
 * @Description: 存储类型
 * 
 * @Date: 2022/8/10
 */
public enum StoreTypeEnum {
    HDFS("HDFS"),
    CFS("CFS"),
    ;
    private final String desc;

    StoreTypeEnum(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}
