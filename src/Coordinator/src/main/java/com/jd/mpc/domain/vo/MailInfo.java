package com.jd.mpc.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 邮件信息
 *
 * 
 * @date 2022/1/5 10:36 上午
 */
@Data
public class MailInfo {

    /**
     * 内容
     */
    private String content;


    /**
     * 接收人
     */
    private List<String> mailTo;


    /**
     * 主题
     */
    private String subject;
}
