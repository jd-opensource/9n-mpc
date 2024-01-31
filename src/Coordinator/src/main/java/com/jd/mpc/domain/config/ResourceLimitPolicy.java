package com.jd.mpc.domain.config;

import lombok.Data;

/**
 * @Description: 资源限制策略
 * 
 * @Date: 2022/4/6
 */
@Data
public class ResourceLimitPolicy {
    /**最小cpu*/
    private Integer minCpu = 32;
    /**最小memory*/
    private Integer minMemory = 64;
    /**最大cpu*/
    private Integer maxCpu = 50;
    /**最大内存*/
    private Integer maxMemory = 100;
    /**内存记录重启次数*/
    private Integer restartCount = 0;
    /**最大重启次数 */
    private Integer maxRestartCount = 5;
}
