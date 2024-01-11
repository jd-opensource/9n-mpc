package com.jd.mpc.domain.vo;

import lombok.Data;

import java.util.Map;

/**
 * @author luoyuyufei1
 * @date 2022/1/12 8:28 下午
 */
@Data
public class SyncInfo {

    /**
     *
     */
    private String customerId;

    /**
     *
     */
    private String target;

    /**
     *
     */
    private Map<String, String> dataInfo;
}
