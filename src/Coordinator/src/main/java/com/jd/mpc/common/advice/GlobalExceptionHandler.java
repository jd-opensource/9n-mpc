package com.jd.mpc.common.advice;

import java.util.List;

import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.HandlerMethod;

import com.jd.mpc.common.response.CommonException;
import com.jd.mpc.common.response.CommonResponse;
import com.jd.mpc.common.response.ErrorStatus;

import lombok.extern.slf4j.Slf4j;

/**
 * 全局异常处理，该类处理时候注意考虑某些场景下新老版本兼容。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    /**
     * 系统异常文案。
     */
    private static final String SYSTEM_ERROR_TEXT = "请求错误，请稍后重试";

    /**
     * 参数校验失败默认文案。
     */
    private static final String PARAMETER_VALIDATE_ERROR = "参数校验失败，请检查之后重试";

    /**
     * 处理业务中触发的异常。
     *
     * @param e
     * @return
     */
    @ExceptionHandler(value = CommonException.class)
    public Object handleException(CommonException e, HandlerMethod method) {
        log.error("处理业务异常", e);

        return buildResponse(method, e.getStatus(), e.getMessage());
    }

    /**
     * 处理通用异常。
     *
     * @return
     */
    @ExceptionHandler(value = BindException.class)
    public Object handleException(BindException bindException, HandlerMethod method) {
        log.error("处理BindException异常", bindException);
        List<ObjectError> allErrors = bindException.getAllErrors();
        for (ObjectError error : allErrors) {
            return buildResponse(method, ErrorStatus.PARAMETER_ERROR, error.getDefaultMessage());
        }
        return buildResponse(method, ErrorStatus.BUSINESS_ERROR, SYSTEM_ERROR_TEXT);
    }

    /**
     * 处理通用异常。
     *
     * @return
     */
    @ExceptionHandler(value = Exception.class)
    public Object handleException(Exception e, HandlerMethod method) {
        log.error("处理通用异常", e);

        return buildResponse(method, ErrorStatus.BUSINESS_ERROR, e.getMessage());
    }

    /**
     * 构建异常返回的数据格式。
     *
     * @param method
     * @param status
     * @param message
     * @return
     */
    private CommonResponse buildResponse(HandlerMethod method, Integer status, String message) {
        CommonResponse<Void> response = new CommonResponse<>();
        response.setError(status, message);
        return response;
    }
}
