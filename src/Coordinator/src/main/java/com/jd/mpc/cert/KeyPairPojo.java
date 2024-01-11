package com.jd.mpc.cert;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 
 * @date 2022-03-31 17:47
 * 生成公私钥的模数，用于生成根证书，需要通过ACES加密保存，
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class KeyPairPojo implements Serializable {
    private String publicExponent;
    private String privateExponent;
    private String modulus;
}
