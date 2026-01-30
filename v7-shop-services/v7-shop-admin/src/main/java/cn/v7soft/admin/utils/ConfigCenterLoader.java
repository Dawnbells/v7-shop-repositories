package cn.v7soft.admin.utils;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.v7soft.dao.properties.ConfigCenterProperty;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ConfigCenterLoader {

    private static ConfigCenterProperty configCenterProperty;

    @Getter
    private static final Map<String, JSONObject> configCache = new ConcurrentHashMap<>();

    public ConfigCenterLoader(ConfigCenterProperty property) {
        configCenterProperty = property;
    }

    @PostConstruct
    public void init() {
        loadFromConfigCenter();
    }

    public static void loadFromConfigCenter() {
        try {
            // 加载部门配置
            loadConfig("email");
        } catch (Exception e) {
            log.error("加载配置中心配置失败", e);
        }
    }

    public static JSONObject loadConfig(String configName) {
        try {
            String url = getConfigUrl(configName);
            log.info("从配置中心加载配置: {}", url);
            String response = HttpUtil.get(url, 10000);
            JSONObject config = JSONUtil.parseObj(response);
            configCache.put(configName, config);
            log.info("配置 {} 加载成功", configName);
            return config;
        } catch (Exception e) {
            log.error("加载配置 {} 失败", configName, e);
            return null;
        }
    }

    public static JSONObject getConfig(String configName) {
        JSONObject config = configCache.get(configName);
        if (config == null) {
            config = loadConfig(configName);
        }
        return config;
    }

    public static String getConfigUrl(String configName) {
        String baseUrl = configCenterProperty != null ? configCenterProperty.getBaseUrl()
                : "https://dawnbells.github.io/v7-shop-config-center";
        return baseUrl + "/" + configName + ".config.json";
    }

    public static void refreshConfig(String configName) {
        loadConfig(configName);
    }

    public static void refreshAllConfigs() {
        loadFromConfigCenter();
    }
}
