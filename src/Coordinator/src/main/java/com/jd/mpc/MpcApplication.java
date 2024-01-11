package com.jd.mpc;

import com.alibaba.nacos.spring.context.annotation.config.NacosPropertySource;
import io.camunda.zeebe.spring.client.EnableZeebeClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.HashMap;

import static org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;
import static org.springframework.core.env.StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME;

/**
 * 启动类
 *
 * @author luoyuyufei1
 * @date 2021/9/22 2:21 下午
 */

@SpringBootApplication
@MapperScan("com.jd.mpc.mapper")
@EnableRetry
@EnableZeebeClient
@NacosPropertySource(dataId = "${nacos.config.data-id}", groupId = "${nacos.config.group}", autoRefreshed = true, first = true)
public class MpcApplication {

    public static void main(String[] args) {
        SpringApplication.run(MpcApplication.class, args);
    }

}
