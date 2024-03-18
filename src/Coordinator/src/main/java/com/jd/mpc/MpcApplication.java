package com.jd.mpc;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

/**
 * 启动类
 *
 */

@SpringBootApplication
@MapperScan("com.jd.mpc.mapper")
@EnableRetry
//@EnableNacosConfig(globalProperties = @NacosProperties(serverAddr = "${nacos.config.server-addr}",namespace = "${nacos.config.namespace}"))
//@NacosPropertySource(dataId = "application.properties", groupId = "APPLICATION_GROUP", autoRefreshed = true, first = true)
public class MpcApplication {

    public static void main(String[] args) {
        System.setProperty("nacos.logging.default.config.enabled", "false");
        SpringApplication sa = new SpringApplication(MpcApplication.class);
        sa.setAllowCircularReferences(Boolean.TRUE);// 加入的参数
        sa.run(args);
    }

}
