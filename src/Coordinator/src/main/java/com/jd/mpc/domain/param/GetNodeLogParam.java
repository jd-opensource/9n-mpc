package com.jd.mpc.domain.param;

import lombok.Data;

/**
 * 获取联邦算子日志的方法入参
 *
 * @date 2023/06/27
 */
@Data
public class GetNodeLogParam {
    private String coordinateTaskId;
    private String logLevel;
    private Integer nodeId;
    private Integer from;
    private Integer size;
}
