package com.jd.mpc.domain.cert;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 
 * @date 2022-04-02 14:29
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SignCertVo {
    private String sign;
    private String certContent;
}
