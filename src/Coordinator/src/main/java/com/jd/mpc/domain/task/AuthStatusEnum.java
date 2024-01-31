package com.jd.mpc.domain.task;

/**
 * @Description: 认证信息状态
 * 
 * @Date: 2022/10/9
 */
public enum AuthStatusEnum {
    SUBMIT("提交"),
    PASS("通过"),
    REJECT("拒绝"),
    ;

    private final String desc;

    AuthStatusEnum(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}
