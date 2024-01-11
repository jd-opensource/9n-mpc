package com.jd.mpc.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @Description: grpc调用异常处理
 * @Author: feiguodong
 * @Date: 2022/3/29
 */
@Slf4j
@Component("grpcRetryExceptionHandler")
public class GrpcRetryExceptionHandler {

    public boolean shouldRetry(Throwable t){
        if (t instanceof StatusRuntimeException){
            StatusRuntimeException statusRuntimeException = (StatusRuntimeException) t;
            boolean flag = statusRuntimeException.getStatus().getCode().equals(Status.UNAVAILABLE.getCode());
            if (flag){
                //打印重试的类和方法
                StackTraceElement element = null;
                for (StackTraceElement stackTraceElement : statusRuntimeException.getStackTrace()) {
                    if (stackTraceElement.getClassName().equals("com.jd.mpc.grpc.GrpcOfflineClient") && !stackTraceElement.getMethodName().contains("$")){
                        element = stackTraceElement;
                        break;
                    }
                }
                if (element == null){
                    log.error("retry-method:unknown");
                }else {
                    log.error("retry-method:"+element.getMethodName());
                }
            }
            return flag;
        }
        return false;
    }
}
