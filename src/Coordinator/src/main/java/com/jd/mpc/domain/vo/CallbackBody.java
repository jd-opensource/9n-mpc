package com.jd.mpc.domain.vo;

import lombok.Data;

/**
 * 
 * @date 2022/2/8 10:46 上午
 */
@Data
public class CallbackBody {
    private Integer status;
    private String result;
    private String callbackUrl;
}
