package com.jd.mpc.common.response;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import java.io.Serializable;

@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CommonResponse<T> implements Serializable {
    private Integer status;
    private String errMsg;
    private T result;
    /**
     * 重定向url
     */
    private String url;

    public CommonResponse() {
        this.status = ErrorStatus.SUCCESS;
    }

    public Integer getStatus() {
        return this.status;
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
        this.status = status;
        this.errMsg = errMsg;
    }

    /**
     * @param t
     * @param <T>
     * @return
     */
    public static <T> CommonResponse<T> ok(T t) {
        CommonResponse<T> r = ok();
        r.setResult(t);
        return r;
    }

    public static <T> CommonResponse<T> ok() {
        CommonResponse<T> r = new CommonResponse<>();
        r.status = ErrorStatus.SUCCESS;
        return r;
    }

    public static <T> CommonResponse<T> fail(String msg) {
        CommonResponse<T> r = new CommonResponse<>();
        r.status = ErrorStatus.BUSINESS_ERROR;
        r.errMsg = msg;
        return r;
    }

    public static <T> CommonResponse<T> fail(Integer code, String msg) {
        CommonResponse<T> r = new CommonResponse<>();
        r.status = code;
        r.errMsg = msg;
        return r;
    }

    public static void convertThrowException(CommonResponse commonResponse){
        if (!ErrorStatus.SUCCESS.equals(commonResponse.getStatus())){
            throw new CommonException(commonResponse.getStatus(), commonResponse.getErrMsg());
        }
    }

}
