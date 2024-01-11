package com.jd.mpc.domain.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.jd.mpc.domain.task.AuthStatusEnum;
import com.jd.mpc.domain.task.CertTypeEnum;
import lombok.Data;

import java.util.Date;

/**
 * @Description: dto
 * 
 * @Date: 2022/10/9
 */
@Data
public class AuthInfoDto {
    /** id */
    private Long id;
    /** domain */
    private String domain;
    /** 证书类型 */
    private CertTypeEnum certType;
    /** 证书 */
    private String cert;
    /** 公钥 */
    private byte[] pubKey;
    /** 私钥 */
    private byte[] priKey;
    /** 状态 */
    private AuthStatusEnum status;
    /** 创建时间 */
    private Date createTime;
    /** 更新时间 */
    private Date updateTime;
}
