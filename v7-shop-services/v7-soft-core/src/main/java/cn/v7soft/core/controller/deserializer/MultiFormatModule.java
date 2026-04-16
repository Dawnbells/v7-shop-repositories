package cn.v7soft.core.controller.deserializer;

import com.fasterxml.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;

public class MultiFormatModule extends SimpleModule {
    public MultiFormatModule() {
        addDeserializer(LocalDateTime.class, new MultiFormatLocalDateTimeDeserializer());
    }
}
