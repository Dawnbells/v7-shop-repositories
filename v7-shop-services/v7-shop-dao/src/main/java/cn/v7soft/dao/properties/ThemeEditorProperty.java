package cn.v7soft.dao.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "application.theme-editor")
public class ThemeEditorProperty {
    /**
     * 开发环境主题编辑器URL
     */
    private String devUrl = "http://127.0.0.1:3000/builder";
}
