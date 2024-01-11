package com.jd.mpc.service.zeebe.domain.param;

import lombok.Data;

import java.util.List;

/**
 * @Description: 上报算子化结果
 * 
 * @Date: 2022/11/14
 */
@Data
public class ProcessResultParam {
    private String processID;
    private String instanceID;
    private String msgID;
    /** 结果集合 */
    private List<Object> data;
}
