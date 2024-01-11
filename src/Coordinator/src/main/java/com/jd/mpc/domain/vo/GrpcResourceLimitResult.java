package com.jd.mpc.domain.vo;

import lombok.Data;

/**
 * @Description: 资源限制结果对象
 * @Author: feiguodong
 * @Date: 2022/4/6
 */
@Data
public class GrpcResourceLimitResult {
    /** id */
    private String id;
    /** subId */
    private Integer subId;
    /** 是否pending */
    private boolean pendFlag;
    /** 是否oom */
    private boolean oomFlag;
}
