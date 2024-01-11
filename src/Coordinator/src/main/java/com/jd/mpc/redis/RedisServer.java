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
 * @author luoyuyufei1
 * @date 2021/9/28 3:29 下午
 */
@Component
@Slf4j
public class RedisServer implements ApplicationRunner {

    @Resource
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

    /**
     * 初始化CA证书
     * 
     * @throws NoSuchAlgorithmException
     * @throws OperatorCreationException
     * @throws CertificateException
     * @throws InvalidKeySpecException
     * @throws IOException
     */
    public void genCACert() throws NoSuchAlgorithmException, OperatorCreationException,
            CertificateException, InvalidKeySpecException, IOException,
            InvalidAlgorithmParameterException, NoSuchProviderException {
        KeyPairPojo keyPairPojo = KeyGenerator.genRSAKeyPair();
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, -1);
        Date before = calendar.getTime();
        calendar.add(Calendar.YEAR, 11);
        Date after = calendar.getTime();
        System.out.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(before));
        System.out.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(after));
        String cert = CertGenerator.issueCACert(before, after, keyPairPojo);
        CertInfo certInfo = new CertInfo();
        certInfo.setCertContent(cert);
        certInfo.setCreateAt(LocalDateTime.now());
        certInfo.setUpdateAt(LocalDateTime.now());
        certInfo.setIsDeleted(IsDeletedEnum.FALSE.getStatus());
        certInfo.setIsRoot(IsRootEnum.TRUE.getStatus());
        certInfo.setModulus(keyPairPojo.getModulus());
        certInfo.setPrivateExponent(keyPairPojo.getPrivateExponent());
        certInfo.setPublicExponent(keyPairPojo.getPublicExponent());
        int replace = certPersistenceService.replace(certInfo);
        // System.out.println("$$$$$$$$$$$ replace:"+replace);
        log.info("CA privateStr:\n" + keyPairPojo.getPrivateExponent() + "\npublicStr:\n"
                + keyPairPojo.getPublicExponent());
    }

    /**
     * 初始化用户证书
     * 
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeySpecException
     * @throws IOException
     * @throws OperatorCreationException
     * @throws CertificateException
     * @throws InvalidKeyException
     */
    public void genUserCert(String name) throws NoSuchAlgorithmException, InvalidKeySpecException,
            IOException, OperatorCreationException, CertificateException, InvalidKeyException,
            InvalidAlgorithmParameterException, NoSuchProviderException {
        KeyPairPojo userKeyPairPojo = KeyGenerator.genRSAKeyPair();
        PrivateKey userPrivateKey = KeyGenerator
                .getPrivateKey(userKeyPairPojo.getPrivateExponent());
        PublicKey userPublicKey = KeyGenerator.getPublicKey(userKeyPairPojo.getPublicExponent());
        X500Name reqName = CertGenerator.getX500Name(name, name, "BeiJing", "BeiJing", "CN", "JDR");
        // X500Name reqName = CertGenerator.getX500Name("T02", "T02", "BeiJing", "BeiJing", "CN",
        // "JDR");
        // X500Name reqName = CertGenerator.getX500Name("T01", "T01", "BeiJing", "BeiJing", "CN",
        // "JDR");
        // X500Name reqName = CertGenerator.getX500Name("TJD", "TJD", "BeiJing", "BeiJing", "CN",
        // "JDR");
        String csr = CSRUtil.csrBuilder(reqName, userPublicKey, userPrivateKey);
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, -1);
        Date before = calendar.getTime();
        calendar.add(Calendar.YEAR, 11);
        Date after = calendar.getTime();
        System.out.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(before));
        System.out.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(after));
        CertInfo rootCert = certPersistenceService.queryRootCert();
        KeyPairPojo rootKeyPairPojo = new KeyPairPojo(
                tdeService.decryptString(rootCert.getPublicExponent()),
                tdeService.decryptString(rootCert.getPrivateExponent()),
                tdeService.decryptString(rootCert.getModulus()));
        String cert = CertGenerator.issueUserCert(csr, before, after, rootKeyPairPojo);
        CertInfo certInfo = new CertInfo();
        certInfo.setCertContent(cert);
        certInfo.setCreateAt(LocalDateTime.now());
        certInfo.setUpdateAt(LocalDateTime.now());
        certInfo.setIsDeleted(IsDeletedEnum.FALSE.getStatus());
        certInfo.setIsRoot(IsRootEnum.FALSE.getStatus());
        certInfo.setModulus(userKeyPairPojo.getModulus());
        certInfo.setPrivateExponent(userKeyPairPojo.getPrivateExponent());
        certInfo.setPublicExponent(userKeyPairPojo.getPublicExponent());
        int replace = certPersistenceService.replace(certInfo);
        // System.out.println("$$$$$$$$$$$ replace:"+replace);
        log.info("nginxCert:" + name + "\ncert:\n" + cert + "\nprivateStr:\n"
                + userKeyPairPojo.getPrivateExponent() + "\npublicStr:\n"
                + userKeyPairPojo.getPublicExponent());
    }

}
