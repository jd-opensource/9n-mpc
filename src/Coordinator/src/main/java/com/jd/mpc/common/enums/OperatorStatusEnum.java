package com.jd.mpc.common.enums;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * @Description: 算子状态
 * @Author: feiguodong
 * @Date: 2022/4/1
 */
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum OperatorStatusEnum {
    INTERNAL_ERROR(500, "内部错误"),
    LOSS_LARGE(501, "学习率过大,模型不再收敛"),
    ORI_DATA_ERROR(501, "原始数据错误"),
    PENDING_ERROR(999, "资源不足"),;

    private final Integer code;

    private final String desc;

    OperatorStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static OperatorStatusEnum getByCode(Integer code) {
        for (OperatorStatusEnum value : OperatorStatusEnum.values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
