package com.jd.mpc.domain.vo;

import lombok.Data;

/**
 * @Description: etl header param
 * @Author: feiguodong
 * @Date: 2022/9/15
 */
@Data
public class EtlHeaderParam {
    private String sql;
    private String sqlParams;
    private String customerId;

    private String target;
}
