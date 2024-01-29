package com.jd.mpc.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 
 * @date 2022/1/26 2:56 下午
 */
@Data
public class PodInfos {

    private Integer podId;

    private List<String> logsList;

    private List<String> resultList;
}
