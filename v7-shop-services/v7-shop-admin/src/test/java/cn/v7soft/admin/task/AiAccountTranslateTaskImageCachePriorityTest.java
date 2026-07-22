package cn.v7soft.admin.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;

import cn.v7soft.admin.controller.req.TranslateByAIRequest;
import cn.v7soft.admin.service.IAiAccountService;
import cn.v7soft.admin.service.ICompanyService;
import cn.v7soft.admin.service.ICountryService;
import cn.v7soft.admin.service.ILanguageService;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.admin.service.IProductService;
import cn.v7soft.admin.service.impl.AiCreditsService;
import cn.v7soft.dao.entities.primary.AiAccount;
import cn.v7soft.dao.entities.primary.ImagePolicyCache;
import cn.v7soft.dao.entities.primary.ImageTranslationCache;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.enums.AiProvider;
import cn.v7soft.dao.repositories.primary.AiTokenUsageRecordRepository;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import cn.v7soft.dao.repositories.primary.ImagePolicyCacheRepository;
import cn.v7soft.dao.repositories.primary.ImageTranslationCacheRepository;
import cn.v7soft.dao.repositories.primary.TextTranslationCacheRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class AiAccountTranslateTaskImageCachePriorityTest {

    @Mock private AsyncTaskRepository asyncTaskRepository;
    @Mock private IProductService productService;
    @Mock private IAiAccountService aiAccountService;
    @Mock private ICompanyService companyService;
    @Mock private IMultimediaFileService multimediaFileService;
    @Mock private ILanguageService languageService;
    @Mock private ICountryService countryService;
    @Mock private AiTokenUsageRecordRepository usageRecordRepository;
    @Mock private ImageTranslationCacheRepository imageTranslationCacheRepository;
    @Mock private ImagePolicyCacheRepository imagePolicyCacheRepository;
    @Mock private TextTranslationCacheRepository textTranslationCacheRepository;
    @Mock private AiCreditsService aiCreditsService;
    @Mock private TransactionTemplate transactionTemplate;

    @Test
    void taskPipelineAppliesPolicyBeforeExactSkippedCacheWithoutBrowserPoll() {
        AiAccountTranslateTask task = new AiAccountTranslateTask(
                asyncTaskRepository, productService, aiAccountService, companyService,
                multimediaFileService, languageService, countryService, usageRecordRepository,
                imageTranslationCacheRepository, imagePolicyCacheRepository,
                textTranslationCacheRepository, aiCreditsService, transactionTemplate, List.of());

        Language language = Language.builder().id(1L).name("Polski").code("pl").build();
        MultimediaFile source = MultimediaFile.builder()
                .id(99L).name("source").suffix("png").width(512).height(512)
                .relativePath("/images/99.png").build();
        AiAccount account = AiAccount.builder()
                .id(7L).provider(AiProvider.TURBOFLOW_GEMINI).build();
        ImageTranslationCache skipped = ImageTranslationCache.builder()
                .imageHash("ignored").sourceFile(source).language(language).skipped(true).build();
        ImagePolicyCache policy = ImagePolicyCache.builder()
                .imageHash("ignored").sourceFile(source)
                .apiStatus("INVALID_ARGUMENT").reason("PUBLIC_ERROR_SEXUAL_UPLOAD").build();

        when(multimediaFileService.getById(99L)).thenReturn(source);
        when(multimediaFileService.download("99", 0))
                .thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        when(imageTranslationCacheRepository.findByImageHashAndLanguageId(anyString(), org.mockito.ArgumentMatchers.eq(1L)))
                .thenReturn(Optional.of(skipped));
        when(imagePolicyCacheRepository.findByImageHash(anyString())).thenReturn(Optional.of(policy));
        when(usageRecordRepository.findFirstByContentHashAndTargetLanguageAndCacheHitFalseOrderByCreateTimeDesc(
                anyString(), org.mockito.ArgumentMatchers.eq("Polski"))).thenReturn(Optional.empty());

        TranslateByAIRequest request = new TranslateByAIRequest();
        request.setProductId("10");
        request.setCountryId("20");
        request.setLanguageId("1");
        request.setAiAccountId("7");
        AiAccountTranslateSubTask subTask = AiAccountTranslateSubTask.image(1L, "99", request);
        AiAccountTranslateTaskStatus status = new AiAccountTranslateTaskStatus(
                1L, 1, 10L, language, 20L, null, 30L);

        assertTrue(task.tryCompleteFromCache(status, subTask, account));
        assertEquals(1, status.getPolicyFallbackImageCount().get());
        assertEquals(1, status.getCompletedSubTaskCount().get());
        verify(imagePolicyCacheRepository).findByImageHash(anyString());
    }
}
