package com.jd.mpc.domain.vo;

import lombok.Data;

import java.util.Map;

/**
 * 特征worker详细信息
 *
 * @author luoyuyufei1
 * @date 2021/12/9 5:01 下午
 */
@Data
public class FeatureDetail {

    /**
     *
     */
    private Map<String, Float> pearsonScore;

    /**
     *
     */
    private Map<String, Float> ivScore;

    /**
     *
     */
    private String msg;

    /**
     * 状态 0：未完成 1：已完成
     */
    private Long status;
}
