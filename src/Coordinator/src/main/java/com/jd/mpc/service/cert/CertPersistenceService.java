package com.jd.mpc.service.cert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jd.mpc.aces.TdeService;
import com.jd.mpc.common.enums.IsDeletedEnum;
import com.jd.mpc.common.enums.IsRootEnum;
import com.jd.mpc.domain.cert.CertInfo;
import com.jd.mpc.mapper.CertMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;

/**
 * 
 * @date 2022-04-01 16:04
 */
@Component
@Slf4j
public class CertPersistenceService {
    @Resource
    private CertMapper certMapper;
    @Resource
    private TdeService tdeService;
    /**
     * 查询根证书
     * @return
     */
    public CertInfo queryRootCert(){
        LambdaQueryWrapper<CertInfo> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(CertInfo::getIsRoot, IsRootEnum.TRUE.getStatus())
                .eq(CertInfo::getIsDeleted, IsDeletedEnum.FALSE.getStatus());
        CertInfo certInfo = certMapper.selectOne(queryWrapper);
        //对秘钥进行解密处理
        certInfo.setModulus(tdeService.decryptString(certInfo.getModulus()));
        certInfo.setPublicExponent(tdeService.decryptString(certInfo.getPublicExponent()));
        certInfo.setPrivateExponent(tdeService.decryptString(certInfo.getPrivateExponent()));
        return certInfo;
    }

    /**
     * 查询用户证书
     * @return
     */
    public CertInfo queryUserCert(){
        LambdaQueryWrapper<CertInfo> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(CertInfo::getIsRoot, IsRootEnum.FALSE.getStatus())
                .eq(CertInfo::getIsDeleted, IsDeletedEnum.FALSE.getStatus());
        CertInfo certInfo = certMapper.selectOne(queryWrapper);
        //对秘钥进行解密处理
        certInfo.setModulus(tdeService.decryptString(certInfo.getModulus()));
        certInfo.setPublicExponent(tdeService.decryptString(certInfo.getPublicExponent()));
        certInfo.setPrivateExponent(tdeService.decryptString(certInfo.getPrivateExponent()));
        return certInfo;
    }

    /**
     * 查询用户证书
     * @return
     */
    public CertInfo queryUserCertById(Integer id){
        LambdaQueryWrapper<CertInfo> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(CertInfo::getId, id);
        CertInfo certInfo = certMapper.selectOne(queryWrapper);
        //对秘钥进行解密处理
        certInfo.setModulus(tdeService.decryptString(certInfo.getModulus()));
        certInfo.setPublicExponent(tdeService.decryptString(certInfo.getPublicExponent()));
        certInfo.setPrivateExponent(tdeService.decryptString(certInfo.getPrivateExponent()));
        return certInfo;
    }

    /**
     * 更换证书，删除旧证书，插入新证书
     * @param certInfo
     * @return
     */
    @Transactional
    public int replace(CertInfo certInfo){
        //对秘钥进行加密
        certInfo.setModulus(tdeService.encryptString(certInfo.getModulus()));
        certInfo.setPublicExponent(tdeService.encryptString(certInfo.getPublicExponent()));
        certInfo.setPrivateExponent(tdeService.encryptString(certInfo.getPrivateExponent()));
        //查询旧证书，如果存在则软删除
        LambdaQueryWrapper<CertInfo> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(CertInfo::getIsRoot,certInfo.getIsRoot())
                .eq(CertInfo::getIsDeleted, IsDeletedEnum.FALSE.getStatus());
        CertInfo old = certMapper.selectOne(queryWrapper);
        if (old != null){
            old.setIsDeleted(IsDeletedEnum.TRUE.getStatus());
            certMapper.updateById(old);
        }
        return certMapper.insert(certInfo);
    }


    /**
     * 查询根证书
     * @return
     */
    public CertInfo unsafeQueryRootCert(){
        LambdaQueryWrapper<CertInfo> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(CertInfo::getIsRoot, IsRootEnum.TRUE.getStatus())
                .eq(CertInfo::getIsDeleted, IsDeletedEnum.FALSE.getStatus());
        CertInfo certInfo = certMapper.selectOne(queryWrapper);
        //对秘钥进行解密处理
        certInfo.setModulus(certInfo.getModulus());
        certInfo.setPublicExponent(certInfo.getPublicExponent());
        certInfo.setPrivateExponent(certInfo.getPrivateExponent());
        return certInfo;
    }

    /**
     * 查询用户证书
     * @return
     */
    public CertInfo unsafeQueryUserCert(){
        LambdaQueryWrapper<CertInfo> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(CertInfo::getIsRoot, IsRootEnum.FALSE.getStatus())
                .eq(CertInfo::getIsDeleted, IsDeletedEnum.FALSE.getStatus());
        CertInfo certInfo = certMapper.selectOne(queryWrapper);
        //对秘钥进行解密处理
        certInfo.setModulus(certInfo.getModulus());
        certInfo.setPublicExponent(certInfo.getPublicExponent());
        certInfo.setPrivateExponent(certInfo.getPrivateExponent());
        return certInfo;
    }

    /**
     * 查询用户证书
     * @return
     */
    public CertInfo unsafeQueryUserCertById(Integer id){
        LambdaQueryWrapper<CertInfo> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(CertInfo::getId, id);
        CertInfo certInfo = certMapper.selectOne(queryWrapper);
        //对秘钥进行解密处理
        certInfo.setModulus(certInfo.getModulus());
        certInfo.setPublicExponent(certInfo.getPublicExponent());
        certInfo.setPrivateExponent(certInfo.getPrivateExponent());
        return certInfo;
    }

    /**
     * 更换证书，删除旧证书，插入新证书
     * @param certInfo
     * @return
     */
    @Transactional
    public int unsafeInsert(CertInfo certInfo){
        //对秘钥进行加密
        certInfo.setModulus(certInfo.getModulus());
        certInfo.setPublicExponent(certInfo.getPublicExponent());
        certInfo.setPrivateExponent(certInfo.getPrivateExponent());
        return certMapper.insert(certInfo);
    }
}
