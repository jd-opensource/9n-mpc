package com.jd.mpc.domain.offline.commons;

import com.jd.mpc.domain.config.ResourceLimitPolicy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author luoyuyufei1
 * @date 2022/1/14 5:40 下午
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubTask {

    private String id;

    private Integer subId;

    private Integer status;

    private String env;

    /** 资源限制策略 */
    private ResourceLimitPolicy resourceLimitPolicy;

    private List<OfflineTask> tasks;
}
