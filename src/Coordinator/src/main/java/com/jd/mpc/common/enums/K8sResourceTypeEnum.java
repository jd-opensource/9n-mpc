package com.jd.mpc.common.enums;

import lombok.Getter;

import java.util.Objects;

/**
 * k8s资源类型枚举
 *
 * @author luoyuyufei1
 * @date 2021/9/26 8:48 下午
 */
@Getter
public enum K8sResourceTypeEnum {

    /**
     * deployment
     */
    DEPLOYMENT("deployment"),

    /**
     * crd
     */
    CRD("crd");



    private final String name;

    K8sResourceTypeEnum(String name) {
        this.name = name;
    }

    public static K8sResourceTypeEnum getByValue(String name) {
        for (K8sResourceTypeEnum taskTypeEnum : values()) {
            if (Objects.equals(taskTypeEnum.name, name)) {
                return taskTypeEnum;
            }
        }
        return K8sResourceTypeEnum.DEPLOYMENT;
    }
}
