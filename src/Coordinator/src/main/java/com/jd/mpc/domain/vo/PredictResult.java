package com.jd.mpc.domain.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PredictResult {

    private String id;

    private Long predictNum;
}
