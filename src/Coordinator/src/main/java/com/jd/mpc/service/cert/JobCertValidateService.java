package com.jd.mpc.service.cert;

import com.jd.mpc.aces.TdeService;
import com.jd.mpc.cert.CertGenerator;
import com.jd.mpc.cert.KeyGenerator;
import com.jd.mpc.common.enums.IsDeletedEnum;
import com.jd.mpc.common.enums.IsLocalEnum;
import com.jd.mpc.common.util.GsonUtil;
import com.jd.mpc.domain.cert.CertInfo;
import com.jd.mpc.domain.cert.JobTaskStub;
import com.jd.mpc.domain.cert.SignCertVo;
import com.jd.mpc.domain.offline.commons.PreJob;

import cn.hutool.core.codec.Base64;
import lombok.extern.slf4j.Slf4j;
import offline.GrpcOfflineRequest;
import offline.GrpcOfflineResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static com.jd.mpc.cert.CSRUtil.SHA_SIGN_ALGORITHM;

/**
 * 
 * @date 2022-04-01 14:36
 */
@Component
@Slf4j
public class JobCertValidateService {
    @Resource
    private CertPersistenceService certPersistenceService;
    @Resource
    private JobTaskStubService jobTaskStubService;
    @Resource
    private TdeService tdeService;

    /**
     * 遍历map使用用户证书私钥为每个job生成签名
     * @param taskMap key为job执行方target，value为job详情
     * @return map key为job执行方target，value为签名和证书组合
     */
    public Map<String, SignCertVo> genJobSignBatch(Map<String, PreJob> taskMap){
        try {
            CertInfo certInfo = certPersistenceService.queryUserCert();
            if (certInfo == null){
                log.error("用户证书不存在！");
                return null;
            }
            Map<String, SignCertVo> map = new HashMap<>();
            String certContent = certInfo.getCertContent();
            PrivateKey privateKey = KeyGenerator.getPrivateKey(certInfo.getPrivateExponent());
            Signature md5Rsa = Signature.getInstance(SHA_SIGN_ALGORITHM);
            md5Rsa.initSign(privateKey);
            for (Map.Entry<String, PreJob> jobEntry : taskMap.entrySet()) {
                md5Rsa.update(GsonUtil.createGsonString(jobEntry.getValue()).getBytes(StandardCharsets.UTF_8));
                String sign = Base64.encode(md5Rsa.sign());
                SignCertVo signCertVo = new SignCertVo(sign,certContent);
                map.put(jobEntry.getKey(),signCertVo);
            }
            return map;
        } catch (Exception e) {
            log.error(e.getMessage(),e);
        }
        return null;
    }

    /**
     * 使用用户证书私钥为job生成签名
     * @param preJobJson
     * @return
     */
    public SignCertVo genJobSign(String preJobJson){
        try {
            CertInfo certInfo = certPersistenceService.queryUserCert();
            if (certInfo == null){
                log.error("用户证书不存在！");
                return null;
            }
            String certContent = certInfo.getCertContent();
            PrivateKey privateKey = KeyGenerator.getPrivateKey(certInfo.getPrivateExponent());
            Signature md5Rsa = Signature.getInstance(SHA_SIGN_ALGORITHM);
            md5Rsa.initSign(privateKey);
            md5Rsa.update(preJobJson.getBytes(StandardCharsets.UTF_8));
            String sign = Base64.encode(md5Rsa.sign());
            return new SignCertVo(sign,certContent);
        } catch (Exception e) {
            log.error(e.getMessage(),e);
        }
        return null;
    }

    /**
     * 验证执行方返回值携带的签名信息，如果通过则保存到任务存证表
     * @param grpcOfflineResponse
     * @return
     */
    public boolean validateExecutorSign(String target, GrpcOfflineResponse grpcOfflineResponse,String preJobJson,String jobDistributorSign,String jobDistributorCert){
        if (grpcOfflineResponse == null){
            return false;
        }
        if (!grpcOfflineResponse.getSignVerify()){//接收方返回签名验证未通过
            log.error("job target:{},执行方返回签名验证未通过！preJobJson:{}",target,preJobJson);
            return false;
        }
        try {
            String jobExecutorCert = grpcOfflineResponse.getJobExecutorCert();
            String jobExecutorSign = grpcOfflineResponse.getJobExecutorSign();
            if (StringUtils.isBlank(jobExecutorCert)||StringUtils.isBlank(jobExecutorSign)){
                log.error("job target:{},执行方返回的签名和证书为空！preJobJson:{}",target,preJobJson);
                return false;
            }
            X509Certificate userCert = CertGenerator.certStrToObj(jobExecutorCert);
            //1、证书验证，用根证书验证用户证书合法性
            boolean b = certVerify(userCert);
            if (b){
                //2、证书验证通过，开始验证签名
                boolean verify = signVerify(userCert, jobExecutorSign, preJobJson);
                if (verify){
                    //3、签名验证通过，保存任务存证
                    saveJobTaskStub(target,preJobJson,jobDistributorSign,jobDistributorCert,jobExecutorSign,jobExecutorCert);
                    return true;
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(),e);
        }
        return false;
    }

    /**
     * 验证发起端签名，通过后生成执行端签名后返回，验证不通过返回null
     * @param grpcOfflineRequest
     * @return
     */
    public SignCertVo validateDistributorSign(GrpcOfflineRequest grpcOfflineRequest){
        if (grpcOfflineRequest == null){
            return null;
        }
        String preJobJson = grpcOfflineRequest.getJobJson();
        if (StringUtils.isBlank(preJobJson)){
            log.error("jobJson 为空");
            return null;
        }
        try {
            String jobDistributorSign = grpcOfflineRequest.getJobDistributorSign();
            String jobDistributorCert = grpcOfflineRequest.getJobDistributorCert();
            if (StringUtils.isBlank(jobDistributorSign)||StringUtils.isBlank(jobDistributorCert)){
                log.error("job发起方的签名或者证书为空，preJobJson:"+preJobJson);
                return null;
            }
            X509Certificate userCert = CertGenerator.certStrToObj(jobDistributorCert);
            //1、证书验证，用根证书验证用户证书合法性
            boolean b = certVerify(userCert);
            if (b){
                //2、证书验证通过，开始验证签名
                boolean verify = signVerify(userCert, jobDistributorSign, preJobJson);
                if (verify){
                    //3、发起端签名验证通过，生成执行端签名
                    return genJobSign(preJobJson);
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(),e);
        }
        return null;
    }

    /**
     * 用根证书验证用户证书合法性
     * @param userCert
     * @return
     * @throws IOException
     * @throws CertificateException
     */
    public boolean certVerify(X509Certificate userCert) throws IOException, CertificateException {
        CertInfo certInfo = certPersistenceService.queryRootCert();
        if (certInfo == null){
            log.error("根证书不存在！");
            return false;
        }
        X509Certificate rootCert = CertGenerator.certStrToObj(certInfo.getCertContent());
        return CertGenerator.certIsValid(userCert, rootCert);
    }

    /**
     * 用证书公钥验证签名
     * @param userCert
     * @param sign
     * @param data
     * @return
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     * @throws SignatureException
     * @throws IOException
     */
    public boolean signVerify(X509Certificate userCert,String sign,String data) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException, IOException {
        PublicKey publicKey = userCert.getPublicKey();
        Signature md5Rsa = Signature.getInstance(SHA_SIGN_ALGORITHM);
        md5Rsa.initVerify(publicKey);
        md5Rsa.update(data.getBytes(StandardCharsets.UTF_8));
        return md5Rsa.verify(Base64.decode(sign));
    }

    /**
     * 保存任务存证
     * @param target
     * @param preJobJson
     * @param jobDistributorSign
     * @param jobDistributorCert
     * @param jobExecutorSign
     * @param jobExecutorCert
     */
    private void saveJobTaskStub(String target,String preJobJson,String jobDistributorSign,String jobDistributorCert,String jobExecutorSign,String jobExecutorCert){
        PreJob preJob = GsonUtil.changeGsonToBean(preJobJson, PreJob.class);
        JobTaskStub jobTaskStub = new JobTaskStub();
        jobTaskStub.setParentTaskId(preJob.getId());
        jobTaskStub.setPreJobJson(preJobJson);
        jobTaskStub.setJobTarget(target);
        jobTaskStub.setJobDistributorSign(jobDistributorSign);
        jobTaskStub.setJobDistributorCert(jobDistributorCert);
        jobTaskStub.setJobExecutorSign(jobExecutorSign);
        jobTaskStub.setJobExecutorCert(jobExecutorCert);
        jobTaskStub.setIsLocal(IsLocalEnum.FALSE.getStatus());
        jobTaskStub.setCreateAt(LocalDateTime.now());
        jobTaskStub.setUpdateAt(LocalDateTime.now());
        jobTaskStub.setIsDeleted(IsDeletedEnum.FALSE.getStatus());
        jobTaskStubService.insert(jobTaskStub);
    }
}
