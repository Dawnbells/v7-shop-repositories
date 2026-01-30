package cn.v7soft.admin.utils;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class EnvironmentHelper {

    private static Environment environment;

    private final Environment env;

    public EnvironmentHelper(Environment env) {
        this.env = env;
    }

    @PostConstruct
    public void init() {
        environment = this.env;
    }

    public static String getProperty(String key) {
        return environment.getProperty(key);
    }
}
