package com.jd.mpc.service.cert;

import com.jd.mpc.domain.cert.JobTaskStub;
import com.jd.mpc.mapper.JobTaskStubMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 
 * @date 2022-04-02 11:07
 */
@Component
@Slf4j
public class JobTaskStubService {
    @Resource
    private JobTaskStubMapper jobTaskStubMapper;

    public int insert(JobTaskStub jobTaskStub){
        return jobTaskStubMapper.insert(jobTaskStub);
    }
}
