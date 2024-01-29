package com.jd.mpc.domain.offline.commons;

import lombok.Data;

import java.util.List;

/**
 * 
 * @date 2022/1/11 2:38 下午
 */
@Data
public class PreJob {

    private String id;

    private String type;
    /**TODO 临时添加的前缀,用于下载oss代码 */
    private String prefix;

    private String version = "new"; // 區分老ea算子和新平台算子 默認new  可選值old

    private Boolean isCustomer=false;
    /**
     * 区分测试环境、开发环境
     */
    private String env = "test";

    private List<OfflineTask> tasks;
}
