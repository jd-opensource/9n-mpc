package com.jd.mpc.common.util;

import lombok.extern.slf4j.Slf4j;

import java.util.Base64;

@Slf4j
public class JNISigner {
    // This declares that the static `hello` method will be provided
    // a native library.
    public static native byte[] sign(byte[] privateKey, byte[] message);

    public static native byte[] newPrivateKey();

    public static native byte[] publicKey(byte[] privateKey);

    static {
        // This actually loads the shared object that we'll be creating.
        // The actual location of the .so or .dll may differ based on your
        // platform.
        System.loadLibrary("jni_sign");
    }

    // The rest is just regular ol' Java!
    public static void gen() {
        byte[] msg = "dfasfadsad".getBytes();
        for (int i = 0; i < 1; i++) {
            // System.out.println(i);
            byte[] priv = newPrivateKey();
             log.info("java-private: " +
             Base64.getEncoder().encodeToString(priv));
            byte[] pub = publicKey(priv);
            log.info("java-public: " +
             Base64.getEncoder().encodeToString(pub));
            log.info("java-message: " +
             Base64.getEncoder().encodeToString(msg));
            byte[] sig = sign(priv, msg);
            log.info(Base64.getEncoder().encodeToString(sig));
        }
    }
}
