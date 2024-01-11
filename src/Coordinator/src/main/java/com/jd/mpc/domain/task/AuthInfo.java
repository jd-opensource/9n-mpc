package com.jd.mpc.domain.task;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @Description: cert pub/pri
 * domain 和 type 作为联合唯一索引
 * @Author: feiguodong
 * @Date: 2022/9/18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("auth_info")
public class AuthInfo {
    /** id */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** domain */
    private String domain;
    /** 证书类型 */
    private CertTypeEnum certType;
    /** 证书 */
    private String cert;
    /** 公钥 */
    private String pubKey;
    /** 私钥 */
    private String priKey;
    /** 状态 */
    private AuthStatusEnum status;
    /** 创建时间 */
    private Date createTime;
    /** 更新时间 */
    private Date updateTime;
}
