package com.jd.mpc.domain.cert;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 
 * @date 2022-04-01 15:24
 * 存储证书以及秘钥信息
 */
@SuppressWarnings("unused")
@TableName("cert_info")
@Data
public class CertInfo {
    /**
     * 主键[自增]
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 证书文件的字符串内容
     */
    private String certContent;
    /**
     * 公钥指数 ACES加密存储
     */
    private String publicExponent;
    /**
     * 私钥指数 ACES加密存储
     */
    private String privateExponent;
    /**
     * 模数 ACES加密存储
     */
    private String modulus;
    /**
     * x509证书类型
     */
    private Byte isRoot;
    /**
     * 创建时间
     */
    private LocalDateTime createAt;

    /**
     * 更新时间
     */
    private LocalDateTime updateAt;

    /**
     * 是否删除
     */
    private Byte isDeleted;
}
