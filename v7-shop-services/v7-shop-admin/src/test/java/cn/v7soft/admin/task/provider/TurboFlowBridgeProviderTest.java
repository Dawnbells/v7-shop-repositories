package cn.v7soft.admin.task.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import cn.v7soft.admin.controller.req.TurboFlowBridgeCompleteRequest;
import cn.v7soft.admin.controller.req.TranslateByAIRequest;
import cn.v7soft.admin.controller.req.TurboFlowBridgePollRequest;
import cn.v7soft.admin.controller.resp.TurboFlowBridgeTaskResponse;
import cn.v7soft.admin.service.IAiAccountService;
import cn.v7soft.admin.service.ILanguageService;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.admin.task.AiAccountTranslateSubTask;
import cn.v7soft.admin.utils.TokenCostCalculator;
import cn.v7soft.dao.entities.primary.ImagePolicyCache;
import cn.v7soft.dao.entities.primary.ImageTranslationCache;
import cn.v7soft.dao.entities.primary.AiAccount;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.enums.AiBillingPriceUnit;
import cn.v7soft.dao.enums.AiProvider;
import cn.v7soft.dao.repositories.primary.AiTokenUsageRecordRepository;
import cn.v7soft.dao.repositories.primary.ImagePolicyCacheRepository;
import cn.v7soft.dao.repositories.primary.ImageTranslationCacheRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class TurboFlowBridgeProviderTest {

    @Mock private IAiAccountService aiAccountService;
    @Mock private IMultimediaFileService multimediaFileService;
    @Mock private ILanguageService languageService;
    @Mock private ImagePolicyCacheRepository imagePolicyCacheRepository;
    @Mock private ImageTranslationCacheRepository imageTranslationCacheRepository;
    @Mock private AiTokenUsageRecordRepository usageRecordRepository;
    @Mock private TranslateProviderCallback callback;

    @Test
    void pollTaskReturnsRetryImageBeforeAlreadyBufferedNormalImage() {
        TurboFlowBridgeProvider provider = provider();

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

    @Test
    void completePolicyFallbackKeepsOriginalAndChargesLikeCompletedImage() throws Exception {
        TurboFlowBridgeProvider provider = provider();
        AiAccount account = billedAccount();
        Language language = language(1L, "Polski");
        MultimediaFile sourceImage = image(303L);

        when(aiAccountService.findAvailableAccountsByApiKey(AiProvider.TURBOFLOW_GEMINI, "token"))
                .thenReturn(List.of(account));
        when(callback.isTaskActive(anyLong())).thenReturn(true);
        when(multimediaFileService.getById(303L)).thenReturn(sourceImage);
        when(multimediaFileService.download(eq("303"), eq(0)))
                .thenReturn(new ByteArrayInputStream(new byte[] {7, 8, 9}));
        when(imageTranslationCacheRepository.findByImageHashAndLanguageId(anyString(), eq(1L)))
                .thenReturn(Optional.empty());
        when(imagePolicyCacheRepository.findByImageHash(anyString())).thenReturn(Optional.empty());
        when(languageService.getById(1L)).thenReturn(language);

        AiAccountTranslateSubTask subTask = imageSubTask(33L, "303");
        provider.executeSubTask(subTask);
        TurboFlowBridgePollRequest pollRequest = new TurboFlowBridgePollRequest();
        pollRequest.setBridgeId("bridge-a");
        TurboFlowBridgeTaskResponse polled = provider.pollTask("token", pollRequest);

        TurboFlowBridgeCompleteRequest request = new TurboFlowBridgeCompleteRequest();
        request.setBridgeId("bridge-a");
        request.setAssignmentId(polled.getAssignmentId());
        request.setPolicyFallback(true);
        request.setPolicyFallbackStatus("INVALID_ARGUMENT");
        request.setPolicyFallbackReason("PUBLIC_ERROR_SEXUAL_UPLOAD");
        request.setImageHash(cn.hutool.crypto.digest.DigestUtil.sha256Hex(new byte[] {7, 8, 9}));
        request.setElapsedMs(1200L);
        provider.completeTask("token", request);

        ArgumentCaptor<SubTaskResult> resultCaptor = ArgumentCaptor.forClass(SubTaskResult.class);
        verify(callback).onSubTaskCompleted(eq(subTask), resultCaptor.capture());
        SubTaskResult result = resultCaptor.getValue();
        assertNull(result.getTranslatedFile());
        assertEquals("PUBLIC_ERROR_SEXUAL_UPLOAD", result.getPolicyFallbackReason());
        assertEquals(result.getBusinessPromptTokens(), result.getActualPromptTokens());
        assertEquals(result.getBusinessCompletionTokens(), result.getActualCompletionTokens());
        assertTrue(result.getBusinessCredits() > 0);
        verify(multimediaFileService, never()).saveTranslatedImage(any(), anyString(), any());

        ArgumentCaptor<ImagePolicyCache> cacheCaptor = ArgumentCaptor.forClass(ImagePolicyCache.class);
        verify(imagePolicyCacheRepository).save(cacheCaptor.capture());
        assertEquals("PUBLIC_ERROR_SEXUAL_UPLOAD", cacheCaptor.getValue().getReason());
        assertSame(sourceImage, cacheCaptor.getValue().getSourceFile());
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> provider.completeTask("token", request));

    }

    @Test
    void exactLanguageTranslationCacheWinsOverImagePolicyCache() {
        TurboFlowBridgeProvider provider = provider();
        AiAccount account = billedAccount();
        Language language = language(1L, "Polski");
        MultimediaFile sourceImage = image(404L);
        MultimediaFile translatedImage = image(405L);
        ImageTranslationCache translationCache = ImageTranslationCache.builder()
                .imageHash("hash")
                .sourceFile(sourceImage)
                .language(language)
                .translatedFile(translatedImage)
                .skipped(false)
                .build();

        when(aiAccountService.findAvailableAccountsByApiKey(AiProvider.TURBOFLOW_GEMINI, "token"))
                .thenReturn(List.of(account));
        when(callback.isTaskActive(anyLong())).thenReturn(true);
        when(multimediaFileService.getById(404L)).thenReturn(sourceImage);
        when(multimediaFileService.download(eq("404"), eq(0)))
                .thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        when(imageTranslationCacheRepository.findByImageHashAndLanguageId(anyString(), eq(1L)))
                .thenReturn(Optional.of(translationCache));
        when(languageService.getById(1L)).thenReturn(language);

        AiAccountTranslateSubTask subTask = imageSubTask(44L, "404");
        provider.executeSubTask(subTask);
        TurboFlowBridgePollRequest pollRequest = new TurboFlowBridgePollRequest();
        pollRequest.setBridgeId("bridge-a");
        TurboFlowBridgeTaskResponse response = provider.pollTask("token", pollRequest);

        assertFalse(response.isHasTask());
        ArgumentCaptor<SubTaskResult> resultCaptor = ArgumentCaptor.forClass(SubTaskResult.class);
        verify(callback).onSubTaskCompleted(eq(subTask), resultCaptor.capture());
        assertSame(translatedImage, resultCaptor.getValue().getTranslatedFile());
        assertNull(resultCaptor.getValue().getPolicyFallbackReason());
        verify(imagePolicyCacheRepository, never()).findByImageHash(anyString());
    }

    @Test
    void imagePolicyCacheAppliesWhenTargetLanguageHasNoTranslationCache() {
        TurboFlowBridgeProvider provider = provider();
        AiAccount account = billedAccount();
        Language language = language(2L, "Deutsch");
        MultimediaFile sourceImage = image(505L);
        ImagePolicyCache policyCache = ImagePolicyCache.builder()
                .imageHash("hash")
                .sourceFile(sourceImage)
                .apiStatus("INVALID_ARGUMENT")
                .reason("PUBLIC_ERROR_SEXUAL_UPLOAD")
                .build();

        when(aiAccountService.findAvailableAccountsByApiKey(AiProvider.TURBOFLOW_GEMINI, "token"))
                .thenReturn(List.of(account));
        when(callback.isTaskActive(anyLong())).thenReturn(true);
        when(multimediaFileService.getById(505L)).thenReturn(sourceImage);
        when(multimediaFileService.download(eq("505"), eq(0)))
                .thenReturn(new ByteArrayInputStream(new byte[] {9, 8, 7}));
        when(imageTranslationCacheRepository.findByImageHashAndLanguageId(anyString(), eq(1L)))
                .thenReturn(Optional.empty());
        when(imagePolicyCacheRepository.findByImageHash(anyString())).thenReturn(Optional.of(policyCache));
        when(languageService.getById(1L)).thenReturn(language);

        AiAccountTranslateSubTask subTask = imageSubTask(55L, "505");
        provider.executeSubTask(subTask);
        TurboFlowBridgePollRequest pollRequest = new TurboFlowBridgePollRequest();
        pollRequest.setBridgeId("bridge-a");
        TurboFlowBridgeTaskResponse response = provider.pollTask("token", pollRequest);

        assertFalse(response.isHasTask());
        ArgumentCaptor<SubTaskResult> resultCaptor = ArgumentCaptor.forClass(SubTaskResult.class);
        verify(callback).onSubTaskCompleted(eq(subTask), resultCaptor.capture());
        SubTaskResult result = resultCaptor.getValue();
        assertNull(result.getTranslatedFile());
        assertTrue(result.isCacheHit());
        assertEquals("PUBLIC_ERROR_SEXUAL_UPLOAD", result.getPolicyFallbackReason());
        assertEquals(TokenCostCalculator.imageBusinessPromptTokens(512), result.getBusinessPromptTokens());
        assertEquals(TokenCostCalculator.imageBusinessCompletionTokens(512), result.getBusinessCompletionTokens());
        assertTrue(result.getBusinessCredits() > 0);
    }


    @Test
    void imagePolicyCacheWinsOverExactLanguageSkippedCache() {
        TurboFlowBridgeProvider provider = provider();
        AiAccount account = billedAccount();
        Language language = language(1L, "Polski");
        MultimediaFile sourceImage = image(606L);
        ImageTranslationCache skippedCache = ImageTranslationCache.builder()
                .imageHash("hash")
                .sourceFile(sourceImage)
                .language(language)
                .skipped(true)
                .build();
        ImagePolicyCache policyCache = ImagePolicyCache.builder()
                .imageHash("hash")
                .sourceFile(sourceImage)
                .apiStatus("INVALID_ARGUMENT")
                .reason("PUBLIC_ERROR_SEXUAL_UPLOAD")
                .build();

        when(aiAccountService.findAvailableAccountsByApiKey(AiProvider.TURBOFLOW_GEMINI, "token"))
                .thenReturn(List.of(account));
        when(callback.isTaskActive(anyLong())).thenReturn(true);
        when(multimediaFileService.getById(606L)).thenReturn(sourceImage);
        when(multimediaFileService.download(eq("606"), eq(0)))
                .thenReturn(new ByteArrayInputStream(new byte[] {6, 0, 6}));
        when(imageTranslationCacheRepository.findByImageHashAndLanguageId(anyString(), eq(1L)))
                .thenReturn(Optional.of(skippedCache));
        when(imagePolicyCacheRepository.findByImageHash(anyString())).thenReturn(Optional.of(policyCache));
        when(languageService.getById(1L)).thenReturn(language);

        AiAccountTranslateSubTask subTask = imageSubTask(66L, "606");
        provider.executeSubTask(subTask);
        TurboFlowBridgePollRequest pollRequest = new TurboFlowBridgePollRequest();
        pollRequest.setBridgeId("bridge-a");
        TurboFlowBridgeTaskResponse response = provider.pollTask("token", pollRequest);

        assertFalse(response.isHasTask());
        ArgumentCaptor<SubTaskResult> resultCaptor = ArgumentCaptor.forClass(SubTaskResult.class);
        verify(callback).onSubTaskCompleted(eq(subTask), resultCaptor.capture());
        assertEquals("PUBLIC_ERROR_SEXUAL_UPLOAD", resultCaptor.getValue().getPolicyFallbackReason());
    }

    @Test
    void policyCacheConstraintFailureIsNotMisreportedAsDuplicate() {
        TurboFlowBridgeProvider provider = provider();
        AiAccount account = billedAccount();
        Language language = language(1L, "Polski");
        MultimediaFile sourceImage = image(707L);

        when(aiAccountService.findAvailableAccountsByApiKey(AiProvider.TURBOFLOW_GEMINI, "token"))
                .thenReturn(List.of(account));
        when(callback.isTaskActive(anyLong())).thenReturn(true);
        when(multimediaFileService.getById(707L)).thenReturn(sourceImage);
        when(multimediaFileService.download(eq("707"), eq(0)))
                .thenReturn(new ByteArrayInputStream(new byte[] {7, 0, 7}));
        when(imageTranslationCacheRepository.findByImageHashAndLanguageId(anyString(), eq(1L)))
                .thenReturn(Optional.empty());
        when(imagePolicyCacheRepository.findByImageHash(anyString())).thenReturn(Optional.empty());
        when(imagePolicyCacheRepository.save(any(ImagePolicyCache.class)))
                .thenThrow(new DataIntegrityViolationException("foreign key failure"));
        when(languageService.getById(1L)).thenReturn(language);

        AiAccountTranslateSubTask subTask = imageSubTask(77L, "707");
        provider.executeSubTask(subTask);
        TurboFlowBridgePollRequest pollRequest = new TurboFlowBridgePollRequest();
        pollRequest.setBridgeId("bridge-a");
        TurboFlowBridgeTaskResponse polled = provider.pollTask("token", pollRequest);

        TurboFlowBridgeCompleteRequest request = new TurboFlowBridgeCompleteRequest();
        request.setBridgeId("bridge-a");
        request.setAssignmentId(polled.getAssignmentId());
        request.setPolicyFallback(true);
        request.setPolicyFallbackStatus("INVALID_ARGUMENT");
        request.setPolicyFallbackReason("PUBLIC_ERROR_SEXUAL_UPLOAD");

        assertThrows(IllegalStateException.class, () -> provider.completeTask("token", request));
        verify(callback).onSubTaskFailed(eq(subTask), anyString(), eq(true), eq(null), eq("COMPLETE_PROCESSING_FAILED"));
        verify(callback, never()).onSubTaskCompleted(eq(subTask), any());
    }

    @Test
    void persistedPolicyCacheAcknowledgesAStaleCompletionAfterServerRestart() {
        TurboFlowBridgeProvider provider = provider();
        AiAccount account = billedAccount();
        ImagePolicyCache policyCache = ImagePolicyCache.builder()
                .imageHash("image-hash")
                .apiStatus("INVALID_ARGUMENT")
                .reason("PUBLIC_ERROR_SEXUAL_UPLOAD")
                .build();

        when(aiAccountService.findAvailableAccountsByApiKey(AiProvider.TURBOFLOW_GEMINI, "token"))
                .thenReturn(List.of(account));
        when(imagePolicyCacheRepository.findByImageHash("image-hash"))
                .thenReturn(Optional.of(policyCache));

        TurboFlowBridgeCompleteRequest request = new TurboFlowBridgeCompleteRequest();
        request.setBridgeId("bridge-a");
        request.setAssignmentId("stale-assignment");
        request.setImageHash("image-hash");
        request.setPolicyFallback(true);
        request.setPolicyFallbackStatus("INVALID_ARGUMENT");
        request.setPolicyFallbackReason("PUBLIC_ERROR_SEXUAL_UPLOAD");

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> provider.completeTask("token", request));
        verify(callback, never()).onSubTaskCompleted(any(), any());
        verify(callback, never()).onSubTaskFailed(
                any(), anyString(), org.mockito.ArgumentMatchers.anyBoolean(), any(), any());
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
    private TurboFlowBridgeProvider provider() {
        TurboFlowBridgeProvider provider = new TurboFlowBridgeProvider(
                aiAccountService,
                multimediaFileService,
                languageService,
                imageTranslationCacheRepository,
                usageRecordRepository,
                imagePolicyCacheRepository,
                null,
                null,
                null);
        provider.setCallback(callback);
        return provider;
    }

    private AiAccount billedAccount() {
        return AiAccount.builder()
                .id(7L)
                .provider(AiProvider.TURBOFLOW_GEMINI)
                .model("gemini-image")
                .imageInputPrice(new BigDecimal("0.001"))
                .imageInputPriceUnit(AiBillingPriceUnit.PER_IMAGE)
                .imageOutputPrice(new BigDecimal("0.001"))
                .imageOutputPriceUnit(AiBillingPriceUnit.PER_IMAGE)
                .build();
    }

    private Language language(Long id, String name) {
        return Language.builder().id(id).name(name).code("xx").build();
    }

}
