package com.jd.mpc.common.util;


import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Util class for encrypt and decrypt the given content by AES , during which will encrypt and decrypt by JDBase64 for the second time.
 *
 */
@Slf4j
public final class EncryptUtils {

    private static final String MAGIC_CODE = "JD BDP ";
    /**
     * keyword of AES
     */
    private static String internalKeyword;

    /**
     * encrypt the content by AES.
     * @param content content to encrypt
     * @return encrypted string by AES and JDBase64
     */
    public static final String encrypt(final String content) {
        try {
            if (isNull(content)) {
                return "";     //no encrypt when empty content
            }
            if (isNull(internalKeyword)) {
                log.error("keyword not setup yet..");
                throw new RuntimeException("keyword not setup yet..");
            }
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG" );
            secureRandom.setSeed(internalKeyword.getBytes());
            keyGenerator.init(128,secureRandom);
            SecretKey secretKey = keyGenerator.generateKey();
            byte[] encodedFormat = secretKey.getEncoded();
            SecretKeySpec secretKeySpec = new SecretKeySpec(encodedFormat, "AES");
            Cipher cipher = Cipher.getInstance("AES");
            byte[] byteContent = content.getBytes("utf-8");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
            byte[] result = cipher.doFinal(byteContent);
            return Base64Util.base64Encode(result); //encrypt for the second time by our own base64.
        } catch (Throwable e) {
            log.error("error occurs in encrypt content : " + content);
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }


    private static final boolean isNull(final String str) {
        return str == null || str.trim().length() == 0;
    }

    public static final void setup(final String keyword) {
        if (isNull(keyword)) {
            log.error("keyword is null!");
            throw new RuntimeException("keyword is null!");
        }
        if (internalKeyword == null) {
            internalKeyword = magic(keyword);
            log.info("keyword setup already..");
        }
    }

    private static final String magic(final String keyword) {
        final int middle = (keyword.length() + 1) / 2;
        return MAGIC_CODE + keyword.substring(0, middle);
    }

    /**
     * inner class , Base64 implementation of jd.
     * encode and decode the content in JDBase64.
     */
    private static final class Base64Util {

        private static final int RANGE = 0xff;
        private static final byte[] StrToBase64Byte = new byte[128];
        private static final char[] Base64ByteToStr = new char[] { 'K', 'L', 'M',
                'N', 'O', 'P', 'Q', 'R', 'S', 'T',// 0 ~ 9
                'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',// 10 ~ 19
                'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd',// 20 ~ 29
                'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x',// 30 ~ 39
                'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',// 40 ~ 49
                'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7',// 50 ~ 59
                '8', '9', '+', '/' // 60 ~ 63
        };

        static {
            for (int i = 0; i <= StrToBase64Byte.length - 1; i++) {
                StrToBase64Byte[i] = -1;
            }
            for (int i = 0; i <= Base64ByteToStr.length - 1; i++) {
                StrToBase64Byte[Base64ByteToStr[i]] = (byte) i;
            }
        }


        public static final String base64Encode(final byte[] bytes) {
            StringBuilder res = new StringBuilder();
            // per 3 bytes scan and switch to 4 bytes
            for (int i = 0; i <= bytes.length - 1; i += 3) {
                byte[] enBytes = new byte[4];
                // save the right move bit to next position's bit 3 bytes to 4 bytes
                byte tmp = (byte) 0x00;
                for (int k = 0; k <= 2; k++) {// 0 ~ 2 is a line
                    if ((i + k) <= bytes.length - 1) {
                        // note , we only get 0 ~ 127 ???
                        enBytes[k] = (byte) (((((int) bytes[i + k] & RANGE) >>> (2 + 2 * k))) | (int) tmp);
                        tmp = (byte) (((((int) bytes[i + k] & RANGE) << (2 + 2 * (2 - k))) & RANGE) >>> 2);
                    } else {
                        enBytes[k] = tmp;
                        // if tmp > 64 then the char is '=' hen '=' -> byte is -1 ,
                        // so it is EOF or not print char
                        tmp = (byte) 64;
                    }
                }
                // forth byte 4 bytes to encode string
                enBytes[3] = tmp;
                for (int k = 0; k <= 3; k++) {
                    if ((int) enBytes[k] <= 63) {
                        res.append(Base64ByteToStr[(int) enBytes[k]]);
                    } else {
                        res.append('=');
                    }
                }
            }
            return res.toString();
        }

        public static final byte[] Base64Decode(final String val) {
            // destination bytes, valid string that we want
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] srcBytes = val.getBytes();
            byte[] base64bytes = new byte[srcBytes.length];
            // get the base64 bytes (the value is -1 or 0 ~ 63)
            for (int i = 0; i <= srcBytes.length - 1; i++) {
                int ind = (int) srcBytes[i];
                base64bytes[i] = StrToBase64Byte[ind];
            }
            // base64 bytes (4 bytes) to normal bytes (3 bytes)
            for (int i = 0; i <= base64bytes.length - 1; i += 4) {
                byte[] deBytes = new byte[3];
                // if basebytes[i] = -1, then debytes not append this value
                int delen = 0;
                byte tmp;
                for (int k = 0; k <= 2; k++) {
                    if ((i + k + 1) <= base64bytes.length - 1
                            && base64bytes[i + k + 1] >= 0) {
                        tmp = (byte) (((int) base64bytes[i + k + 1] & RANGE) >>> (2 + 2 * (2 - (k + 1))));
                        deBytes[k] = (byte) ((((int) base64bytes[i + k] & RANGE) << (2 + 2 * k) & RANGE) | (int) tmp);
                        delen++;
                    }
                }
                for (int k = 0; k <= delen - 1; k++) {
                    bos.write((int) deBytes[k]);
                }
            }
            return bos.toByteArray();
        }

    }



}
