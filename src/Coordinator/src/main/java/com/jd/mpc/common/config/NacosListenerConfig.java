package com.jd.mpc.common.config;

import com.alibaba.nacos.api.exception.NacosException;
import com.google.common.collect.Maps;
import com.jd.mpc.common.constant.CommonConstant;
import com.jd.mpc.common.enums.TaskTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

//    @NacosInjected
//    private ConfigService configService;

    @Resource
    private MpcConfigService mpcConfigService;

    @Bean
    public Map<TaskTypeEnum, Properties> functorGroup() throws NacosException, IOException {
        Map<TaskTypeEnum,Properties> funtorMap = Maps.newConcurrentMap();
        try {
            for (TaskTypeEnum taskType : TaskTypeEnum.values()) {
                String dataId = taskType.getName() + ".properties";
                long timeout = 5000;
                String config = mpcConfigService.getConfig(dataId, CommonConstant.FUNCTOR_GROUP, timeout);
                if (config == null) {
                    log.warn("nacos default config of " + taskType.getName() + " is null!");
                    continue;
                }
                Properties properties = new Properties();
                properties.load(new StringReader(config));
                funtorMap.put(taskType, properties);
            }
        }catch (Exception e){
            log.info(e.getMessage());
        }
        return funtorMap;
    }

}
