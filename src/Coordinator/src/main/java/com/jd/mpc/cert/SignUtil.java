package com.jd.mpc.cert;

import cn.hutool.core.codec.Base64;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;

public class SignUtil {

    private static final String SIGN_ALGORITHM = "SHA256withECDSA";

    /**
     * 签名
     * @param priKeyStr 私钥
     * @param oriData 原始数据
     * @return base64编码后的结果
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     * @throws SignatureException
     */
    public static String sign(String priKeyStr,String oriData) throws IOException, NoSuchAlgorithmException, InvalidKeyException, SignatureException, NoSuchProviderException {
        BCECPrivateKey privateKey = KeyGenerator.getPrivateKey(priKeyStr);
        return sign(privateKey,oriData);
    }

    public static String sign(PrivateKey privateKey,String oriData) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException, NoSuchProviderException {
        Signature signature = Signature.getInstance(SIGN_ALGORITHM,BouncyCastleProvider.PROVIDER_NAME);
        signature.initSign(privateKey);
        signature.update(oriData.getBytes(StandardCharsets.UTF_8));
        return Base64.encode(signature.sign());
    }

    /**
     * 对base64编码的数据进行验证
     * @param pubKeyStr 公钥
     * @param oriData 原始数据
     * @param signStr base64编码的签名字符串
     * @return
     */
    public static boolean verify(String pubKeyStr,String oriData,String signStr) throws IOException, NoSuchAlgorithmException, InvalidKeyException, SignatureException, NoSuchProviderException {
        BCECPublicKey publicKey = KeyGenerator.getPublicKey(pubKeyStr);
        return verify(publicKey,oriData,signStr);
    }

    public static boolean verify(PublicKey publicKey,String oriData,String signStr) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException, NoSuchProviderException {
        Signature signature = Signature.getInstance(SIGN_ALGORITHM,BouncyCastleProvider.PROVIDER_NAME);
        signature.initVerify(publicKey);
        signature.update(oriData.getBytes(StandardCharsets.UTF_8));
        return signature.verify(Base64.decode(signStr));
    }

}
