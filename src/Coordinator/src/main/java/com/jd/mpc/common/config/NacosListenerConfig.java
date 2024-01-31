package com.jd.mpc.common.config;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.annotation.NacosInjected;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.annotation.NacosConfigListener;
import com.alibaba.nacos.api.config.convert.NacosConfigConverter;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.spring.context.event.config.TimeoutNacosConfigListener;
import com.google.common.collect.Maps;
import com.jd.mpc.common.constant.CommonConstant;
import com.jd.mpc.common.enums.TaskTypeEnum;
import com.jd.mpc.common.response.CommonException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ReflectionUtils;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.Properties;

/**
 * @Description: TODO
 * 
 * @Date: 2022/12/23
 */
@Slf4j
@Configuration
public class NacosListenerConfig {

    @NacosInjected
    private ConfigService configService;

    @Bean
    public Map<TaskTypeEnum, Properties> functorGroup() throws NacosException, IOException {
        Map<TaskTypeEnum,Properties> funtorMap = Maps.newConcurrentMap();
        try {
            for (TaskTypeEnum taskType : TaskTypeEnum.values()) {
                String dataId = taskType.getName() + ".properties";
                long timeout = 5000;
                String config = configService.getConfig(dataId, CommonConstant.FUNCTOR_GROUP, timeout);
                if (config == null) {
                    log.warn("nacos default config of " + taskType.getName() + " is null!");
                    continue;
                }
                Properties properties = new Properties();
                properties.load(new StringReader(config));
                funtorMap.put(taskType, properties);
                configService.addListener(dataId, CommonConstant.FUNCTOR_GROUP, new TimeoutNacosConfigListener(dataId, CommonConstant.FUNCTOR_GROUP, timeout) {
                    @Override
                    protected void onReceived(String config) {
                        try {
                            Properties properties = new Properties();
                            properties.load(new StringReader(config));
                            funtorMap.put(taskType, properties);
                        } catch (Exception e) {
                        }
                    }
                });
            }
        }catch (Exception e){

        }
        return funtorMap;
    }

}
