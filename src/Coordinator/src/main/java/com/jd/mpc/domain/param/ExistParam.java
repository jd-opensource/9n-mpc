package com.jd.mpc.domain.param;

import lombok.Data;

import java.util.Map;

/**
 * @Description: 是否存在
 * @Author: feiguodong
 * @Date: 2022/9/22
 */
@Data
public class ExistParam {

    private String id;

    private String type;

    private String target;

    private Map<String,String> parameters;
}
