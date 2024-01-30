package com.jd.mpc.cert;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStrictStyle;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;

import cn.hutool.core.codec.Base64;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.Date;

import static com.jd.mpc.cert.CSRUtil.SHA_SIGN_ALGORITHM;
import static com.jd.mpc.cert.KeyGenerator.getPrivateKey;
import static com.jd.mpc.cert.KeyGenerator.getPublicKey;

/**
 * 
 * @date 2022-03-31 17:18
 */
@Slf4j
public class CertGenerator {

    private static X500Name issuerName = null;//根证书发行机构信息
    static {
        Security.addProvider(new BouncyCastleProvider());
        issuerName = getX500Name("JD", "JD", "BeiJing", "BeiJing", "CN", "JDR");
    }


    /**
     * 生成根证书
     * @Param notBefore 证书起始日期
     * @Param notAfter 证书截止日期
     * @Param keyPairPojo 根证书秘钥元信息，加密保存
     * @return
     */
    public static String issueCACert(Date notBefore,Date notAfter,KeyPairPojo rootKeyPairPojo) throws InvalidKeySpecException, NoSuchAlgorithmException, CertificateException, OperatorCreationException, IOException {
        // 证书序列号
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis() / 1000);
        //构建证书对应的公钥
        BCECPublicKey rootPublicKey = getPublicKey(rootKeyPairPojo.getPublicExponent());
        //构建证书对应的私钥
        BCECPrivateKey rootPrivateKey = getPrivateKey(rootKeyPairPojo.getPrivateExponent());
        //构建证书的build
        return certBuilder(issuerName, issuerName, serial, notBefore, notAfter, rootPublicKey, rootPrivateKey);
    }

    /**
     * 签发用户证书
     * @param csr 用户证书请求文件，字符串格式
     * @param notBefore 证书起始日期
     * @param notAfter 证书截止日期
     * @param rootKeyPairPojo 根证书秘钥元信息
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     * @throws IOException
     * @throws InvalidKeySpecException
     * @throws CertificateException
     * @throws OperatorCreationException
     */
    public static String issueUserCert(String csr,Date notBefore,Date notAfter,KeyPairPojo rootKeyPairPojo) throws NoSuchAlgorithmException, InvalidKeyException, IOException, InvalidKeySpecException, CertificateException, OperatorCreationException {
        JcaPKCS10CertificationRequest jcaPKCS10CertificationRequest = CSRUtil.parseCSRStr(csr);
        //证书申请者公钥
        PublicKey userPublicKey = jcaPKCS10CertificationRequest.getPublicKey();
        //证书申请者主体信息
        X500Name userReqName = jcaPKCS10CertificationRequest.getSubject();
        // 证书序列号
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis() / 1000);
        //构建根证书对应的私钥
        BCECPrivateKey rootPrivateKey = getPrivateKey(rootKeyPairPojo.getPrivateExponent());

        //构建证书的build
        return certBuilder(issuerName, userReqName, serial, notBefore, notAfter, userPublicKey, rootPrivateKey);
    }

    /**
     *  根据如下参数获取对应base64编码格式的证书文件字符串
     *      issuerName 与 reqName 对象是同一个则认为生成的是CA证书
     * @param issuerName 颁发者信息
     * @param reqName   请求者主体信息
     *                  <br> issuerName == reqName ---> CA
     * @param serial 证书序列号
     *                 <br>eg: BigInteger serial = BigInteger.valueOf(System.currentTimeMillis() / 1000);
     * @param notBefore 有效期开始时间  2018-08-01 00:00:00
     * @param notAfter 有效期截至时间   2028-08-01 00:00:00
     * @param userPublicKey 请求者主题公钥信息
     * @param rootPrivateKey   颁发者私钥信息
     * @return String
     * @throws OperatorCreationException
     * @throws CertificateException
     * @throws IOException
     */
    public static String certBuilder(X500Name issuerName, X500Name reqName, BigInteger serial, Date notBefore, Date notAfter, PublicKey userPublicKey, PrivateKey rootPrivateKey) throws OperatorCreationException, CertificateException, IOException {
        JcaX509v3CertificateBuilder x509v3CertificateBuilder = new JcaX509v3CertificateBuilder(
                issuerName, serial, notBefore, notAfter, reqName, userPublicKey);
        // 签发者 与 使用者 信息一致则是CA证书生成，开展增加CA标识
        if(issuerName == reqName){
            BasicConstraints constraint = new BasicConstraints(1);
            x509v3CertificateBuilder.addExtension(Extension.basicConstraints, false, constraint);
        }
        //签名的工具
        ContentSigner signer = new JcaContentSignerBuilder(SHA_SIGN_ALGORITHM).setProvider(BouncyCastleProvider.PROVIDER_NAME).build(rootPrivateKey);
        //触发签名产生用户证书
        X509CertificateHolder x509CertificateHolder = x509v3CertificateBuilder.build(signer);
        JcaX509CertificateConverter certificateConverter = new JcaX509CertificateConverter();
        certificateConverter.setProvider(BouncyCastleProvider.PROVIDER_NAME);
        Certificate userCertificate = certificateConverter.getCertificate(x509CertificateHolder);
        //处理证书 ANS.I DER 编码 =》 String Base64编码
        return "-----BEGIN CERTIFICATE-----" + "\n" +
                Base64.encode(userCertificate.getEncoded()) +
                "-----END CERTIFICATE-----";
    }
    /**
     * 证书字符串转对象
     * @param certStr 证书字符串
     * @return X509Certificate
     * @throws UnsupportedEncodingException
     * @throws IOException
     * @throws CertificateException
     */
    public static X509Certificate certStrToObj(String certStr) throws IOException, CertificateException {
        if( !certStr.startsWith("-----BEGIN CERTIFICATE-----") || !certStr.endsWith("-----END CERTIFICATE-----")){
            throw new IOException("cert 信息不合法");
        }
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) certFactory.generateCertificate(
                new ByteArrayInputStream(certStr.getBytes()));
    }

    /**
     * 验证user证书的合法性
     * @param userCert
     * @param rootCert
     * @return
     */
    public static boolean certIsValid(X509Certificate userCert,X509Certificate rootCert) {
        try {
            userCert.checkValidity(new Date());//验证用户证书时间是否在有效期
            Principal issuerDN = userCert.getIssuerDN();
            Principal subjectDN = rootCert.getSubjectDN();
            if (issuerDN.equals(subjectDN)){//验证用户证书的发行者是否为root证书主体
                userCert.verify(rootCert.getPublicKey());//关键步骤：验证用户正式是否为root证书签发
                return true;
            }
        } catch (Exception e) {
            log.error(e.getMessage(),e);
        }
        return false;
    }
    /**
     *  颁发者 或者 申请者 信息封装
     * @param CN 公用名
     *              对于 SSL 证书，一般为网站域名；而对于代码签名证书则为申请单位名称；而对于客户端证书则为证书申请者的姓名
     * @param O 组织
     *              对于 SSL 证书，一般为网站域名；而对于代码签名证书则为申请单位名称；而对于客户端单位证书则为证书申请者所在单位名称；
     * @param L 城市
     * @param ST 省/ 市/ 自治区名
     * @param C 国家
     * @param OU 组织单位/显示其他内容
     * @return
     */
    public static X500Name getX500Name(String CN, String O, String L, String ST, String C , String OU) {
        X500NameBuilder rootIssueMessage = new X500NameBuilder(
                BCStrictStyle.INSTANCE);
        rootIssueMessage.addRDN(BCStyle.CN, CN);
        rootIssueMessage.addRDN(BCStyle.O, O);
        rootIssueMessage.addRDN(BCStyle.L, L);
        rootIssueMessage.addRDN(BCStyle.ST, ST);
        rootIssueMessage.addRDN(BCStyle.C, C);
        rootIssueMessage.addRDN(BCStyle.OU, OU);
        return rootIssueMessage.build();
    }
}
