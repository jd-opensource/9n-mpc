package com.jd.mpc.cert;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import cn.hutool.core.codec.Base64;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;

/**
 * @author yezhenyue1
 * @date 2022-03-31 18:54
 */
public class CSRUtil {

    public static final String SHA_SIGN_ALGORITHM = "SHA256withECDSA";

    /**
     *  生成CSR请求文件
     * @param reqName 请求者主体信息
     * @param userPublicKey 用户公钥
     * @param userPrivateKey 用户私钥
     * @return
     * @throws OperatorCreationException
     * @throws CertificateException
     * @throws IOException
     */
    public static String csrBuilder(X500Name reqName, PublicKey userPublicKey, PrivateKey userPrivateKey) throws OperatorCreationException, IOException {

        PKCS10CertificationRequestBuilder p10Builder = new JcaPKCS10CertificationRequestBuilder(reqName,userPublicKey );
        JcaContentSignerBuilder csBuilder = new JcaContentSignerBuilder(SHA_SIGN_ALGORITHM);
        ContentSigner csrSigner = csBuilder.build(userPrivateKey);
        PKCS10CertificationRequest csr = p10Builder.build(csrSigner);

        //处理证书 ANS.I DER 编码 =》 String Base64编码
        String encode = Base64.encode(csr.getEncoded());;
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN CERTIFICATE REQUEST-----"+"\n");
        sb.append(encode);
        sb.append("-----END CERTIFICATE REQUEST-----");
        return sb.toString();
    }
    /**
     *  根据CSR字符串转换成JcaPKCS10CertificationRequest对象
     * @param csrStr CSR字符串
     * @return JcaPKCS10CertificationRequest 包含证书申请方主体信息（jcaPKCS10CertificationRequest.getSubject()）以及公钥信息（jcaPKCS10CertificationRequest.getPublicKey()）
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     */
    public static JcaPKCS10CertificationRequest parseCSRStr(String csrStr) throws NoSuchAlgorithmException, InvalidKeyException, IOException {
        if( !csrStr.startsWith("-----BEGIN CERTIFICATE REQUEST-----") || !csrStr.endsWith("-----END CERTIFICATE REQUEST-----")){
            throw new IOException("csr 信息不合法");
        }
        csrStr = csrStr.replace("-----BEGIN CERTIFICATE REQUEST-----"+"\n","");
        csrStr = csrStr.replace("-----END CERTIFICATE REQUEST-----","");
        byte[] bArray = Base64.decode(csrStr);
        PKCS10CertificationRequest csrRequest = new PKCS10CertificationRequest(bArray);
        return new JcaPKCS10CertificationRequest(csrRequest);
    }
}
