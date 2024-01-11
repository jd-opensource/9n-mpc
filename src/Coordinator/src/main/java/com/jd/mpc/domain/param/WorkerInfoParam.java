package com.jd.mpc.domain.param;

import lombok.Data;

/**
 * @Description: worker信息
 * @Author: feiguodong
 * @Date: 2022/9/19
 */
@Data
public class WorkerInfoParam {
    /** 随机数 */
    private long random;
    /** 时间戳 */
    private long timestamp;
    /** worker类型 */
    private String workerType;
    /** worker版本号 */
    private String workerVersion;
    /** 任务信息 */
    private TaskInfoParam taskInfo;
}
