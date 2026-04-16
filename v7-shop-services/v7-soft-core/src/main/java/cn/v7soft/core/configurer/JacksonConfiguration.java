package cn.v7soft.core.configurer;

import cn.v7soft.core.controller.deserializer.MultiFormatModule;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

@Configuration
public class JacksonConfiguration {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        // 设置全局时区为中国时区
        objectMapper.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        // 禁用时间戳的序列化，使用ISO-8601格式输出
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 设置全局的日期格式为 yyyy-MM-dd HH:mm:ss
        objectMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.setFilterProvider(new SimpleFilterProvider().setFailOnUnknownId(false));
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.registerModule(new MultiFormatModule());
        // 配置ObjectMapper忽略null值
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return objectMapper;
    }
}
