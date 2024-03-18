package com.jd.mpc.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertiesPropertySource;

import javax.annotation.PostConstruct;
import java.io.StringReader;
import java.util.Properties;

/**
 * @Author : chenghekai1
 * @Date : 2024/3/13 15:20
 * @Version : V1.0
 * @Description : 初始化一个nacos ConfigService
 */
@Configuration
@Slf4j
public class NacosServiceInitConfig {
    @Value("${nacos.config.server-addr}")
    String serverAddr;
    @Value("${nacos.config.data-id}")
    String dataId;
    @Value("${nacos.config.group}")
    String group;
    @Value("${nacos.config.namespace}")
    String namespace;

    @Autowired
    private Environment environment;

    @Autowired
    private MpcConfigService mpcConfigService;

    @PostConstruct
    public void init() {
        try {
            String content = mpcConfigService.getConfig(dataId, group, 5000);
            Properties props = new Properties();
            props.load(new StringReader(content));
            PropertiesPropertySource propertySource = new PropertiesPropertySource(dataId, props);
            log.info("application.properties local.test=" + propertySource.getProperty("local.test"));
            ((ConfigurableEnvironment) environment).getPropertySources().addFirst(propertySource);
        } catch (Exception e) {
            log.error("init application.properties error " + e.getMessage());
        }
    }
}
