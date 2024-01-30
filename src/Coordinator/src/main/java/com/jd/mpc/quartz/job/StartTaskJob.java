package com.jd.mpc.quartz.job;

import cn.hutool.extra.spring.SpringUtil;
import com.jd.mpc.service.OfflineService;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

public class StartTaskJob extends QuartzJobBean {

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        OfflineService offlineService = SpringUtil.getBean(OfflineService.class);
        offlineService.startTask();
    }
}
