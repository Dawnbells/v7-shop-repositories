package cn.v7soft.admin.task.provider;

import cn.v7soft.dao.entities.primary.MultimediaFile;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SubTaskResult {

    private final MultimediaFile translatedFile;
    private final String translatedText;
    private final String translatedHtml;
    private final Long elapsedMs;
    private final String resultMimeType;
}
