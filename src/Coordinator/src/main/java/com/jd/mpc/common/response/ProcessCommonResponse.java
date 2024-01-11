package com.jd.mpc.common.response;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import java.io.Serializable;

@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProcessCommonResponse<T> implements Serializable {
    private Integer code;
    private String errMsg;
    private T result;
    /**
     * 重定向url
     */
    private String url;

    public ProcessCommonResponse() {
        this.code = ErrorStatus.SUCCESS;
    }

    public String getErrMsg() {
        return this.errMsg;
    }

    public T getResult() {
        return this.result;
    }

    public void setResult(T result) {
        this.result = result;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setError(Integer status, String errMsg) {
        this.code = status;
        this.errMsg = errMsg;
    }

    /**
     * @param t
     * @param <T>
     * @return
     */
    public static <T> ProcessCommonResponse<T> ok(T t) {
        ProcessCommonResponse<T> r = ok();
        r.setResult(t);
        return r;
    }

    public Integer getCode() {
        return code;
    }

    public static <T> ProcessCommonResponse<T> ok() {
        ProcessCommonResponse<T> r = new ProcessCommonResponse<>();
        r.code = ErrorStatus.SUCCESS;
        return r;
    }

    public static <T> ProcessCommonResponse<T> fail(String msg) {
        ProcessCommonResponse<T> r = new ProcessCommonResponse<>();
        r.code = ErrorStatus.BUSINESS_ERROR;
        r.errMsg = msg;
        return r;
    }

    public static <T> ProcessCommonResponse<T> fail(Integer code, String msg) {
        ProcessCommonResponse<T> r = new ProcessCommonResponse<>();
        r.code = code;
        r.errMsg = msg;
        return r;
    }

    public static void convertThrowException(ProcessCommonResponse commonResponse){
        if (!ErrorStatus.SUCCESS.equals(commonResponse.getCode())){
            throw new CommonException(commonResponse.getCode(), commonResponse.getErrMsg());
        }
    }

}
