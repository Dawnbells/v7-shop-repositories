package cn.v7soft.common.forest.config;

import com.dtflys.forest.converter.json.ForestJacksonConverter;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class CustomForestJacksonConverter extends ForestJacksonConverter {
    public CustomForestJacksonConverter() {
        super();
        mapper.registerModule(new JavaTimeModule());
    }

}
