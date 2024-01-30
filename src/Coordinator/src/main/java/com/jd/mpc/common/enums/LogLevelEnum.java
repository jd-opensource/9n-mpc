package com.jd.mpc.common.enums;

public enum LogLevelEnum {
    DEBUG("debug"),
    INFO("info"),
    WARNING("warning"),
    ERROR("error"),
//    ALL("all"),
    ;

    private String desc;

    LogLevelEnum(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}
