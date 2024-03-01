package com.jd.mpc;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.annotation.NacosProperties;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.spring.context.annotation.EnableNacos;
import com.alibaba.nacos.spring.context.annotation.config.EnableNacosConfig;
import com.alibaba.nacos.spring.context.annotation.config.NacosPropertySource;
import io.camunda.zeebe.spring.client.EnableZeebeClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.HashMap;
import java.util.Properties;

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
@EnableNacosConfig(globalProperties = @NacosProperties(serverAddr = "${nacos.config.server-addr}",namespace = "${nacos.config.namespace}"))
@NacosPropertySource(dataId = "application.properties", groupId = "APPLICATION_GROUP", autoRefreshed = true, first = true)
public class MpcApplication {

    public static void main(String[] args) {
        System.setProperty("nacos.logging.default.config.enabled", "false");
        SpringApplication sa = new SpringApplication(MpcApplication.class);
        sa.setAllowCircularReferences(Boolean.TRUE);// 加入的参数
        sa.run(args);
    }

}
