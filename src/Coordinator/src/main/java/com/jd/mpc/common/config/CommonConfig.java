package com.jd.mpc.common.config;

import cn.hutool.core.codec.Base64;
import com.alibaba.fastjson.JSON;
import com.jd.mpc.common.util.JNISigner;
import com.jd.mpc.domain.task.AuthInfo;
import com.jd.mpc.domain.task.CertTypeEnum;
import com.jd.mpc.domain.vo.AuthInfoDto;
import com.jd.mpc.grpc.GrpcSignClient;
import com.jd.mpc.service.AuthInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import authprotocol.GrpcVo;

import javax.annotation.Resource;

/**
 * @Description: 通用配置
 * 
 * @Date: 2022/10/9
 */
@Slf4j
@Configuration
public class CommonConfig {

    @Value("${target}")
    private String localTarget;

    @Resource
    private AuthInfoService authInfoService;
    @Resource
    private GrpcSignClient signClient;

    /**
     * init authInfo
     * @return
     */
    @Bean
    public AuthInfoDto authInfoDto(){
        AuthInfo authInfo = authInfoService.getByCertInfo(localTarget, CertTypeEnum.WORKER);
        log.info("authInfo query auth info：{}", JSON.toJSONString(authInfo));
        if (authInfo == null){
            byte[] privateKey = JNISigner.newPrivateKey();
            byte[] publicKey = JNISigner.publicKey(privateKey);
            GrpcVo grpcVo = signClient.issueCert(CertTypeEnum.WORKER.name(), localTarget, publicKey);
            if (grpcVo.getStatus() != 0){
                log.error("worker cert init failed! errCode:"+grpcVo.getStatus()+" errMsg:"+grpcVo.getErrMsg());
                return null;
            }
            String cert = grpcVo.getResult().toStringUtf8();
            authInfo = AuthInfo.builder()
                    .cert(cert)
                    .certType(CertTypeEnum.WORKER)
                    .domain(localTarget)
                    .pubKey(Base64.encode(publicKey))
                    .priKey(Base64.encode(privateKey))
                    .build();
            authInfoService.save(authInfo);
        }
        AuthInfoDto authInfoDto = new AuthInfoDto();
        BeanUtils.copyProperties(authInfo,authInfoDto);
        authInfoDto.setPubKey(Base64.decode(authInfo.getPubKey()));
        authInfoDto.setPriKey(Base64.decode(authInfo.getPriKey()));
        return authInfoDto;
    }
}
