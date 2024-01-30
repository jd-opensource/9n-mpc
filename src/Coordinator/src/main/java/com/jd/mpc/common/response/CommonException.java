package com.jd.mpc.common.response;


public class CommonException extends RuntimeException {
    private final Integer status;
    private Object[] params;

    public CommonException(String msg) {
        super(msg);
        this.status = ErrorStatus.BUSINESS_ERROR;
        this.params = null;
    }

    public CommonException(Integer code, String msg) {
        super(msg);
        this.status = code;
        this.params = null;
    }

    public CommonException(Integer code, String msg, Exception e) {
        super(msg, e);
        this.status = code;
        this.params = null;
    }

    public CommonException(Integer code, String msg, Throwable t) {
        super(msg, t);
        this.status = code;
        this.params = null;
    }

    public Integer getStatus() {
        return this.status;
    }

    public Object[] getParams() {
        return this.params;
    }

    public void setParams(Object[] params) {
        this.params = params;
    }
}
