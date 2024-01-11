package com.jd.mpc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jd.mpc.domain.task.AuthInfo;
import com.jd.mpc.domain.task.CertTypeEnum;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @Description: mapper
 * @Author: feiguodong
 * @Date: 2022/9/18
 */
@Mapper
public interface AuthInfoMapper extends BaseMapper<AuthInfo> {

    /**
     * 通过domain和证书类型获得认证信息
     * @param domain
     * @param certType
     * @return
     */
    AuthInfo selectByDomainCertType(@Param("domain")String domain,@Param("certType") CertTypeEnum certType);
}
