package com.jd.mpc.domain.vo;

import lombok.Data;

/**
 * worker信息
 *
 * @author luoyuyufei1
 * @date 2021/12/21 4:35 下午
 */
@Data
public class WorkerInfo {

    private String clusterId;

    private Integer nodeId;

    private Integer status;

    private String message;

    private String result;

    private Integer percent;
}
