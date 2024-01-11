package com.jd.mpc.cert;

import cn.hutool.crypto.SecureUtil;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.bouncycastle.util.io.pem.PemWriter;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.*;

/**
 * @author yezhenyue1
 * @date 2022-03-31 18:52
 */
public class KeyGenerator {
    private static final String KEY_ALGORITHM = "ECDSA";
    private static final String PARAM_SPEC = "prime256v1";
    private static final String PRIVATE_KEY_DESC = "PRIVATE KEY";
    private static final String PUBLIC_KEY_DESC = "PUBLIC KEY";

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * 生成秘钥对方法，通过ACES加密存储，用于生成根证书
     * @return
     * @throws NoSuchAlgorithmException
     */
    public static KeyPairPojo genRSAKeyPair() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, IOException {
        SecureRandom random = new SecureRandom();
        ECParameterSpec ecSpec = ECNamedCurveTable.getParameterSpec(PARAM_SPEC);
        //获得对象 KeyPairGenerator 参数 RSA 2048
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance(KEY_ALGORITHM);
        keyPairGen.initialize(ecSpec,random);
        //通过对象 KeyPairGenerator 获取对象KeyPair
        KeyPair keyPair = keyPairGen.generateKeyPair();
        //通过对象 KeyPair 获取RSA公私钥对象RSAPublicKey RSAPrivateKey
        BCECPublicKey publicKey = (BCECPublicKey) keyPair.getPublic();
        BCECPrivateKey privateKey = (BCECPrivateKey) keyPair.getPrivate();
        return new KeyPairPojo(key2Str(PUBLIC_KEY_DESC,publicKey),key2Str(PRIVATE_KEY_DESC,privateKey),null);
    }

    private static String key2Str(String desc,Key key) throws IOException {
        StringWriter strWriter = new StringWriter();
        PemWriter writer = new PemWriter(strWriter);
        writer.writeObject(new PemObject(desc,key.getEncoded()));
        writer.flush();
        writer.close();
        return strWriter.toString();
    }

    /**
     *  封装对应的私钥通过下列参数
     * @param radix 参数进制
     * @param publicModulusStr 公钥 modulus
     * @param privateExponentStr 私钥 expoent
     * @return
     * @throws InvalidKeySpecException
     * @throws NoSuchAlgorithmException
     */
    public static PrivateKey getPrivateKey(int radix, String publicModulusStr, String privateExponentStr) throws InvalidKeySpecException, NoSuchAlgorithmException {
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        BigInteger privateModulus = new BigInteger(publicModulusStr, radix);
        BigInteger privateExponent = new BigInteger(privateExponentStr, radix);
        RSAPrivateKeySpec rsaPrivateKeySpec = new RSAPrivateKeySpec(privateModulus,privateExponent);
        return keyFactory.generatePrivate(rsaPrivateKeySpec);
    }

    /**
     *  封装对应的公钥通过下列参数
     * @param radix 参数进制
     * @param publicModulusStr 公钥modulus
     * @param publicExponentStr 公钥exponent
     * @return
     * @throws InvalidKeySpecException
     * @throws NoSuchAlgorithmException
     */
    public static PublicKey getPublicKey(int radix, String publicModulusStr, String publicExponentStr) throws InvalidKeySpecException, NoSuchAlgorithmException {
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        BigInteger publicModulus = new BigInteger(publicModulusStr, radix);
        BigInteger publicExponent = new BigInteger(publicExponentStr, radix);
        RSAPublicKeySpec keySpecPublic = new RSAPublicKeySpec( publicModulus, publicExponent) ;
        return keyFactory.generatePublic(keySpecPublic);
    }


    /**
     *  封装对应的公钥通过下列参数
     * @param privateStr 公钥exponent
     * @return
     */
    public static BCECPrivateKey getPrivateKey(String privateStr) throws IOException {
        PemReader reader = new PemReader(new StringReader(privateStr));
        PemObject pemObject = reader.readPemObject();
        return (BCECPrivateKey)SecureUtil.generatePrivateKey(KEY_ALGORITHM,pemObject.getContent());
    }

    /**
     *  封装对应的公钥通过下列参数
     * @param publicStr 公钥exponent
     * @return
     */
    public static BCECPublicKey getPublicKey(String publicStr) throws IOException {
        PemReader reader = new PemReader(new StringReader(publicStr));
        PemObject pemObject = reader.readPemObject();
        return (BCECPublicKey)SecureUtil.generatePublicKey(KEY_ALGORITHM,pemObject.getContent());
    }

//    public static void main(String[] args) throws Exception {
//        KeyPairPojo keyPairPojo = genRSAKeyPair();
//        System.out.println(GsonUtil.createGsonString(keyPairPojo));
//        System.out.println(getPrivateKey(keyPairPojo.getPrivateExponent()));
////        PrivateKey privateKey = getPrivateKey(16, keyPairPojo.getModulus(), keyPairPojo.getPrivateExponent());
////        KeyStore store = KeyStore.getInstance("PKCS12");
////        store.load(null, null);
////        store.setKeyEntry("", privateKey,
////                "123456".toCharArray(), new Certificate[] {});
////        FileOutputStream fout =new FileOutputStream("/Users/yezhenyue1/workspace/abcTest.key");
////        store.store(fout, "123456".toCharArray());
////        fout.close();
//    }
}
