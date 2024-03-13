package com.jd.mpc.domain.vo;

import lombok.Data;

@Data
public class PredictQuery {

    private String customerId;

    private String ids;

    private String target;
}
