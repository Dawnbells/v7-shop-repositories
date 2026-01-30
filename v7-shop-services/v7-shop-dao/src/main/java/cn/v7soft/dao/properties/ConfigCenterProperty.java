package cn.v7soft.dao.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "application.config-center")
public class ConfigCenterProperty {
    /**
     * 配置中心基础URL
     */
    private String baseUrl = "https://dawnbells.github.io/v7-shop-config-center";
}

