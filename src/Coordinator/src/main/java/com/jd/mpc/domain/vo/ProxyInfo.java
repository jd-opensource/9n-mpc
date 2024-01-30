package com.jd.mpc.domain.vo;

import lombok.Data;

import java.util.Map;

@Data
public class ProxyInfo {

    private String customerId;

    private Map<String,String> proxyMap;
}
