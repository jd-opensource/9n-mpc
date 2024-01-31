package com.jd.mpc.redis;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.Date;

import javax.annotation.Resource;

import cn.hutool.core.codec.Base64;
import com.jd.mpc.common.util.JNISigner;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.OperatorCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.jd.mpc.aces.TdeService;
import com.jd.mpc.cert.CSRUtil;
import com.jd.mpc.cert.CertGenerator;
import com.jd.mpc.cert.KeyGenerator;
import com.jd.mpc.cert.KeyPairPojo;
import com.jd.mpc.common.enums.IsDeletedEnum;
import com.jd.mpc.common.enums.IsRootEnum;
import com.jd.mpc.domain.cert.CertInfo;
import com.jd.mpc.service.cert.CertPersistenceService;

import lombok.extern.slf4j.Slf4j;

/**
 * redis 客户端
 *
 * 
 * @date 2021/9/28 3:29 下午
 */
@Component
@Slf4j
public class RedisServer implements ApplicationRunner {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Value("${grpc.server.port}")
    private String grpcServerPort;

    @Value("${node.ip}")
    private String nodeIp;

    @Value("${node.port}")
    private String nodePort;

    @Value("${grpc.regist.port}")
    private String grpcRegistPort;

    @Autowired
    private CertPersistenceService certPersistenceService;

    @Autowired
    private TdeService tdeService;

    /**
     * 服务启动后执行此方法
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        stringRedisTemplate.opsForValue().set("network:coordinator",
                nodeIp + ":" + grpcRegistPort);
        stringRedisTemplate.opsForValue().set("coordinator-portal-pk",
                nodeIp + ":" + nodePort);
        log.info("grpc host:port = {}:{}", nodeIp, nodePort);
    }

}
