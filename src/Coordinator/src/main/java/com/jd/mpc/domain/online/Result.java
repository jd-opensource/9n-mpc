package com.jd.mpc.domain.online;

import lombok.Data;

/**
 * 返回结果【实时】
 *
 * @author luoyuyufei1
 * @date 2021/11/8 6:25 下午
 */
@Data
public class Result {
    /**
     * 状态
     */
    private Integer status;

    /**
     * 结果
     */
    private String result;
}
