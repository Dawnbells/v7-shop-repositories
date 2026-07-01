package cn.v7soft.admin.task.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import cn.v7soft.admin.controller.req.TranslateByAIRequest;
import cn.v7soft.admin.controller.req.TurboFlowBridgePollRequest;
import cn.v7soft.admin.controller.resp.TurboFlowBridgeTaskResponse;
import cn.v7soft.admin.service.IAiAccountService;
import cn.v7soft.admin.service.ILanguageService;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.admin.task.AiAccountTranslateSubTask;
import cn.v7soft.dao.entities.primary.AiAccount;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.enums.AiProvider;
import cn.v7soft.dao.repositories.primary.AiTokenUsageRecordRepository;
import cn.v7soft.dao.repositories.primary.ImageTranslationCacheRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TurboFlowBridgeProviderTest {

    @Mock private IAiAccountService aiAccountService;
    @Mock private IMultimediaFileService multimediaFileService;
    @Mock private ILanguageService languageService;
    @Mock private ImageTranslationCacheRepository imageTranslationCacheRepository;
    @Mock private AiTokenUsageRecordRepository usageRecordRepository;
    @Mock private TranslateProviderCallback callback;

    @Test
    void pollTaskReturnsRetryImageBeforeAlreadyBufferedNormalImage() {
        TurboFlowBridgeProvider provider = new TurboFlowBridgeProvider(
                aiAccountService,
                multimediaFileService,
                languageService,
                imageTranslationCacheRepository,
                usageRecordRepository,
                null,
                null,
                null);
        provider.setCallback(callback);

        AiAccount account = AiAccount.builder()
                .id(7L)
                .provider(AiProvider.TURBOFLOW_GEMINI)
                .model("gemini-image")
                .build();
        Language language = Language.builder()
                .id(1L)
                .name("Polski")
                .code("pl")
                .build();
        MultimediaFile retryImage = image(202L);

        when(aiAccountService.findAvailableAccountsByApiKey(AiProvider.TURBOFLOW_GEMINI, "token"))
                .thenReturn(List.of(account));
        when(callback.isTaskActive(anyLong())).thenReturn(true);
        when(multimediaFileService.getById(202L)).thenReturn(retryImage);
        when(multimediaFileService.download(eq("202"), eq(0)))
                .thenReturn(new ByteArrayInputStream(new byte[] {4, 5, 6}));
        when(imageTranslationCacheRepository.findByImageHashAndLanguageId(anyString(), eq(1L)))
                .thenReturn(Optional.empty());
        when(languageService.getById(1L)).thenReturn(language);

        AiAccountTranslateSubTask normalSubTask = imageSubTask(11L, "101");
        AiAccountTranslateSubTask retrySubTask = imageSubTask(22L, "202");
        retrySubTask.dispatch("bridge-a", "old-assignment", LocalDateTime.now().plusMinutes(1));
        retrySubTask.retry("previous TurboFlow failure");

        provider.executeSubTask(normalSubTask);
        provider.executeSubTask(retrySubTask);

        TurboFlowBridgePollRequest pollRequest = new TurboFlowBridgePollRequest();
        pollRequest.setBridgeId("bridge-a");

        TurboFlowBridgeTaskResponse response = provider.pollTask("token", pollRequest);

        assertTrue(response.isHasTask());
        assertEquals(retrySubTask.getSubTaskId(), response.getSubTaskId());
    }

    private AiAccountTranslateSubTask imageSubTask(Long taskId, String imageId) {
        TranslateByAIRequest request = new TranslateByAIRequest();
        request.setProductId("1");
        request.setCountryId("1");
        request.setLanguageId("1");
        request.setAiAccountId("7");
        return AiAccountTranslateSubTask.image(taskId, imageId, request);
    }

    private MultimediaFile image(Long id) {
        return MultimediaFile.builder()
                .id(id)
                .name("image-" + id)
                .suffix("png")
                .width(512)
                .height(512)
                .relativePath("/images/" + id + ".png")
                .build();
    }
}
