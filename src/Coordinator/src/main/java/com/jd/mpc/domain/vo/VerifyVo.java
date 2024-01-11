package com.jd.mpc.domain.vo;

import lombok.Data;

/**
 * @Description: TODO
 * @Author: feiguodong
 * @Date: 2022/9/19
 */
@Data
public class VerifyVo {
    private String cert;
    private String sig;
    private String data;
}
