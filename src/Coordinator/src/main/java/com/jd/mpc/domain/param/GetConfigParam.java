package com.jd.mpc.domain.param;

import lombok.Data;

import java.util.List;
@Data
public class GetConfigParam {
    private List<String> remoteTarget;
    private String coordinatorUrl;
    private List<String> fileServiceList;
}
