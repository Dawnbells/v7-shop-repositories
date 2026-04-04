package cn.v7soft.admin.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.bean.copier.ValueProvider;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.poi.excel.BigExcelWriter;
import cn.hutool.poi.excel.ExcelUtil;
import cn.v7soft.admin.controller.req.DownloadOrderRequest;
import cn.v7soft.admin.controller.req.SyncThirdPartyOrdersRequest;
import cn.v7soft.admin.controller.req.TranslateByAIRequest;
import cn.v7soft.admin.service.IAsyncTaskService;
import cn.v7soft.admin.service.IOrderService;
import cn.v7soft.admin.service.IOrderTemplateService;
import cn.v7soft.admin.service.IProductService;
import cn.v7soft.admin.service.IS3Service;
import cn.v7soft.admin.service.ITaskExecutorService;
import cn.v7soft.admin.service.IThirdPartyWebsiteService;
import cn.v7soft.admin.service.dto.OrderCheckInfoDto;
import cn.v7soft.admin.service.dto.OrderDownloadDto;
import cn.v7soft.admin.utils.OrderQueryHelper;
import cn.v7soft.common.utils.ConvertUtils;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.admin.utils.TokenCostCalculator;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.entities.primary.Country;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.entities.primary.Order;
import cn.v7soft.dao.entities.primary.Product;
import cn.v7soft.dao.entities.primary.ProductSpecification;
import cn.v7soft.dao.entities.primary.ProductSpecificationAttributes;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.entities.primary.Company;
import cn.v7soft.dao.entities.primary.AiTokenUsageRecord;
import cn.v7soft.dao.entities.primary.ImageTranslationCache;
import cn.v7soft.dao.entities.primary.TextTranslationCache;
import cn.v7soft.dao.tenant.TenantContext;
import cn.v7soft.dao.enums.InvokeMode;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;
import cn.v7soft.dao.enums.TranslationContentType;
import cn.v7soft.dao.repositories.primary.AiTokenUsageRecordRepository;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import cn.v7soft.dao.repositories.primary.ImageTranslationCacheRepository;
import cn.v7soft.dao.repositories.primary.TextTranslationCacheRepository;
import cn.v7soft.admin.exception.DailyQuotaExhaustedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.BatchJob;
import com.google.genai.types.BatchJobDestination;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

@Slf4j
@Service
public class TaskExecutorService implements ITaskExecutorService {

    private final static int BATCH_SIZE = 1000;
    private final static int RUNNING_PROGRESS = 1;
    private static final int RESOLVE_PROGRESS = 99;
    private static final int COMPLETED_OR_FAILED_PROGRESS = 100;

    public static final String QUOTA_EXHAUSTED_MSG = "当前任务已暂停，API今日配额已用尽，恢复配额后将自动重试";
    private static final String ALL_CACHED_BATCH_JOB_NAME = "ALL_CACHED";
    private static final int MAX_ACTIVE_BATCH_JOBS = 90;

    private volatile boolean shutdownRequested = false;
    private final java.util.concurrent.atomic.AtomicInteger activeBatchJobs = new java.util.concurrent.atomic.AtomicInteger(0);

    @jakarta.annotation.PreDestroy
    public void onShutdown() {
        shutdownRequested = true;
        log.info("[TaskExecutorService] 收到应用关闭信号，通知所有长时间运行任务尽快退出");
    }
    public static final Pattern IMG_ID_PATTERN = Pattern.compile("/multimedia/([0-9]+)");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> BATCH_COMPLETED_STATES = Set.of(
            "JOB_STATE_SUCCEEDED", "JOB_STATE_FAILED", "JOB_STATE_CANCELLED",
            "JOB_STATE_EXPIRED", "JOB_STATE_PARTIALLY_SUCCEEDED");
    private static final Set<String> BATCH_DOWNLOADABLE_STATES = Set.of(
            "JOB_STATE_SUCCEEDED", "JOB_STATE_PARTIALLY_SUCCEEDED");

    // ======================== Phase A~D 数据模型 ========================

    @lombok.Data
    @lombok.Builder
    private static class TranslateContext {
        private Product product;
        private Language language;
        private Country country;
        private SystemUser owner;
        private String langName;
        private String introduction;

        /** A.1: 未命中缓存的文本 (hash -> sourceText)，内容已去重 */
        private Map<String, String> uncachedTextMap;
        /** A.2: 命中缓存的文本 (hash -> translatedText) */
        private Map<String, String> cachedTextMap;
        /** A.3: 所有图片 id -> hash */
        private Map<String, String> imageIdToHash;
        /** A.4: 未命中缓存的图片 (hash -> byte[])，按内容去重 */
        private Map<String, byte[]> uncachedImageData;
        /** A.4 辅助: hash -> mimeType */
        private Map<String, String> uncachedImageMimeTypes;
        /** A.4 辅助: hash -> 源文件 (存缓存用) */
        private Map<String, MultimediaFile> imageHashToSourceFile;
        /** A.5: 命中缓存的图片 (hash -> targetFile, null=skipped) */
        private Map<String, MultimediaFile> cachedImageMap;
        /** A.6: 未命中缓存的 HTML（翻译前 html，null 表示已缓存或无 html） */
        private String uncachedHtml;
        /** A.7: 已命中缓存的 HTML（翻译后 html，null 表示需要翻译） */
        private String cachedTranslatedHtml;
    }

    @lombok.Data
    @lombok.Builder
    private static class TranslateResult {
        private Map<String, String> translatedTextMap;
        private Map<String, MultimediaFile> translatedImageMap;
        private String translatedHtml;
    }

    // ======================== 依赖注入 ========================

    private final IOrderService orderService;
    private final IS3Service s3Service;
    private final IThirdPartyWebsiteService thirdPartyWebsiteService;
    private final IAsyncTaskService asyncTaskService;
    private final IOrderTemplateService orderTemplateService;
    private final IProductService productService;
    private final GeminiTranslateService geminiTranslateService;
    private final IMultimediaFileService multimediaFileService;
    private final cn.v7soft.admin.service.ILanguageService languageService;
    private final cn.v7soft.admin.service.ICountryService countryService;
    private final AsyncTaskRepository asyncTaskRepository;
    private final TranslateTaskMetrics translateTaskMetrics;
    private final ITaskExecutorService self;
    private final ImageTranslationCacheRepository imageTranslationCacheRepository;
    private final TextTranslationCacheRepository textTranslationCacheRepository;
    private final cn.v7soft.admin.service.ICompanyService companyService;
    private final ExecutorService translationExecutor;
    private final RateLimiter geminiRateLimiter;
    private final Retry geminiDirectRetry;
    private final Retry batchPollRetry;
    private final AiTokenUsageRecordRepository aiTokenUsageRecordRepository;
    private final GeminiQuotaTracker geminiQuotaTracker;

    public TaskExecutorService(IAsyncTaskService asyncTaskService, @Lazy IOrderService orderService, IS3Service s3Service,
                       @Lazy IThirdPartyWebsiteService thirdPartyWebsiteService, IOrderTemplateService orderTemplateService,
                       @Lazy IProductService productService, GeminiTranslateService geminiTranslateService,
                       IMultimediaFileService multimediaFileService, cn.v7soft.admin.service.ILanguageService languageService,
                       cn.v7soft.admin.service.ICountryService countryService,
                       AsyncTaskRepository asyncTaskRepository, TranslateTaskMetrics translateTaskMetrics,
                       @Lazy ITaskExecutorService self,
                       ImageTranslationCacheRepository imageTranslationCacheRepository,
                       TextTranslationCacheRepository textTranslationCacheRepository,
                       cn.v7soft.admin.service.ICompanyService companyService,
                       @Qualifier("translationExecutor") ExecutorService translationExecutor,
                       RateLimiter geminiRateLimiter,
                       @Qualifier("geminiDirectRetry") Retry geminiDirectRetry,
                       @Qualifier("batchPollRetry") Retry batchPollRetry,
                       AiTokenUsageRecordRepository aiTokenUsageRecordRepository,
                       GeminiQuotaTracker geminiQuotaTracker) {
        this.asyncTaskService = asyncTaskService;
        this.orderService = orderService;
        this.s3Service = s3Service;
        this.thirdPartyWebsiteService = thirdPartyWebsiteService;
        this.orderTemplateService = orderTemplateService;
        this.productService = productService;
        this.geminiTranslateService = geminiTranslateService;
        this.multimediaFileService = multimediaFileService;
        this.languageService = languageService;
        this.countryService = countryService;
        this.asyncTaskRepository = asyncTaskRepository;
        this.translateTaskMetrics = translateTaskMetrics;
        this.self = self;
        this.imageTranslationCacheRepository = imageTranslationCacheRepository;
        this.textTranslationCacheRepository = textTranslationCacheRepository;
        this.companyService = companyService;
        this.translationExecutor = translationExecutor;
        this.geminiRateLimiter = geminiRateLimiter;
        this.geminiDirectRetry = geminiDirectRetry;
        this.batchPollRetry = batchPollRetry;
        this.aiTokenUsageRecordRepository = aiTokenUsageRecordRepository;
        this.geminiQuotaTracker = geminiQuotaTracker;
    }

    // ======================== ITaskExecutorService 接口实现 ========================

    @Override
    public void recoverUnfinishedTasks() {
        List<AsyncTask> unfinished = asyncTaskRepository.findByStateIn(List.of(TaskState.PENDING, TaskState.PROCESSING));

        int batchJobCount = 0;
        for (AsyncTask task : unfinished) {
            if (task.getTaskType() == TaskType.PRODUCT_AI_TRANSLATE
                    && task.getState() == TaskState.PROCESSING
                    && task.getBatchJobName() != null && !task.getBatchJobName().isBlank()) {
                batchJobCount++;
            }
        }
        activeBatchJobs.set(batchJobCount);
        log.info("[recoverTasks] activeBatchJobs 校准: {}", batchJobCount);

        if (unfinished.isEmpty()) {
            log.info("[recoverTasks] 没有需要恢复的未完成任务");
            return;
        }
        log.info("[recoverTasks] 发现 {} 个未完成任务，开始恢复", unfinished.size());
        for (AsyncTask task : unfinished) {
            try {
                if (task.getTaskType() == TaskType.PRODUCT_AI_TRANSLATE
                        && task.getBatchJobName() != null && !task.getBatchJobName().isBlank()) {
                    log.info("[recoverTasks] 恢复AI翻译任务(已提交Batch): taskId={}, jobName={}", task.getId(), task.getBatchJobName());
                } else {
                    log.info("[recoverTasks] 重新执行任务: taskId={}, type={}, state={}", task.getId(), task.getTaskType(), task.getState());
                    asyncTaskService.updateAsyncTask(task, TaskState.PENDING, 0);
                    self.submitAsyncTask(task.getId());
                }
            } catch (Exception e) {
                log.error("[recoverTasks] 恢复任务失败: taskId={}", task.getId(), e);
                task.setMessage("恢复任务失败: " + e.getMessage());
                asyncTaskService.updateAsyncTask(task, TaskState.FAILED, COMPLETED_OR_FAILED_PROGRESS);
            }
        }
    }

    @Override
    @Async("threadPoolTaskExecutor")
    public void resumeTranslateTask(Long taskId) {
        Pair<AsyncTask, SystemUserDto> pair = asyncTaskService.getAndInitializeOwner(taskId);
        AsyncTask task = pair.getKey();
        initTenantContext(task);
        try {
            log.info("[resumeTranslateTask] 开始恢复AI翻译: taskId={}", taskId);
            resumeProductAITranslate(task);
        } finally {
            TenantContext.clear();
        }
    }

    @Override
    @Async("threadPoolTaskExecutor")
    public void submitAsyncTask(Long taskId) {
        ThreadUtil.sleep(500);
        Pair<AsyncTask, SystemUserDto> pair = asyncTaskService.getAndInitializeOwner(taskId);
        AsyncTask task = pair.getKey();
        SystemUserDto owner = pair.getValue();
        initTenantContext(task);
        try {
            log.debug("开始执行任务: {}", task.getId());
            if (task.getState() != TaskState.PENDING) {
                task.setMessage("当前任务状态异常: " + task.getState());
                asyncTaskService.updateAsyncTask(task, TaskState.FAILED, 100);
                return;
            }
            if (Objects.requireNonNull(task.getTaskType()) == TaskType.ORDER_DOWNLOAD) {
                executeOrderDownload(task, owner);
            } else if (task.getTaskType() == TaskType.ORDER_UPLOAD) {
                executeOrderUpload(task, owner);
            } else if (task.getTaskType() == TaskType.THIRD_PARTY_ORDER_SYNC) {
                executeThirdPartyOrderSyncUpload(task, owner);
            } else if (task.getTaskType() == TaskType.PRODUCT_AI_TRANSLATE) {
                executeBatchTranslate(task);
            } else if (task.getTaskType() == TaskType.PRODUCT_AI_TRANSLATE_DIRECT) {
                if (geminiQuotaTracker.isAllExhausted()) {
                    log.info("[submitAsyncTask] taskId={} API配额已耗尽, 保持PENDING等待配额恢复", task.getId());
                    task.setMessage(QUOTA_EXHAUSTED_MSG);
                    asyncTaskService.updateAsyncTask(task, TaskState.PENDING, 0);
                    return;
                }
                executeDirectTranslate(task);
            } else {
                task.setMessage("未知任务类型: " + task.getTaskType());
                asyncTaskService.updateAsyncTask(task, TaskState.FAILED, 100);
            }
            if (task.getState() == TaskState.FAILED) {
                log.debug("任务挂起: {}", task.getId());
            } else {
                log.debug("任务完成: {}", task.getId());
            }
        } finally {
            TenantContext.clear();
        }
    }

    // ======================== Phase A: 准备翻译数据 ========================

    private TranslateContext prepareTranslateContext(AsyncTask task, TranslateByAIRequest request) {
        Product product = productService.getByIdWithSpecifications(Long.parseLong(request.getProductId()));
        Language language = languageService.getById(Long.valueOf(request.getLanguageId()));
        Country country = request.getCountryId() != null
                ? countryService.getById(Long.valueOf(request.getCountryId()))
                : product.getCountry();
        SystemUser owner = task.getOwner();
        String langName = language.getName();
        String introduction = product.getIntroduction();

        Map<String, String> allTextMap = collectTextsToTranslate(product);
        List<String> imgIds = collectImageIds(product);

        // A.3~A.5: 图片处理 (hash-keyed)
        Map<String, String> imageIdToHash = new LinkedHashMap<>();
        Map<String, byte[]> uncachedImageData = new LinkedHashMap<>();
        Map<String, String> uncachedImageMimeTypes = new HashMap<>();
        Map<String, MultimediaFile> imageHashToSourceFile = new HashMap<>();
        Map<String, MultimediaFile> cachedImageMap = new HashMap<>();

        downloadAndFilterImages(imgIds, language, imageIdToHash,
                uncachedImageData, uncachedImageMimeTypes, imageHashToSourceFile, cachedImageMap);

        // A.1~A.2: 文本缓存拆分
        Map<String, String> cachedTextMap = new LinkedHashMap<>();
        Map<String, String> uncachedTextMap = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : allTextMap.entrySet()) {
            String cached = lookupSingleTextCache(entry.getValue(), language);
            if (cached != null) {
                cachedTextMap.put(entry.getKey(), cached);
            } else {
                uncachedTextMap.put(entry.getKey(), entry.getValue());
            }
        }

        // A.6~A.7: HTML 缓存
        String uncachedHtml = null;
        String cachedTranslatedHtml = null;
        if (introduction != null && !introduction.isBlank()) {
            cachedTranslatedHtml = lookupHtmlCache(introduction, language);
            if (cachedTranslatedHtml == null) {
                uncachedHtml = introduction;
            }
        }

        log.info("[prepareContext] taskId={} 产品: title='{}', targetLang='{}', targetCountry='{}', " +
                        "texts: uncached={}/{}, images: uncached={}/{}, html={}",
                task.getId(), product.getTitle(), langName, country != null ? country.getName() : "null",
                uncachedTextMap.size(), allTextMap.size(),
                uncachedImageData.size(), imgIds.size(),
                cachedTranslatedHtml != null ? "CACHED" : (uncachedHtml != null ? "UNCACHED" : "NONE"));

        return TranslateContext.builder()
                .product(product).language(language).country(country).owner(owner)
                .langName(langName).introduction(introduction)
                .uncachedTextMap(uncachedTextMap).cachedTextMap(cachedTextMap)
                .imageIdToHash(imageIdToHash)
                .uncachedImageData(uncachedImageData).uncachedImageMimeTypes(uncachedImageMimeTypes)
                .imageHashToSourceFile(imageHashToSourceFile).cachedImageMap(cachedImageMap)
                .uncachedHtml(uncachedHtml).cachedTranslatedHtml(cachedTranslatedHtml)
                .build();
    }

    private void downloadAndFilterImages(List<String> imgIds, Language language,
                                         Map<String, String> imageIdToHash,
                                         Map<String, byte[]> uncachedImageData,
                                         Map<String, String> uncachedImageMimeTypes,
                                         Map<String, MultimediaFile> imageHashToSourceFile,
                                         Map<String, MultimediaFile> cachedImageMap) {
        log.info("[downloadImages] 开始处理 {} 张图片 (hash-keyed, 缓存+去重)", imgIds.size());
        int cachedCount = 0, skippedAnimated = 0, dedupCount = 0;
        for (int i = 0; i < imgIds.size(); i++) {
            String imgId = imgIds.get(i);
            try {
                MultimediaFile file = multimediaFileService.getById(Long.valueOf(imgId));
                String suffix = file.getSuffix().toLowerCase();

                if ("gif".equalsIgnoreCase(suffix)) {
                    log.info("[downloadImages] [{}/{}] imgId={} 跳过GIF", i + 1, imgIds.size(), imgId);
                    skippedAnimated++;
                    continue;
                }

                byte[] bytes;
                try (InputStream stream = s3Service.download(file.getRelativePath())) {
                    bytes = stream.readAllBytes();
                }

                if ("webp".equalsIgnoreCase(suffix) && isAnimatedWebp(bytes)) {
                    log.info("[downloadImages] [{}/{}] imgId={} 跳过动图 webp", i + 1, imgIds.size(), imgId);
                    skippedAnimated++;
                    continue;
                }

                String imageHash = DigestUtil.sha256Hex(bytes);
                imageIdToHash.put(imgId, imageHash);

                Optional<ImageTranslationCache> cached = imageTranslationCacheRepository
                        .findByImageHashAndLanguageId(imageHash, language.getId());
                if (cached.isPresent()) {
                    ImageTranslationCache it = cached.get();
                    if (!it.isSkipped() && it.getTranslatedFile() != null) {
                        cachedImageMap.put(imageHash, it.getTranslatedFile());
                    }
                    cachedCount++;
                    continue;
                }

                if (uncachedImageData.containsKey(imageHash)) {
                    dedupCount++;
                    continue;
                }

                String mimeType = "image/" + ("jpg".equalsIgnoreCase(suffix) ? "jpeg" : suffix);
                uncachedImageData.put(imageHash, bytes);
                uncachedImageMimeTypes.put(imageHash, mimeType);
                imageHashToSourceFile.put(imageHash, file);
            } catch (Exception e) {
                log.warn("[downloadImages] [{}/{}] imgId={} 下载失败, 使用原图", i + 1, imgIds.size(), imgId, e);
            }
        }
        if (dedupCount > 0) translateTaskMetrics.recordDedupHit();
        log.info("[downloadImages] 完成: 总计={}, 缓存={}, 动图跳过={}, 去重={}, 待翻译={}",
                imgIds.size(), cachedCount, skippedAnimated, dedupCount, uncachedImageData.size());
    }

    // ======================== Phase B: 批量翻译 ========================

    private void executeBatchTranslate(AsyncTask task) {
        try {
            if (activeBatchJobs.get() >= MAX_ACTIVE_BATCH_JOBS) {
                log.info("[batchTranslate] taskId={} 活跃 Batch Job 已达上限({}), 任务保持 PENDING 等待下次调度",
                        task.getId(), MAX_ACTIVE_BATCH_JOBS);
                task.setMessage("批量任务队列已满，等待空位后自动重试");
                asyncTaskService.updateAsyncTask(task, TaskState.PENDING, 0);
                return;
            }

            TranslateByAIRequest request = JSONUtil.toBean(task.getParameters(), TranslateByAIRequest.class);
            log.info("[batchTranslate] taskId={} 开始批量翻译, productId={}, languageId={}",
                    task.getId(), request.getProductId(), request.getLanguageId());

            task.setMessage("正在准备翻译内容...");
            if (!asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 2)) {
                log.info("[batchTranslate] taskId={} 任务已被取消或终止, 跳过执行", task.getId());
                return;
            }

            TranslateContext ctx = prepareTranslateContext(task, request);

            int cachedCount = ctx.getCachedTextMap().size()
                    + (ctx.getCachedTranslatedHtml() != null ? 1 : 0)
                    + ctx.getCachedImageMap().size();
            int totalRequests = cachedCount;

            StringBuilder jsonl = new StringBuilder();

            for (Map.Entry<String, String> entry : ctx.getUncachedTextMap().entrySet()) {
                jsonl.append(geminiTranslateService.buildTextTranslateJsonlEntry(
                        "text-" + entry.getKey(), entry.getValue(), ctx.getLangName())).append("\n");
                totalRequests++;
            }

            if (ctx.getUncachedHtml() != null) {
                jsonl.append(geminiTranslateService.buildHtmlTranslateJsonlEntry(
                        "html", ctx.getUncachedHtml(), ctx.getLangName())).append("\n");
                totalRequests++;
            }

            for (Map.Entry<String, byte[]> entry : ctx.getUncachedImageData().entrySet()) {
                String hash = entry.getKey();
                jsonl.append(geminiTranslateService.buildImageTranslateJsonlEntry(
                        "img-" + hash, entry.getValue(),
                        ctx.getUncachedImageMimeTypes().get(hash), ctx.getLangName())).append("\n");
                totalRequests++;
            }

            boolean hasUncached = !ctx.getUncachedTextMap().isEmpty()
                    || ctx.getUncachedHtml() != null
                    || !ctx.getUncachedImageData().isEmpty();

            log.info("[batchTranslate] taskId={} JSONL: totalRequests={}, cachedCount={}, size={}bytes",
                    task.getId(), totalRequests, cachedCount, jsonl.length());

            if (!hasUncached) {
                log.info("[batchTranslate] taskId={} 全部命中缓存, 设置标记等待下次轮询处理", task.getId());
                task.setBatchJobName(ALL_CACHED_BATCH_JOB_NAME);
                task.setMessage("全部命中缓存，等待保存...");
                asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 10);
                return;
            }

            request.setTotalRequests(totalRequests);
            task.setParameters(JSONUtil.toJsonStr(request));
            task.setMessage("正在上传翻译请求...");
            if (!asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 5)) {
                log.info("[batchTranslate] taskId={} 上传前发现任务已取消, 跳过", task.getId());
                return;
            }

            String uploadedFileName = geminiTranslateService.uploadBatchFile(jsonl.toString());
            BatchJob batchJob = geminiTranslateService.createBatchJob(uploadedFileName);
            String jobName = batchJob.name().orElseThrow(() -> new RuntimeException("Batch Job 创建后无 name"));
            task.setBatchJobName(jobName);
            activeBatchJobs.incrementAndGet();
            log.info("[batchTranslate] taskId={} Batch Job 创建成功: jobName={}, activeBatchJobs={}",
                    task.getId(), jobName, activeBatchJobs.get());

            task.setMessage("AI翻译中: 等待Batch完成...");
            asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 10);

        } catch (Throwable e) {
            log.error("[batchTranslate] taskId={} 提交异常", task.getId(), e);
            if (!shutdownRequested) {
                task.setMessage(e.getMessage());
                asyncTaskService.updateAsyncTask(task, TaskState.FAILED, COMPLETED_OR_FAILED_PROGRESS);
                translateTaskMetrics.recordFailed();
            }
        }
    }

    private TranslateResult processBatchResult(AsyncTask task, String batchState, BatchJob currentJob,
                                               TranslateContext ctx) {
        Map<String, String> translatedTextMap = new HashMap<>();
        Map<String, MultimediaFile> translatedImageMap = new HashMap<>();
        String translatedHtml = null;

        if (!BATCH_DOWNLOADABLE_STATES.contains(batchState)) {
            log.warn("[processResult] taskId={} Batch 状态不可下载结果: {}, 全部交给 fallback", task.getId(), batchState);
            return TranslateResult.builder()
                    .translatedTextMap(translatedTextMap).translatedImageMap(translatedImageMap)
                    .translatedHtml(translatedHtml).build();
        }

        String resultFileName = currentJob.dest()
                .flatMap(BatchJobDestination::fileName).orElse(null);
        if (resultFileName == null) {
            log.warn("[processResult] taskId={} 无结果文件", task.getId());
            return TranslateResult.builder()
                    .translatedTextMap(translatedTextMap).translatedImageMap(translatedImageMap)
                    .translatedHtml(translatedHtml).build();
        }

        try {
            String resultContent = geminiTranslateService.downloadBatchResult(resultFileName);
            Map<String, JsonNode> resultMap = new HashMap<>();
            for (String line : resultContent.split("\n")) {
                if (line.isBlank()) continue;
                try {
                    JsonNode node = OBJECT_MAPPER.readTree(line);
                    String key = node.has("key") ? node.get("key").asText() : null;
                    if (key != null) resultMap.put(key, node);
                } catch (Exception e) {
                    log.warn("[processResult] taskId={} 解析结果行失败: {}", task.getId(), e.getMessage());
                }
            }

            String langName = ctx.getLangName();
            SystemUser owner = ctx.getOwner();

            // 文本
            for (Map.Entry<String, String> entry : ctx.getUncachedTextMap().entrySet()) {
                String hash = entry.getKey();
                JsonNode textNode = resultMap.get("text-" + hash);
                if (textNode != null && textNode.has("response")) {
                    JsonNode respNode = textNode.get("response");
                    String translated = extractTextFromResponse(respNode);
                    if (translated != null && !translated.isBlank()) {
                        translatedTextMap.put(hash, translated);
                        writeSingleTextCache(entry.getValue(), translated, ctx.getLanguage());
                    }
                    GeminiTranslateService.TokenUsage usage = GeminiTranslateService.extractTokenUsageFromBatchResponse(respNode);
                    saveTokenUsageRecord(task.getId(), TranslationContentType.TEXT, hash, langName,
                            false, InvokeMode.BATCH, usage, false, null, null, null, owner);
                }
            }

            // HTML
            if (ctx.getUncachedHtml() != null) {
                JsonNode htmlNode = resultMap.get("html");
                if (htmlNode != null && htmlNode.has("response")) {
                    JsonNode respNode = htmlNode.get("response");
                    translatedHtml = extractTextFromResponse(respNode);
                    if (translatedHtml != null) {
                        writeHtmlTranslationCache(ctx.getUncachedHtml(), translatedHtml, ctx.getLanguage());
                    }
                    String htmlHash = DigestUtil.sha256Hex(ctx.getUncachedHtml());
                    GeminiTranslateService.TokenUsage usage = GeminiTranslateService.extractTokenUsageFromBatchResponse(respNode);
                    saveTokenUsageRecord(task.getId(), TranslationContentType.HTML, htmlHash, langName,
                            false, InvokeMode.BATCH, usage, false, null, null, null, owner);
                }
            }

            // 图片
            for (String hash : ctx.getUncachedImageData().keySet()) {
                JsonNode imgNode = resultMap.get("img-" + hash);
                if (imgNode == null || !imgNode.has("response")) continue;
                JsonNode respNode = imgNode.get("response");
                byte[] imgBytes = extractImageFromResponse(respNode);
                MultimediaFile sourceFile = ctx.getImageHashToSourceFile().get(hash);
                GeminiTranslateService.TokenUsage usage = GeminiTranslateService.extractTokenUsageFromBatchResponse(respNode);
                if (imgBytes != null && sourceFile != null) {
                    try {
                        MultimediaFile newFile = multimediaFileService.saveTranslatedImage(
                                imgBytes, sourceFile.getSuffix(), ctx.getOwner());
                        translatedImageMap.put(hash, newFile);
                        saveImageTranslationCache(hash, sourceFile, ctx.getLanguage(), newFile, false);
                        saveTokenUsageRecord(task.getId(), TranslationContentType.IMAGE, hash, langName,
                                false, InvokeMode.BATCH, usage, true, null, null, null, owner);
                    } catch (Exception e) {
                        log.warn("[processResult] taskId={} 图片 hash={} 保存失败", task.getId(), hash, e);
                    }
                } else if (sourceFile != null) {
                    saveImageTranslationCache(hash, sourceFile, ctx.getLanguage(), null, true);
                    saveNoOutputImageTokenRecord(task.getId(), hash, langName, InvokeMode.BATCH, usage, sourceFile, owner);
                }
            }

            geminiTranslateService.deleteFile(resultFileName);
        } catch (Exception e) {
            log.error("[processResult] taskId={} 下载/解析结果失败", task.getId(), e);
        }

        log.info("[processResult] taskId={} 结果: texts={}, images={}, html={}",
                task.getId(), translatedTextMap.size(), translatedImageMap.size(),
                translatedHtml != null ? translatedHtml.length() + "chars" : "null");

        return TranslateResult.builder()
                .translatedTextMap(translatedTextMap).translatedImageMap(translatedImageMap)
                .translatedHtml(translatedHtml).build();
    }

    private TranslateResult fallbackFailedItems(AsyncTask task, TranslateContext ctx, TranslateResult batchResult) {
        Map<String, String> failedTextMap = new LinkedHashMap<>();
        for (String hash : ctx.getUncachedTextMap().keySet()) {
            if (!batchResult.getTranslatedTextMap().containsKey(hash)) {
                failedTextMap.put(hash, ctx.getUncachedTextMap().get(hash));
            }
        }

        Map<String, byte[]> failedImageData = new LinkedHashMap<>();
        Map<String, String> failedImageMimeTypes = new HashMap<>();
        Map<String, MultimediaFile> failedImageSourceFiles = new HashMap<>();
        for (String hash : ctx.getUncachedImageData().keySet()) {
            if (!batchResult.getTranslatedImageMap().containsKey(hash)) {
                failedImageData.put(hash, ctx.getUncachedImageData().get(hash));
                failedImageMimeTypes.put(hash, ctx.getUncachedImageMimeTypes().get(hash));
                failedImageSourceFiles.put(hash, ctx.getImageHashToSourceFile().get(hash));
            }
        }

        boolean needHtmlFallback = ctx.getUncachedHtml() != null && batchResult.getTranslatedHtml() == null;

        if (failedTextMap.isEmpty() && failedImageData.isEmpty() && !needHtmlFallback) {
            return TranslateResult.builder()
                    .translatedTextMap(new HashMap<>()).translatedImageMap(new HashMap<>())
                    .translatedHtml(null).build();
        }

        log.info("[fallback] taskId={} 开始补刀: texts={}, images={}, html={}",
                task.getId(), failedTextMap.size(), failedImageData.size(), needHtmlFallback);

        if (!failedTextMap.isEmpty()) translateTaskMetrics.recordFallbackText();
        if (!failedImageData.isEmpty()) translateTaskMetrics.recordFallbackImage();
        if (needHtmlFallback) translateTaskMetrics.recordFallbackHtml();

        task.setMessage("正在补充翻译...");
        asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 84);

        TranslateContext fallbackCtx = TranslateContext.builder()
                .product(ctx.getProduct()).language(ctx.getLanguage()).country(ctx.getCountry())
                .owner(ctx.getOwner()).langName(ctx.getLangName()).introduction(ctx.getIntroduction())
                .uncachedTextMap(failedTextMap).cachedTextMap(new HashMap<>())
                .imageIdToHash(ctx.getImageIdToHash())
                .uncachedImageData(failedImageData).uncachedImageMimeTypes(failedImageMimeTypes)
                .imageHashToSourceFile(failedImageSourceFiles).cachedImageMap(new HashMap<>())
                .uncachedHtml(needHtmlFallback ? ctx.getUncachedHtml() : null)
                .cachedTranslatedHtml(null)
                .build();

        return executeDirectTranslateCore(task, fallbackCtx);
    }

    private void resumeProductAITranslate(AsyncTask task) {
        long taskStart = System.currentTimeMillis();
        String jobName = task.getBatchJobName();
        try {
            log.info("[resumeTranslate] taskId={} 单次检查, jobName={}", task.getId(), jobName);

            if (ALL_CACHED_BATCH_JOB_NAME.equals(jobName)) {
                log.info("[resumeTranslate] taskId={} 全部命中缓存, 直接保存", task.getId());
                TranslateByAIRequest request = JSONUtil.toBean(task.getParameters(), TranslateByAIRequest.class);
                TranslateContext ctx = prepareTranslateContext(task, request);
                writeCacheHitTokenRecords(task, ctx, InvokeMode.BATCH);
                saveTranslatedProduct(task, ctx, TranslateResult.builder()
                        .translatedTextMap(new HashMap<>())
                        .translatedImageMap(new HashMap<>())
                        .translatedHtml(null).build());
                translateTaskMetrics.recordCompleted();
                return;
            }

            AsyncTask freshTask = asyncTaskService.getById(task.getId());
            if (freshTask.getState() == TaskState.CANCELLED) {
                log.info("[resumeTranslate] taskId={} 任务已取消", task.getId());
                geminiTranslateService.cancelBatchJob(jobName);
                activeBatchJobs.decrementAndGet();
                return;
            }

            BatchJob currentJob;
            try {
                currentJob = Retry.decorateSupplier(batchPollRetry,
                        () -> geminiTranslateService.getBatchJob(jobName)).get();
            } catch (Exception e) {
                log.warn("[resumeTranslate] taskId={} 查询 Batch 状态失败, 等待下次巡检", task.getId(), e);
                return;
            }

            String batchState = currentJob.state().map(Object::toString).orElse("UNKNOWN");
            log.info("[resumeTranslate] taskId={} Batch 状态: {}", task.getId(), batchState);

            if (!BATCH_COMPLETED_STATES.contains(batchState)) {
                if (isCancelled(task)) {
                    log.info("[resumeTranslate] taskId={} 更新进度前发现已取消", task.getId());
                    return;
                }
                TranslateByAIRequest resumeRequest = JSONUtil.toBean(task.getParameters(), TranslateByAIRequest.class);
                int totalRequests = resumeRequest.getTotalRequests() != null ? resumeRequest.getTotalRequests() : 0;

                long successCount = 0, failedCount = 0;
                int completed = 0;
                try {
                    JsonNode jobJson = OBJECT_MAPPER.readTree(currentJob.toJson());
                    JsonNode statsNode = jobJson.path("batchStats");
                    if (!statsNode.isMissingNode()) {
                        successCount = Long.parseLong(statsNode.path("successfulRequestCount").asText("0"));
                        failedCount = Long.parseLong(statsNode.path("failedRequestCount").asText("0"));
                        completed = (int) (successCount + failedCount);
                    }
                } catch (Exception ignored) {}

                task.setMessage(String.format("AI翻译中: 已完成 %d/%d (成功 %d, 失败 %d)",
                        completed, totalRequests, successCount, failedCount));
                asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, Math.min(10 + completed * 80 / Math.max(totalRequests, 1), 80));
                return;
            }

            if (isCancelled(task)) {
                log.info("[resumeTranslate] taskId={} 处理结果前发现已取消", task.getId());
                activeBatchJobs.decrementAndGet();
                cleanupBatchResources(jobName, null);
                return;
            }

            activeBatchJobs.decrementAndGet();
            log.info("[resumeTranslate] taskId={} Batch 已结束: {}, activeBatchJobs={}", task.getId(), batchState, activeBatchJobs.get());

            task.setMessage("正在加载产品数据...");
            asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 82);

            TranslateByAIRequest request = JSONUtil.toBean(task.getParameters(), TranslateByAIRequest.class);
            TranslateContext ctx = prepareTranslateContext(task, request);

            writeCacheHitTokenRecords(task, ctx, InvokeMode.BATCH);

            TranslateResult batchResult = processBatchResult(task, batchState, currentJob, ctx);
            cleanupBatchResources(jobName, null);

            TranslateResult fallbackResult = fallbackFailedItems(task, ctx, batchResult);
            TranslateResult mergedResult = mergeResults(batchResult, fallbackResult);

            saveTranslatedProduct(task, ctx, mergedResult);

            long elapsed = System.currentTimeMillis() - taskStart;
            translateTaskMetrics.recordDuration(elapsed);
            if (task.getState() == TaskState.COMPLETED) translateTaskMetrics.recordCompleted();
            log.info("[resumeTranslate] taskId={} 恢复完成, 总耗时={}ms", task.getId(), elapsed);

        } catch (DailyQuotaExhaustedException e) {
            log.warn("[resumeTranslate] taskId={} 每日配额已耗尽, 暂停任务等待配额恢复", task.getId());
            task.setMessage(QUOTA_EXHAUSTED_MSG);
            asyncTaskService.updateAsyncTask(task, TaskState.PENDING, 0);
        } catch (Throwable e) {
            long elapsed = System.currentTimeMillis() - taskStart;
            translateTaskMetrics.recordFailed();
            translateTaskMetrics.recordDuration(elapsed);
            log.error("[resumeTranslate] taskId={} 恢复失败, 总耗时={}ms", task.getId(), elapsed, e);
            activeBatchJobs.decrementAndGet();
            if (!shutdownRequested) {
                task.setMessage(e.getMessage());
                asyncTaskService.updateAsyncTask(task, TaskState.FAILED, COMPLETED_OR_FAILED_PROGRESS);
            }
        }
    }

    // ======================== Phase C: 即时翻译 ========================

    private void executeDirectTranslate(AsyncTask task) {
        long taskStart = System.currentTimeMillis();
        try {
            TranslateByAIRequest request = JSONUtil.toBean(task.getParameters(), TranslateByAIRequest.class);
            log.info("[directTranslate] taskId={} 开始即时翻译, productId={}, languageId={}",
                    task.getId(), request.getProductId(), request.getLanguageId());

            task.setMessage("即时翻译: 正在准备...");
            if (!asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 2)) {
                log.info("[directTranslate] taskId={} 任务已被取消或终止, 跳过执行", task.getId());
                return;
            }

            TranslateContext ctx = prepareTranslateContext(task, request);

            writeCacheHitTokenRecords(task, ctx, InvokeMode.STANDARD);

            TranslateResult result = executeDirectTranslateCore(task, ctx);

            saveTranslatedProduct(task, ctx, result);

            long elapsed = System.currentTimeMillis() - taskStart;
            translateTaskMetrics.recordDuration(elapsed);
            translateTaskMetrics.recordCompleted();
            log.info("[directTranslate] taskId={} 完成, 总耗时={}ms", task.getId(), elapsed);

        } catch (DailyQuotaExhaustedException e) {
            log.warn("[directTranslate] taskId={} 每日配额已耗尽, 暂停任务等待配额恢复", task.getId());
            task.setMessage(QUOTA_EXHAUSTED_MSG);
            asyncTaskService.updateAsyncTask(task, TaskState.PENDING, 0);
        } catch (Throwable e) {
            long elapsed = System.currentTimeMillis() - taskStart;
            translateTaskMetrics.recordFailed();
            translateTaskMetrics.recordDuration(elapsed);
            log.error("[directTranslate] taskId={} 异常, 总耗时={}ms", task.getId(), elapsed, e);
            if (!shutdownRequested) {
                task.setMessage(e.getMessage());
                asyncTaskService.updateAsyncTask(task, TaskState.FAILED, COMPLETED_OR_FAILED_PROGRESS);
            } else {
                log.info("[directTranslate] taskId={} 应用关闭中, 跳过标记失败, 任务将在重启后恢复", task.getId());
            }
        }
    }

    /**
     * Phase C 核心：并发翻译 A.1 + A.4 + A.6，使用 RateLimiter + Retry + 线程池。
     */
    private TranslateResult executeDirectTranslateCore(AsyncTask task, TranslateContext ctx) {
        Map<String, String> translatedTextMap = new ConcurrentHashMap<>();
        Map<String, MultimediaFile> translatedImageMap = new ConcurrentHashMap<>();
        java.util.concurrent.atomic.AtomicReference<String> translatedHtmlRef = new java.util.concurrent.atomic.AtomicReference<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        java.util.concurrent.atomic.AtomicBoolean cancelledFlag = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicBoolean quotaExhaustedFlag = new java.util.concurrent.atomic.AtomicBoolean(false);

        Long tenantId = TenantContext.getCurrentTenant();
        Company tenantCompany = TenantContext.getCurrentTenantEntity();
        String langName = ctx.getLangName();
        SystemUser owner = ctx.getOwner();

        int totalTasks = ctx.getUncachedTextMap().size()
                + ctx.getUncachedImageData().size()
                + (ctx.getUncachedHtml() != null ? 1 : 0);
        java.util.concurrent.atomic.AtomicInteger completedTasks = new java.util.concurrent.atomic.AtomicInteger(0);

        // 文本任务
        for (Map.Entry<String, String> entry : ctx.getUncachedTextMap().entrySet()) {
            String hash = entry.getKey();
            String sourceText = entry.getValue();
            final int total = totalTasks;
            futures.add(CompletableFuture.runAsync(() -> {
                if (shutdownRequested || cancelledFlag.get() || quotaExhaustedFlag.get()) return;
                TenantContext.setCurrentTenant(tenantId, tenantCompany);
                try {
                    java.util.concurrent.atomic.AtomicReference<GeminiTranslateService.TokenUsage> usageRef =
                            new java.util.concurrent.atomic.AtomicReference<>();
                    String translated = callWithRateLimitAndRetry(() -> {
                        checkCancelledBeforeApiCall(cancelledFlag, task);
                        return geminiTranslateService.translateTextRaw(sourceText, langName, usageRef::set);
                    });
                    if (translated != null && !translated.isBlank()) {
                        translatedTextMap.put(hash, translated);
                        writeSingleTextCache(sourceText, translated, ctx.getLanguage());
                    }
                    if (!shutdownRequested && !cancelledFlag.get()) {
                        saveTokenUsageRecord(task.getId(), TranslationContentType.TEXT, hash, langName,
                                false, InvokeMode.STANDARD, usageRef.get(), false, null, null, null, owner);
                    }
                } catch (DailyQuotaExhaustedException e) {
                    quotaExhaustedFlag.set(true);
                    throw e;
                } catch (java.util.concurrent.CancellationException e) {
                    log.debug("[directTranslate] taskId={} text hash={} 任务已取消, 跳过", task.getId(), hash);
                } catch (Exception e) {
                    log.warn("[directTranslate] taskId={} text hash={} 翻译失败", task.getId(), hash, e);
                } finally {
                    if (!shutdownRequested && !cancelledFlag.get() && !quotaExhaustedFlag.get()) {
                        if (!updateDirectTranslateProgress(task, completedTasks.incrementAndGet(), total)) {
                            cancelledFlag.set(true);
                        }
                    }
                    TenantContext.clear();
                }
            }, translationExecutor));
        }

        // HTML 任务
        if (ctx.getUncachedHtml() != null) {
            String htmlHash = DigestUtil.sha256Hex(ctx.getUncachedHtml());
            final int total = totalTasks;
            futures.add(CompletableFuture.runAsync(() -> {
                if (shutdownRequested || cancelledFlag.get() || quotaExhaustedFlag.get()) return;
                TenantContext.setCurrentTenant(tenantId, tenantCompany);
                try {
                    java.util.concurrent.atomic.AtomicReference<GeminiTranslateService.TokenUsage> usageRef =
                            new java.util.concurrent.atomic.AtomicReference<>();
                    String html = callWithRateLimitAndRetry(() -> {
                        checkCancelledBeforeApiCall(cancelledFlag, task);
                        return geminiTranslateService.translateHtmlRaw(ctx.getUncachedHtml(), langName, usageRef::set);
                    });
                    if (html != null) {
                        translatedHtmlRef.set(html);
                        writeHtmlTranslationCache(ctx.getUncachedHtml(), html, ctx.getLanguage());
                    }
                    if (!shutdownRequested && !cancelledFlag.get()) {
                        saveTokenUsageRecord(task.getId(), TranslationContentType.HTML, htmlHash, langName,
                                false, InvokeMode.STANDARD, usageRef.get(), false, null, null, null, owner);
                    }
                } catch (DailyQuotaExhaustedException e) {
                    quotaExhaustedFlag.set(true);
                    throw e;
                } catch (java.util.concurrent.CancellationException e) {
                    log.debug("[directTranslate] taskId={} HTML 任务已取消, 跳过", task.getId());
                } catch (Exception e) {
                    log.warn("[directTranslate] taskId={} HTML 翻译失败", task.getId(), e);
                } finally {
                    if (!shutdownRequested && !cancelledFlag.get() && !quotaExhaustedFlag.get()) {
                        if (!updateDirectTranslateProgress(task, completedTasks.incrementAndGet(), total)) {
                            cancelledFlag.set(true);
                        }
                    }
                    TenantContext.clear();
                }
            }, translationExecutor));
        }

        // 图片任务
        for (Map.Entry<String, byte[]> entry : ctx.getUncachedImageData().entrySet()) {
            String hash = entry.getKey();
            byte[] imgBytes = entry.getValue();
            String mimeType = ctx.getUncachedImageMimeTypes().get(hash);
            MultimediaFile sourceFile = ctx.getImageHashToSourceFile().get(hash);
            final int total = totalTasks;
            futures.add(CompletableFuture.runAsync(() -> {
                if (shutdownRequested || cancelledFlag.get() || quotaExhaustedFlag.get()) return;
                TenantContext.setCurrentTenant(tenantId, tenantCompany);
                try {
                    java.util.concurrent.atomic.AtomicReference<GeminiTranslateService.TokenUsage> usageRef =
                            new java.util.concurrent.atomic.AtomicReference<>();
                    byte[] result = callWithRateLimitAndRetry(() -> {
                        checkCancelledBeforeApiCall(cancelledFlag, task);
                        return geminiTranslateService.translateImageRaw(imgBytes, mimeType, langName, usageRef::set);
                    });
                    if (result != null && sourceFile != null) {
                        MultimediaFile newFile = multimediaFileService.saveTranslatedImage(
                                result, sourceFile.getSuffix(), ctx.getOwner());
                        translatedImageMap.put(hash, newFile);
                        saveImageTranslationCache(hash, sourceFile, ctx.getLanguage(), newFile, false);
                        if (!shutdownRequested && !cancelledFlag.get()) {
                            saveTokenUsageRecord(task.getId(), TranslationContentType.IMAGE, hash, langName,
                                    false, InvokeMode.STANDARD, usageRef.get(), true, null, null, null, owner);
                        }
                    } else if (sourceFile != null) {
                        saveImageTranslationCache(hash, sourceFile, ctx.getLanguage(), null, true);
                        if (!shutdownRequested && !cancelledFlag.get()) {
                            saveNoOutputImageTokenRecord(task.getId(), hash, langName,
                                    InvokeMode.STANDARD, usageRef.get(), sourceFile, owner);
                        }
                    }
                } catch (DailyQuotaExhaustedException e) {
                    quotaExhaustedFlag.set(true);
                    throw e;
                } catch (java.util.concurrent.CancellationException e) {
                    log.debug("[directTranslate] taskId={} img hash={} 任务已取消, 跳过", task.getId(), hash);
                } catch (Exception e) {
                    log.warn("[directTranslate] taskId={} img hash={} 翻译失败", task.getId(), hash, e);
                } finally {
                    if (!shutdownRequested && !cancelledFlag.get() && !quotaExhaustedFlag.get()) {
                        if (!updateDirectTranslateProgress(task, completedTasks.incrementAndGet(), total)) {
                            cancelledFlag.set(true);
                        }
                    }
                    TenantContext.clear();
                }
            }, translationExecutor));
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (java.util.concurrent.CompletionException e) {
            if (e.getCause() instanceof DailyQuotaExhaustedException dqe) {
                throw dqe;
            }
            throw e;
        }

        log.info("[directTranslate] taskId={} 并发翻译完成: texts={}, images={}, html={}",
                task.getId(), translatedTextMap.size(), translatedImageMap.size(),
                translatedHtmlRef.get() != null ? "OK" : "null");

        return TranslateResult.builder()
                .translatedTextMap(new HashMap<>(translatedTextMap))
                .translatedImageMap(new HashMap<>(translatedImageMap))
                .translatedHtml(translatedHtmlRef.get())
                .build();
    }

    private <T> T callWithRateLimitAndRetry(java.util.function.Supplier<T> supplier) {
        java.util.function.Supplier<T> rateLimited = RateLimiter.decorateSupplier(geminiRateLimiter, supplier);
        java.util.function.Supplier<T> retried = Retry.decorateSupplier(geminiDirectRetry, rateLimited);
        return retried.get();
    }

    /**
     * 即时翻译进度更新：将完成比例映射到 5%~95% 区间。
     * synchronized 保证多线程串行更新，updateAsyncTask 内部会自动 getById 取最新版本。
     */
    private synchronized boolean updateDirectTranslateProgress(AsyncTask task, int completed, int total) {
        try {
            if (total <= 0) return true;
            int progress = 5 + (int) ((completed * 90.0) / total);
            progress = Math.min(progress, 95);
            task.setMessage(String.format("即时翻译中: %d/%d 已完成", completed, total));
            return asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, progress);
        } catch (Exception e) {
            log.debug("[directTranslate] 更新进度失败: taskId={}", task.getId(), e);
            return true;
        }
    }

    // ======================== Phase D: 保存新产品 ========================

    private void saveTranslatedProduct(AsyncTask task, TranslateContext ctx, TranslateResult result) throws Exception {
        AsyncTask preSave = asyncTaskService.getById(task.getId());
        if (preSave.getState() == TaskState.CANCELLED) {
            log.info("[save] taskId={} 保存前任务已取消", task.getId());
            return;
        }

        task.setMessage("正在保存翻译结果...");
        asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 97);

        Map<String, String> finalTextMap = new HashMap<>(ctx.getCachedTextMap());
        finalTextMap.putAll(result.getTranslatedTextMap());

        String finalHtml = ctx.getCachedTranslatedHtml();
        if (result.getTranslatedHtml() != null) {
            finalHtml = result.getTranslatedHtml();
        }
        if (finalHtml == null) {
            finalHtml = ctx.getIntroduction();
        }

        Map<String, MultimediaFile> allImageResults = new HashMap<>(ctx.getCachedImageMap());
        allImageResults.putAll(result.getTranslatedImageMap());
        Map<String, MultimediaFile> finalImageMap = buildFinalImageMap(ctx.getImageIdToHash(), allImageResults);

        productService.assembleTranslatedProduct(
                ctx.getProduct(), ctx.getLanguage(), ctx.getCountry(), ctx.getOwner(),
                finalTextMap, finalHtml, finalImageMap);

        task.setMessage("翻译完成");
        asyncTaskService.updateAsyncTask(task, TaskState.COMPLETED, COMPLETED_OR_FAILED_PROGRESS);
    }

    private Map<String, MultimediaFile> buildFinalImageMap(
            Map<String, String> imageIdToHash, Map<String, MultimediaFile> hashToFile) {
        Map<String, MultimediaFile> result = new HashMap<>();
        for (Map.Entry<String, String> entry : imageIdToHash.entrySet()) {
            MultimediaFile file = hashToFile.get(entry.getValue());
            if (file != null) {
                result.put(entry.getKey(), file);
            }
        }
        return result;
    }

    private TranslateResult mergeResults(TranslateResult base, TranslateResult extra) {
        Map<String, String> texts = new HashMap<>(base.getTranslatedTextMap());
        texts.putAll(extra.getTranslatedTextMap());
        Map<String, MultimediaFile> images = new HashMap<>(base.getTranslatedImageMap());
        images.putAll(extra.getTranslatedImageMap());
        String html = extra.getTranslatedHtml() != null ? extra.getTranslatedHtml() : base.getTranslatedHtml();
        return TranslateResult.builder().translatedTextMap(texts).translatedImageMap(images).translatedHtml(html).build();
    }

    // ======================== 辅助方法 ========================

    private boolean isCancelled(AsyncTask task) {
        return asyncTaskService.getById(task.getId()).getState() == TaskState.CANCELLED;
    }

    private void checkCancelledBeforeApiCall(java.util.concurrent.atomic.AtomicBoolean cancelledFlag, AsyncTask task) {
        if (cancelledFlag.get() || isCancelled(task)) {
            cancelledFlag.set(true);
            throw new java.util.concurrent.CancellationException("任务已取消");
        }
    }

    private Map<String, String> collectTextsToTranslate(Product product) {
        Map<String, String> uniqueTextMap = new LinkedHashMap<>();
        addTextIfPresent(uniqueTextMap, product.getTitle());
        addTextIfPresent(uniqueTextMap, product.getSummary());
        addTextIfPresent(uniqueTextMap, product.getWaybillProductName());
        for (ProductSpecification spec : product.getSpecificationList()) {
            for (ProductSpecificationAttributes attr : spec.getAttributes()) {
                addTextIfPresent(uniqueTextMap, attr.getName());
                addTextIfPresent(uniqueTextMap, attr.getValue());
            }
        }
        return uniqueTextMap;
    }

    private void addTextIfPresent(Map<String, String> map, String text) {
        if (text != null && !text.isBlank()) {
            map.putIfAbsent(DigestUtil.sha256Hex(text), text);
        }
    }

    private List<String> collectImageIds(Product product) {
        Set<String> dedup = new LinkedHashSet<>();
        if (product.getImageFiles() != null) {
            for (MultimediaFile img : product.getImageFiles()) {
                if (img != null && img.getId() != null && !"gif".equalsIgnoreCase(img.getSuffix()))
                    dedup.add(String.valueOf(img.getId()));
            }
        }
        for (ProductSpecification spec : product.getSpecificationList()) {
            MultimediaFile specImg = spec.getSpecificationImage();
            if (specImg != null && specImg.getId() != null && !"gif".equalsIgnoreCase(specImg.getSuffix()))
                dedup.add(String.valueOf(specImg.getId()));
            for (ProductSpecificationAttributes attr : spec.getAttributes()) {
                MultimediaFile attrImg = attr.getMultimediaFile();
                if (attrImg != null && attrImg.getId() != null && !"gif".equalsIgnoreCase(attrImg.getSuffix()))
                    dedup.add(String.valueOf(attrImg.getId()));
            }
        }
        if (product.getIntroduction() != null) {
            Matcher matcher = IMG_ID_PATTERN.matcher(product.getIntroduction());
            while (matcher.find()) dedup.add(matcher.group(1));
        }
        return new ArrayList<>(dedup);
    }

    private boolean isAnimatedWebp(byte[] data) {
        if (data.length < 20) return false;
        if (data[0] != 'R' || data[1] != 'I' || data[2] != 'F' || data[3] != 'F') return false;
        if (data[8] != 'W' || data[9] != 'E' || data[10] != 'B' || data[11] != 'P') return false;
        if (data.length > 20 && data[12] == 'V' && data[13] == 'P' && data[14] == '8' && data[15] == 'X') {
            return (data[20] & 0x02) != 0;
        }
        for (int i = 12; i < data.length - 4; i++) {
            if (data[i] == 'A' && data[i + 1] == 'N' && data[i + 2] == 'I' && data[i + 3] == 'M') return true;
        }
        return false;
    }

    private String extractTextFromResponse(JsonNode responseNode) {
        try {
            JsonNode candidates = responseNode.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray()) {
                    StringBuilder sb = new StringBuilder();
                    for (JsonNode part : parts) {
                        if (part.has("text")) sb.append(part.get("text").asText());
                    }
                    return sb.toString();
                }
            }
        } catch (Exception e) {
            log.warn("[extractTextFromResponse] 解析失败", e);
        }
        return null;
    }

    private byte[] extractImageFromResponse(JsonNode responseNode) {
        try {
            JsonNode candidates = responseNode.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray()) {
                    for (JsonNode part : parts) {
                        if (part.has("inlineData")) {
                            String base64 = part.path("inlineData").path("data").asText();
                            if (base64 != null && !base64.isEmpty()) return Base64.getDecoder().decode(base64);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[extractImageFromResponse] 解析失败", e);
        }
        return null;
    }

    private void cleanupBatchResources(String jobName, String uploadedFileName) {
        try { if (uploadedFileName != null) geminiTranslateService.deleteFile(uploadedFileName); }
        catch (Exception e) { log.warn("[cleanupBatch] 清理上传文件失败", e); }
        try { if (jobName != null) geminiTranslateService.deleteBatchJob(jobName); }
        catch (Exception e) { log.warn("[cleanupBatch] 删除 batch job 失败", e); }
    }

    // ======================== 翻译缓存辅助方法 ========================

    private void saveImageTranslationCache(String imageHash, MultimediaFile sourceFile, Language language,
                                           MultimediaFile translatedFile, boolean skipped) {
        try {
            if (imageHash == null || imageHash.isBlank()) return;
            ImageTranslationCache record = ImageTranslationCache.builder()
                    .imageHash(imageHash).sourceFile(sourceFile).language(language)
                    .translatedFile(translatedFile).skipped(skipped).build();
            imageTranslationCacheRepository.save(record);
        } catch (DataIntegrityViolationException e) {
            log.debug("[imageCache] 缓存已存在(并发写入): hash={}", imageHash);
        } catch (Exception e) {
            log.warn("[imageCache] 写入缓存失败: hash={}", imageHash, e);
        }
    }

    private String lookupSingleTextCache(String text, Language language) {
        try {
            String hash = DigestUtil.sha256Hex(text);
            Optional<TextTranslationCache> cached = textTranslationCacheRepository
                    .findByContentHashAndLanguageIdAndContentType(hash, language.getId(), TranslationContentType.TEXT);
            if (cached.isPresent()) {
                String translated = cached.get().getTranslatedText();
                if (translated != null && !translated.isBlank()) return translated;
            }
        } catch (Exception e) {
            log.warn("[textCache] 查询失败: text.length={}", text.length(), e);
        }
        return null;
    }

    private void writeSingleTextCache(String source, String translated, Language language) {
        try {
            String hash = DigestUtil.sha256Hex(source);
            Optional<TextTranslationCache> existing = textTranslationCacheRepository
                    .findByContentHashAndLanguageIdAndContentType(hash, language.getId(), TranslationContentType.TEXT);
            if (existing.isPresent()) return;
            textTranslationCacheRepository.save(TextTranslationCache.builder()
                    .contentHash(hash).language(language).contentType(TranslationContentType.TEXT)
                    .sourceText(source).translatedText(translated).build());
        } catch (DataIntegrityViolationException e) {
            log.debug("[textCache] 缓存已存在(并发写入)");
        } catch (Exception e) {
            log.warn("[textCache] 写入缓存失败", e);
        }
    }

    private String lookupHtmlCache(String html, Language language) {
        try {
            String hash = DigestUtil.sha256Hex(html);
            Optional<TextTranslationCache> cached = textTranslationCacheRepository
                    .findByContentHashAndLanguageIdAndContentType(hash, language.getId(), TranslationContentType.HTML);
            if (cached.isPresent()) {
                String translated = cached.get().getTranslatedText();
                if (translated != null && !translated.isBlank()) return translated;
            }
        } catch (Exception e) {
            log.warn("[htmlCache] 查询失败", e);
        }
        return null;
    }

    private void writeHtmlTranslationCache(String source, String translated, Language language) {
        try {
            String hash = DigestUtil.sha256Hex(source);
            Optional<TextTranslationCache> existing = textTranslationCacheRepository
                    .findByContentHashAndLanguageIdAndContentType(hash, language.getId(), TranslationContentType.HTML);
            if (existing.isPresent()) return;
            textTranslationCacheRepository.save(TextTranslationCache.builder()
                    .contentHash(hash).language(language).contentType(TranslationContentType.HTML)
                    .sourceText(source.length() > 65535 ? source.substring(0, 65535) : source)
                    .translatedText(translated).build());
        } catch (DataIntegrityViolationException e) {
            log.debug("[htmlCache] 缓存已存在(并发写入)");
        } catch (Exception e) {
            log.warn("[htmlCache] 写入缓存失败", e);
        }
    }

    // ======================== AI Token 使用记录写入 ========================

    private void saveTokenUsageRecord(Long taskId, TranslationContentType contentType, String contentHash,
                                      String targetLanguage, boolean cacheHit, InvokeMode invokeMode,
                                      GeminiTranslateService.TokenUsage actual, boolean hasImageOutput,
                                      Integer bizPrompt, Integer bizCompletion, Integer bizThinking,
                                      SystemUser owner) {
        try {
            int aPrompt = actual != null && actual.getPromptTokens() != null ? actual.getPromptTokens() : 0;
            int aCompletion = actual != null && actual.getCompletionTokens() != null ? actual.getCompletionTokens() : 0;
            int aThinking = actual != null && actual.getThinkingTokens() != null ? actual.getThinkingTokens() : 0;
            int aTotal = actual != null && actual.getTotalTokens() != null ? actual.getTotalTokens() : 0;
            Long elapsed = actual != null ? actual.getElapsedMs() : null;

            int bPrompt = bizPrompt != null ? bizPrompt : aPrompt;
            int bCompletion = bizCompletion != null ? bizCompletion : aCompletion;
            int bThinking = bizThinking != null ? bizThinking : aThinking;
            int bTotal = bPrompt + bCompletion + bThinking;

            BigDecimal actualCost = TokenCostCalculator.calculateCost(
                    contentType, invokeMode, aPrompt, aCompletion, aThinking);
            BigDecimal businessCost = TokenCostCalculator.calculateCost(
                    contentType, invokeMode, bPrompt, bCompletion, bThinking);

            int businessCredits = TokenCostCalculator.usdToCredits(businessCost);

            AiTokenUsageRecord record = AiTokenUsageRecord.builder()
                    .taskId(taskId)
                    .contentType(contentType)
                    .contentHash(contentHash)
                    .targetLanguage(targetLanguage)
                    .cacheHit(cacheHit)
                    .model(geminiTranslateService.getModel())
                    .invokeMode(invokeMode)
                    .actualPromptTokens(aPrompt)
                    .actualCompletionTokens(aCompletion)
                    .actualThinkingTokens(aThinking)
                    .actualTotalTokens(aTotal)
                    .businessPromptTokens(bPrompt)
                    .businessCompletionTokens(bCompletion)
                    .businessThinkingTokens(bThinking)
                    .businessTotalTokens(bTotal)
                    .actualCost(actualCost)
                    .businessCost(businessCost)
                    .businessCredits(businessCredits)
                    .elapsedMs(elapsed)
                    .hasImageOutput(hasImageOutput)
                    .build();
            record.setOwner(owner);
            aiTokenUsageRecordRepository.save(record);
        } catch (Exception e) {
            log.warn("[tokenUsage] 写入 token 记录失败: taskId={}, hash={}", taskId, contentHash, e);
        }
    }

    /**
     * 缓存命中场景：查历史同 contentHash+targetLanguage 首次翻译记录，复制业务 token。
     */
    private void saveCacheHitTokenRecord(Long taskId, TranslationContentType contentType,
                                         String contentHash, String targetLanguage, InvokeMode invokeMode,
                                         SystemUser owner) {
        try {
            Optional<AiTokenUsageRecord> historyOpt = aiTokenUsageRecordRepository
                    .findFirstByContentHashAndTargetLanguageAndCacheHitFalseOrderByCreateTimeDesc(contentHash, targetLanguage);
            Integer bizPrompt = null, bizCompletion = null, bizThinking = null;
            boolean hasImageOutput = false;
            if (historyOpt.isPresent()) {
                AiTokenUsageRecord history = historyOpt.get();
                bizPrompt = history.getActualPromptTokens();
                bizCompletion = history.getActualCompletionTokens();
                bizThinking = history.getActualThinkingTokens();
                hasImageOutput = Boolean.TRUE.equals(history.getHasImageOutput());
            }
            saveTokenUsageRecord(taskId, contentType, contentHash, targetLanguage,
                    true, invokeMode, null, hasImageOutput, bizPrompt, bizCompletion, bizThinking, owner);
        } catch (Exception e) {
            log.warn("[tokenUsage] 缓存命中记录写入失败: taskId={}, hash={}", taskId, contentHash, e);
        }
    }

    /**
     * 首次翻译图片但无图片输出（2b）：实际 token 从 API，业务输入=实际输入，业务输出=档位公式。
     */
    private void saveNoOutputImageTokenRecord(Long taskId, String contentHash, String targetLanguage,
                                              InvokeMode invokeMode, GeminiTranslateService.TokenUsage actual,
                                              MultimediaFile sourceFile, SystemUser owner) {
        try {
            int maxDim = Math.max(sourceFile.getWidth(), sourceFile.getHeight());
            if (maxDim <= 0) maxDim = 512;
            int bizPrompt = actual != null && actual.getPromptTokens() != null ? actual.getPromptTokens() : 0;
            int bizCompletion = TokenCostCalculator.imageBusinessCompletionTokens(maxDim);
            saveTokenUsageRecord(taskId, TranslationContentType.IMAGE, contentHash, targetLanguage,
                    false, invokeMode, actual, false, bizPrompt, bizCompletion, 0, owner);
        } catch (Exception e) {
            log.warn("[tokenUsage] 无输出图片记录写入失败: taskId={}, hash={}", taskId, contentHash, e);
        }
    }

    /**
     * Phase A 完成后，为缓存命中的文本/HTML/图片写入业务 token 记录。
     */
    private void writeCacheHitTokenRecords(AsyncTask task, TranslateContext ctx, InvokeMode invokeMode) {
        String langName = ctx.getLangName();
        SystemUser owner = ctx.getOwner();

        // 缓存命中的文本
        for (String hash : ctx.getCachedTextMap().keySet()) {
            saveCacheHitTokenRecord(task.getId(), TranslationContentType.TEXT, hash, langName, invokeMode, owner);
        }

        // 缓存命中的 HTML
        if (ctx.getCachedTranslatedHtml() != null && ctx.getIntroduction() != null) {
            String htmlHash = DigestUtil.sha256Hex(ctx.getIntroduction());
            saveCacheHitTokenRecord(task.getId(), TranslationContentType.HTML, htmlHash, langName, invokeMode, owner);
        }

        // 缓存命中的图片（cachedImageMap 中非 null 值 = 有翻译后的文件）
        for (Map.Entry<String, MultimediaFile> entry : ctx.getCachedImageMap().entrySet()) {
            saveCacheHitTokenRecord(task.getId(), TranslationContentType.IMAGE, entry.getKey(), langName, invokeMode, owner);
        }
    }

    private void initTenantContext(AsyncTask task) {
        Long companyId = task.getCompanyId();
        if (companyId != null) {
            Company company = companyService.companyCached(companyId);
            if (company != null) {
                TenantContext.setCurrentTenant(companyId, company);
            } else {
                log.warn("[initTenantContext] taskId={} 未找到 company: companyId={}", task.getId(), companyId);
            }
        } else {
            log.warn("[initTenantContext] taskId={} companyId 为 null", task.getId());
        }
    }

    // ======================== 非翻译任务（订单下载/上传/第三方同步） ========================

    private void executeOrderUpload(AsyncTask task, SystemUserDto owner) {
        final List<String> successIds = new ArrayList<>();
        final List<String> errorMsgList = new ArrayList<>();
        try {
            String filePath = task.getUploadFilePath();
            task.setMessage("正在处理");
            asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, RUNNING_PROGRESS);
            if (!FileUtil.exist(filePath)) {
                task.setMessage("上传文件不存在");
                asyncTaskService.updateAsyncTask(task, TaskState.FAILED, COMPLETED_OR_FAILED_PROGRESS);
                return;
            }
            final int totalRows = getTotalRowCount(filePath, 0);
            final Map<String, Integer> rowNameMap = new HashMap<>();
            ExcelUtil.readBySax(filePath, 0, (sheetIndex, rowIndex, rowCells) -> {
                if (rowIndex == 0) {
                    for (int i = 0; i < rowCells.size(); i++) {
                        if (rowCells.get(i) == null || StrUtil.isBlank(rowCells.get(i).toString())) continue;
                        String title = rowCells.get(i).toString();
                        if (!rowNameMap.containsKey(title)) rowNameMap.put(title, i);
                    }
                    return;
                }
                try {
                    OrderCheckInfoDto orderCheckInfoDto = BeanUtil.toBean(OrderCheckInfoDto.class, new ValueProvider<>() {
                        @Override
                        public Object value(String key, Type valueType) {
                            return rowCells.get(rowNameMap.get(OrderCheckInfoDto.KEY_MAPPING.get(key)));
                        }
                        @Override
                        public boolean containsKey(String key) {
                            return rowNameMap.containsKey(OrderCheckInfoDto.KEY_MAPPING.get(key));
                        }
                    }, CopyOptions.create());
                    Optional<Order> orderOption = Optional.empty();
                    if (ConvertUtils.isLong(orderCheckInfoDto.getOrderId())) {
                        orderOption = orderService.findById(ConvertUtils.parseLong(orderCheckInfoDto.getOrderId()));
                    }
                    if (orderOption.isEmpty()) {
                        orderOption = orderService.findByOriginOrderId(orderCheckInfoDto.getOrderId());
                    }
                    if (orderOption.isEmpty()) {
                        throw ClientResponseEnum.PARAMETER_ILLEGAL.newException("订单不存在");
                    }
                    Order order = orderOption.get();
                    if (!owner.isSuperAdmin() && !Objects.equals(order.getCompanyId(), owner.getCompanyId())) {
                        ClientResponseEnum.NO_PERMISSION.throwException("权限不足");
                    }
                    orderCheckInfoDto.fillChangeOrder(order);
                    orderService.saveAndFlush(order);
                    successIds.add(orderCheckInfoDto.getOrderId());
                } catch (Exception e) {
                    errorMsgList.add("第" + rowIndex + "行: " + e.getMessage());
                } finally {
                    int percent = (int) (((rowIndex + 1) * 100.0) / totalRows);
                    asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, Math.max(0, Math.min(100, percent)));
                }
            });
            task.setMessage("上传成功订单列表: \n" + ArrayUtil.join(successIds.toArray(), "\n") + "\n上传失败列表: \n" + ArrayUtil.join(errorMsgList.toArray(), "\n"));
            asyncTaskService.updateAsyncTask(task, TaskState.COMPLETED, COMPLETED_OR_FAILED_PROGRESS);
        } catch (Exception e) {
            log.error("执行失败: ", e);
            task.setMessage(e.getMessage() + (successIds.isEmpty() ? "" : ("\n上传成功订单列表: \n" + ArrayUtil.join(successIds.toArray(), "\n") + "\n上传失败列表: \n" + ArrayUtil.join(errorMsgList.toArray(), "\n"))));
            asyncTaskService.updateAsyncTask(task, TaskState.FAILED, COMPLETED_OR_FAILED_PROGRESS);
        }
    }

    private void executeOrderDownload(AsyncTask task, SystemUserDto owner) {
        try {
            task.setMessage("正在处理");
            asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, RUNNING_PROGRESS);
            String parameters = task.getParameters();
            DownloadOrderRequest request = JSONUtil.toBean(parameters, DownloadOrderRequest.class);
            request.setPageNo(1);
            request.setPageSize(BATCH_SIZE);

            BigExcelWriter writer = ExcelUtil.getBigWriter(BATCH_SIZE);
            Font font = writer.createFont();
            font.setFontName("Calibri");
            font.setFontHeightInPoints((short) 11);
            writer.getStyleSet().setFont(font, false);
            writer.getStyleSet().setBorder(BorderStyle.NONE, IndexedColors.GREY_25_PERCENT);
            writer.getStyleSet().setAlign(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

            Map<String, String> headerAliasMap = orderTemplateService.getHeaderAliasMap(request.getTemplateId(), request.getIsAudit());
            writer.setHeaderAlias(headerAliasMap);
            writer.setOnlyAlias(true);
            while (true) {
                Page<Order> paginated = orderService.findPaginated(OrderQueryHelper.convertOrderQueryPageRequest(request, orderService), owner, task.getViewMode());
                asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, printPaginationProgressMax99(paginated));
                writer.write(paginated.map(OrderDownloadDto::convert).stream().map(BeanUtil::trimStrFields).toList());
                if (!paginated.hasNext()) break;
                request.setPageNo(request.getPageNo() + 1);
            }

            task.setMessage("执行成功");
            asyncTaskService.updateAsyncTask(task, TaskState.RESOLVED, RESOLVE_PROGRESS);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            writer.autoSizeColumnAll();
            writer.flush(outputStream, true);
            LocalDateTime createTime = LocalDateTime.now();
            String relativePath = "async-task/" + owner.getCompanyId() + "/" + DateUtil.format(createTime, "yyyy") + "/" + DateUtil.format(createTime, "MM") + "/" + DateUtil.format(createTime, "dd") + "/" + IdUtil.simpleUUID() + ".xlsx";
            s3Service.uploadExcel(outputStream.toByteArray(), relativePath);
            task.setMessage("执行完成");
            task.setExportRelativePath(relativePath);
            asyncTaskService.updateAsyncTask(task, TaskState.COMPLETED, COMPLETED_OR_FAILED_PROGRESS);
        } catch (Throwable e) {
            log.error("执行失败: ", e);
            task.setMessage(e.getMessage());
            asyncTaskService.updateAsyncTask(task, TaskState.FAILED, COMPLETED_OR_FAILED_PROGRESS);
        }
    }

    private void executeThirdPartyOrderSyncUpload(AsyncTask task, SystemUserDto owner) {
        try {
            task.setMessage("正在处理");
            asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, RUNNING_PROGRESS);
            String parameters = task.getParameters();
            SyncThirdPartyOrdersRequest request = JSONUtil.toBean(parameters, SyncThirdPartyOrdersRequest.class);
            String pageInfo = "";
            while (pageInfo != null) {
                thirdPartyWebsiteService.loadOrders(request, pageInfo);
                asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 0);
                pageInfo = null;
            }
            task.setMessage("执行成功");
            asyncTaskService.updateAsyncTask(task, TaskState.RESOLVED, RESOLVE_PROGRESS);
        } catch (Throwable e) {
            log.error("执行失败: ", e);
            task.setMessage(e.getMessage());
            asyncTaskService.updateAsyncTask(task, TaskState.FAILED, COMPLETED_OR_FAILED_PROGRESS);
        }
    }

    private <T> int printPaginationProgressMax99(Page<T> paginated) {
        int currentPage = paginated.getNumber();
        int totalPages = paginated.getTotalPages();
        double progress = (currentPage + 1.0) / totalPages;
        double mappingProgress = RUNNING_PROGRESS + (RESOLVE_PROGRESS - RUNNING_PROGRESS) * progress;
        return (int) Math.max(RUNNING_PROGRESS, Math.min(RESOLVE_PROGRESS, mappingProgress));
    }

    public static int getTotalRowCount(String filePath, int sheetIndex) {
        try (InputStream is = new FileInputStream(filePath)) {
            Workbook workbook = WorkbookFactory.create(is);
            Sheet sheet = workbook.getSheetAt(sheetIndex);
            return sheet.getLastRowNum() + 1;
        } catch (Exception e) {
            throw new RuntimeException("读取总行数失败", e);
        }
    }
}
