package cn.v7soft.dao.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "application.cloak")
public class CloakCenterProperty {
    /**
     * 配置中心基础URL
     */
    private String baseUrl = "https://cloak.xmskyai.com";
}

