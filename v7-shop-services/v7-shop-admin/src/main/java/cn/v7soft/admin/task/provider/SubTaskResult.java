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

    private final int actualPromptTokens;
    private final int actualCompletionTokens;
    private final int actualThinkingTokens;
    private final int businessPromptTokens;
    private final int businessCompletionTokens;
    private final int businessThinkingTokens;
    private final int businessCredits;

    private final boolean cacheHit;
}
