package com.jd.mpc.quartz;

import com.jd.mpc.quartz.job.FinishTaskJob;
import com.jd.mpc.quartz.job.StartTaskJob;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuartzConfig {

    /**
     * for {@link StartTaskJob}
     * @return
     */
    @Bean
    public JobDetail startTaskJobDetail(){
        return JobBuilder
                .newJob(StartTaskJob.class)
                .withIdentity("StartTaskJobDetail")
                .storeDurably().build();
    }

    /**
     * for {@link StartTaskJob}
     * @return
     */
    @Bean
    public Trigger startTaskJobTrigger(){
        return TriggerBuilder
                .newTrigger()
                .forJob(startTaskJobDetail())
                .withIdentity("StartTaskJobTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0/1 * * * ? *"))
                .build();
    }

    /**
     * for {@link FinishTaskJob}
     * @return
     */
    @Bean
    public JobDetail finishTaskJobDetail(){
        return JobBuilder
                .newJob(FinishTaskJob.class)
                .withIdentity("FinishTaskJobDetail")
                .storeDurably().build();
    }

    /**
     * for {@link FinishTaskJob}
     * @return
     */
    @Bean
    public Trigger finishTaskJobTrigger(){
        return TriggerBuilder
                .newTrigger()
                .forJob(finishTaskJobDetail())
                .withIdentity("FinishTaskJobTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0/1 * * * ? *"))
                .build();
    }
}
