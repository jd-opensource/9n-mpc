package com.jd.mpc.common.config;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Scanner;

/**
 * @Author : chenghekai1
 * @Date : 2024/3/13 15:44
 * @Version : V1.0
 * @Description : 兼容nacos的ConfigService和读取本地配置文件功能
 */
@Slf4j
@Component
public class MpcConfigService {
    private static ConfigService nacosConfigService;
    @Value("${nacos.config.server-addr}")
    String serverAddr;
    @Value("${nacos.config.namespace}")
    String namespace;

    @PostConstruct
    public void init() {
        log.info("serverAddr=" + serverAddr + ", namespace=" + namespace);
        try {
            // 使用单例模式确保只初始化一次ConfigService
            if (serverAddr == null || !isNacosServerAvailable(serverAddr)) {
                log.info("nacos server=" + serverAddr + " can not use");
                nacosConfigService = null;
            } else {
                log.info("start 2");
                Properties properties = new Properties();
                log.info("start 3");
                properties.put("serverAddr", String.valueOf(serverAddr));
                log.info("start 4");
                properties.put("namespace", String.valueOf(namespace));
                log.info("configService init：" + properties);
                // 不会主动连接，而是实际获取配置时才会判断nacos服务是否可用
                nacosConfigService = NacosFactory.createConfigService(properties);
                log.info("configService: " + nacosConfigService);
            }
        } catch (Exception e) {
            // 处理异常
            log.info("configService error, " + e.getMessage());
            nacosConfigService = null;
        }
    }
    public String getConfig(String dataId, String groupId, long timeoutMs) throws Exception {
        if (nacosConfigService != null) {
            // 如果连接到Nacos，从Nacos获取配置
            log.info("nacos link succeed");
            return nacosConfigService.getConfig(dataId, groupId, timeoutMs);
        } else if ("APPLICATION_GROUP".equals(groupId)) {
            return readResourceFile(".", dataId);
        } else {
            // 否则，从本地属性获取配置
            return readResourceFile(groupId, dataId);
        }
    }

    public String readResourceFile(String groupId, String fileName) throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(groupId + "/" + fileName);

        if (inputStream == null) {
            throw new IOException("File not found: " + fileName);
        }

        try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8.name())) {
            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    /**
     * 判断nacos服务可用性
     *
     * @param serverAddr
     * @return
     */
    public boolean isNacosServerAvailable(String serverAddr) {
        try {
            String[] parts = serverAddr.split(":");
            String host = parts[0];
            // nacos默认端口8848
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 8848;
            // 使用InetAddress避免主机名解析异常
            InetAddress inetAddress = InetAddress.getByName(host);
            // 尝试连接服务，设置超时时间为1000毫秒
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(inetAddress, port), 1000);
                // 连接成功，服务可用
                return true;
            }
        } catch (IOException e) {
            // 连接失败，服务不可用
            return false;
        }
    }

}
