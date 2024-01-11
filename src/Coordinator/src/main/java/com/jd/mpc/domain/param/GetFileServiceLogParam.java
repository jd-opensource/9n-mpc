package com.jd.mpc.domain.param;

import lombok.Data;

@Data
public class GetFileServiceLogParam {
    private Integer fileServiceType;
    private String bdpAccount;
    private String logLevel;
    private Integer from;
    private Integer size;
    private String startTime;
    private String endTime;
}
