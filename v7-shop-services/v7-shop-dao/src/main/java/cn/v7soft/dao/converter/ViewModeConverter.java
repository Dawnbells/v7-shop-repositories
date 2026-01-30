package cn.v7soft.dao.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import cn.v7soft.dao.enums.ViewMode;

@Component
public class ViewModeConverter implements Converter<String, ViewMode> {
    @Override
    public ViewMode convert(String source) {
        return ViewMode.valueOf(source.toUpperCase());
    }
}