package com.jd.mpc.service;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jd.mpc.domain.task.AuthInfo;
import com.jd.mpc.domain.task.CertTypeEnum;
import com.jd.mpc.mapper.AuthInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


/**
 * @Description: service
 * @Author: feiguodong
 * @Date: 2022/9/18
 */
@Slf4j
@Service
public class AuthInfoService extends ServiceImpl<AuthInfoMapper, AuthInfo> {

    public AuthInfo getByCertInfo(String domain, CertTypeEnum certType){
        return baseMapper.selectByDomainCertType(domain, certType);
    }
}
