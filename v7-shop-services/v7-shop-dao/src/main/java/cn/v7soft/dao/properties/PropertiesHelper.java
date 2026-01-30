package cn.v7soft.dao.properties;

import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
public class PropertiesHelper {
    private static MultimediaFileProperty multimediaFilePropertyHolder;

    public PropertiesHelper(MultimediaFileProperty multimediaFileProperty) {
        multimediaFilePropertyHolder = multimediaFileProperty;
    }

    public static MultimediaFileProperty getMultimediaFilePropertyHolder() {
        return multimediaFilePropertyHolder;
    }
}
