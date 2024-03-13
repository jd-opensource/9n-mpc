package com.jd.mpc.domain.param;

import lombok.Data;

@Data
public class GetCoordinatorLogParam {
    private String logLevel;
    private Integer from;
    private Integer size;
    private String startTime;
    private String endTime;
}
