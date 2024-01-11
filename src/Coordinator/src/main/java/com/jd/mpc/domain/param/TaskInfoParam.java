package com.jd.mpc.domain.param;

import lombok.Data;

import java.util.Map;

/**
 * @Description: 任务信息
 * 
 * @Date: 2022/9/19
 */
@Data
public class TaskInfoParam {
    /** clusterId */
    private String clusterID;
    /** nodeId */
    private String nodeID;
    /** 本侧target */
    private String localDomain;
    /** 对侧target */
    private String remoteDomain;
    /** 额外的信息 */
    private Map<String,Object> extra;
}
