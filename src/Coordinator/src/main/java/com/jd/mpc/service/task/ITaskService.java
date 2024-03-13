package com.jd.mpc.service.task;

import com.jd.mpc.domain.offline.commons.Job;
import com.jd.mpc.domain.offline.commons.PreJob;

import java.util.Map;

/**
 * @Description: taskService interface
 * 
 * @Date: 2022/6/28
 */
public interface ITaskService {

    /**
     * match request's Prejob
     * @param preJob
     * @return
     */
    boolean match(PreJob preJob);

    /**
     * create multi-party PreJob
     * @return
     */
    Map<String,PreJob> createTaskMap(PreJob preJob);

    /**
     * compile local-party PreJob
     * @param preJob
     * @return
     */
    Job compile(PreJob preJob);
}
