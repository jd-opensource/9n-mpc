package com.jd.mpc.aces;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


/**
 */
@Component
@Slf4j
public class TdeService {

    /**
     * 加密
     * @param input
     * @return
     */
    public String encryptString(String input){
            return input;
    }

    /**
     * 解密
     * @param input
     * @return
     */
    public String decryptString(String input){
            return input;
    }
}
