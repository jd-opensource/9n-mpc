package com.jd.mpc.service.zeebe;

import com.jd.mpc.common.enums.TaskTypeEnum;
import com.jd.mpc.domain.offline.commons.OfflineTask;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;

import java.util.List;

/**
 * @Description: interface
 * 
 * @Date: 2022/10/17
 */
public interface IZeebeService {

    /**
     * match taskType
     * @param taskType
     * @return
     */
    boolean match(TaskTypeEnum taskType);

    /**
     * compile task
     * @param client
     * @param job
     * @return
     */
    List<OfflineTask> compile(final JobClient client, final ActivatedJob job);
}
