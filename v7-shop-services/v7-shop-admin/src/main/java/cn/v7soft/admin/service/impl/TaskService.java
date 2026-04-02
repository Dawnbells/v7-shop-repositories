package cn.v7soft.admin.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import cn.v7soft.admin.controller.resp.AsyncTaskResponse;
import cn.v7soft.admin.service.IAsyncTaskService;
import cn.v7soft.admin.service.IOrderService;
import cn.v7soft.admin.service.IOrderTemplateService;
import cn.v7soft.admin.service.IProductService;
import cn.v7soft.admin.service.IS3Service;
import cn.v7soft.admin.service.ITaskService;
import cn.v7soft.admin.service.IThirdPartyWebsiteService;
import cn.v7soft.admin.service.dto.OrderCheckInfoDto;
import cn.v7soft.admin.service.dto.OrderDownloadDto;
import cn.v7soft.admin.utils.OrderQueryHelper;
import cn.v7soft.common.utils.ConvertUtils;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.admin.service.IMultimediaFileService;
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
import cn.v7soft.dao.entities.primary.ImageTranslationCache;
import cn.v7soft.dao.entities.primary.TextTranslationCache;
import cn.v7soft.dao.tenant.TenantContext;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;
import cn.v7soft.dao.enums.TranslationContentType;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import cn.v7soft.dao.repositories.primary.ImageTranslationCacheRepository;
import cn.v7soft.dao.repositories.primary.TextTranslationCacheRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.BatchJob;
import com.google.genai.types.BatchJobDestination;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TaskService implements ITaskService {

    private final static int BATCH_SIZE = 1000;
    private final static int RUNNING_PROGRESS = 1;
    private static final int RESOLVE_PROGRESS = 99;
    private static final int COMPLETED_OR_FAILED_PROGRESS = 100;

    private static final long BATCH_POLL_INTERVAL_MS = 3 * 60 * 1000L;
    private static final long BATCH_TIMEOUT_MS = 30 * 60 * 1000L;
    private static final Pattern IMG_ID_PATTERN = Pattern.compile("/multimedia/([0-9]+)");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> BATCH_COMPLETED_STATES = Set.of(
            "JOB_STATE_SUCCEEDED", "JOB_STATE_FAILED", "JOB_STATE_CANCELLED", "JOB_STATE_EXPIRED");

    /**
     * 翻译任务的准备数据上下文，由 {@link #prepareTranslateContext} 统一构建。
     */
    @lombok.Data
    @lombok.Builder
    private static class TranslateContext {
        private Product product;
        private Language language;
        private Country country;
        private SystemUser owner;
        private String langName;
        private String introduction;

        private Map<String, String> uniqueTextMap;
        private List<String> imgIds;

        private Map<String, byte[]> originalImageBytes;
        private Map<String, String> imageMimeTypes;
        private List<String> translatableImgIds;
        private Map<String, MultimediaFile> preCachedImageMap;
        private Map<String, String> imageHashByImgId;
        private Map<String, List<String>> hashToDeferredImgIds;

        private Map<String, String> cachedTextMap;
        private Map<String, String> uncachedTextMap;
        private String cachedTranslatedHtml;
    }

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
    private final ITaskService self;
    private final ImageTranslationCacheRepository imageTranslationCacheRepository;
    private final TextTranslationCacheRepository textTranslationCacheRepository;
    private final cn.v7soft.admin.service.ICompanyService companyService;

    public TaskService(IAsyncTaskService asyncTaskService, @Lazy IOrderService orderService, IS3Service s3Service,
                       @Lazy IThirdPartyWebsiteService thirdPartyWebsiteService, IOrderTemplateService orderTemplateService,
                       @Lazy IProductService productService, GeminiTranslateService geminiTranslateService,
                       IMultimediaFileService multimediaFileService, cn.v7soft.admin.service.ILanguageService languageService,
                       cn.v7soft.admin.service.ICountryService countryService,
                       AsyncTaskRepository asyncTaskRepository, TranslateTaskMetrics translateTaskMetrics,
                       @Lazy ITaskService self,
                       ImageTranslationCacheRepository imageTranslationCacheRepository,
                       TextTranslationCacheRepository textTranslationCacheRepository,
                       cn.v7soft.admin.service.ICompanyService companyService) {
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
    }

    @Override
    public AsyncTaskResponse status(Long taskId) {
        AsyncTask task = asyncTaskService.getById(taskId);
        return AsyncTaskResponse.convert(task);
    }

    @Override
    public AsyncTaskResponse cancel(Long taskId) {
        AsyncTask task = asyncTaskService.getById(taskId);
        log.info("[cancel] taskId={} 请求取消, 当前状态={}, taskType={}, batchJobName={}",
                taskId, task.getState(), task.getTaskType(), task.getBatchJobName());
        if (task.getState() == TaskState.PENDING || task.getState() == TaskState.PROCESSING) {
            task.setMessage("任务已取消");
            asyncTaskService.updateAsyncTask(task, TaskState.CANCELLED, COMPLETED_OR_FAILED_PROGRESS);
            if (task.getBatchJobName() != null && !task.getBatchJobName().isBlank()) {
                try {
                    geminiTranslateService.cancelBatchJob(task.getBatchJobName());
                    geminiTranslateService.deleteBatchJob(task.getBatchJobName());
                    log.info("[cancel] taskId={} Gemini Batch 资源已清理", taskId);
                } catch (Exception e) {
                    log.warn("[cancel] taskId={} 清理 Gemini Batch 资源失败: {}", taskId, e.getMessage());
                }
            }
            log.info("[cancel] taskId={} 已取消", taskId);
        } else {
            log.info("[cancel] taskId={} 状态为 {}, 不可取消", taskId, task.getState());
        }
        return AsyncTaskResponse.convert(task);
    }

    @Override
    public InputStream download(Long id) {
        AsyncTask task = asyncTaskService.getById(id);
        return s3Service.download(task.getExportRelativePath());
    }

    @Override
    public Page<AsyncTaskResponse> list(TaskState state, int page, int size) {
        Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        Page<AsyncTask> taskPage = (state != null)
                ? asyncTaskRepository.findByStateOrderByCreateTimeDesc(state, pageable)
                : asyncTaskRepository.findAllByOrderByCreateTimeDesc(pageable);
        return taskPage.map(AsyncTaskResponse::convert);
    }

    @Override
    public Page<AsyncTaskResponse> unacknowledged(int page, int size) {
        Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return asyncTaskRepository.findByAcknowledgedFalseOrderByCreateTimeDesc(pageable)
                .map(AsyncTaskResponse::convert);
    }

    @Override
    public void acknowledge(Long taskId) {
        AsyncTask task = asyncTaskService.getById(taskId);
        task.setAcknowledged(true);
        asyncTaskRepository.saveAndFlush(task);
    }

    @Override
    public void acknowledgeAllCompleted() {
        List<AsyncTask> unacked = asyncTaskRepository.findByAcknowledgedFalseOrderByCreateTimeDesc();
        for (AsyncTask task : unacked) {
            if (task.getState() == TaskState.COMPLETED || task.getState() == TaskState.FAILED || task.getState() == TaskState.CANCELLED) {
                task.setAcknowledged(true);
            }
        }
        asyncTaskRepository.saveAllAndFlush(unacked);
    }

    @Override
    public AsyncTaskResponse switchToDirectTranslate(Long taskId) {
        AsyncTask task = asyncTaskService.getById(taskId);
        log.info("[switchToDirectTranslate] taskId={} 请求切换, 当前状态={}, batchJobName={}",
                taskId, task.getState(), task.getBatchJobName());
        if (task.getTaskType() != TaskType.PRODUCT_AI_TRANSLATE) {
            throw new IllegalArgumentException("只有 AI 翻译任务支持此操作");
        }
        if (task.getState() != TaskState.PROCESSING && task.getState() != TaskState.PENDING) {
            throw new IllegalStateException("只有执行中或等待中的任务才能切换模式");
        }
        if (task.getBatchJobName() != null && !task.getBatchJobName().isBlank()) {
            try {
                geminiTranslateService.cancelBatchJob(task.getBatchJobName());
                geminiTranslateService.deleteBatchJob(task.getBatchJobName());
                log.info("[switchToDirectTranslate] taskId={} Batch Job 已清理: {}", taskId, task.getBatchJobName());
            } catch (Exception e) {
                log.warn("[switchToDirectTranslate] taskId={} 清理 Batch Job 失败: {}", taskId, e.getMessage());
            }
        }
        task.setBatchJobName(null);
        task.setMessage("正在切换为即时翻译...");
        asyncTaskService.updateAsyncTask(task, TaskState.PENDING, 0);
        log.info("[switchToDirectTranslate] taskId={} 已重置为 PENDING, 启动即时翻译", taskId);
        self.executeDirectTranslateAsync(task.getId());
        return AsyncTaskResponse.convert(task);
    }

    @Override
    @Transactional
    public AsyncTaskResponse retry(Long taskId) {
        AsyncTask task = asyncTaskService.getById(taskId);
        log.info("[retry] taskId={} 请求重试, 当前状态={}, taskType={}", taskId, task.getState(), task.getTaskType());
        if (task.getState() != TaskState.FAILED && task.getState() != TaskState.CANCELLED) {
            throw new IllegalStateException("只有失败或已取消的任务才能重试");
        }
        task.setBatchJobName(null);
        task.setMessage("正在重试...");
        task.setAcknowledged(false);
        asyncTaskService.updateAsyncTask(task, TaskState.PENDING, 0);
        asyncTaskRepository.resetCreateTime(taskId, java.time.LocalDateTime.now());
        log.info("[retry] taskId={} 已重置为 PENDING, 重新提交任务", taskId);
        self.submitAsyncTask(task.getId());
        task.setCreateTime(java.time.LocalDateTime.now());
        return AsyncTaskResponse.convert(task);
    }

    @Override
    @Async("threadPoolTaskExecutor")
    public void executeDirectTranslateAsync(Long taskId) {
        ThreadUtil.sleep(500);
        Pair<AsyncTask, SystemUserDto> pair = asyncTaskService.getAndInitializeOwner(taskId);
        AsyncTask task = pair.getKey();
        initTenantContext(task);
        try {
            executeProductAITranslateDirect(task);
        } finally {
            TenantContext.clear();
        }
    }

    @Override
    public void recoverUnfinishedTasks() {
        List<AsyncTask> unfinished = asyncTaskRepository.findByStateIn(List.of(TaskState.PENDING, TaskState.PROCESSING));
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
                    self.resumeTranslateTask(task.getId());
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
                executeProductAITranslate(task);
            } else {
                task.setMessage("未知任务类型: " + task.getTaskType());
                asyncTaskService.updateAsyncTask(task, TaskState.FAILED, 100);
            }
            log.debug("任务完成: {}", task.getId());
        } finally {
            TenantContext.clear();
        }
    }

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
                        if (rowCells.get(i) == null || StrUtil.isBlank(rowCells.get(i).toString())) {
                            continue;
                        }
                        String title = rowCells.get(i).toString();
                        if (!rowNameMap.containsKey(title)) {
                            rowNameMap.put(title, i);
                        }
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
                    log.debug("{}, {}, dto is = {}", rowIndex + "/" + totalRows, Thread.currentThread().getName(), JSONUtil.toJsonStr(orderCheckInfoDto));
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
                    log.error("{}: {}, error msg = {}", rowIndex + "/" + totalRows, Thread.currentThread().getName(), e.getMessage());
//                    ClientResponseEnum.PARAMETER_ILLEGAL.throwException("第" + rowIndex + "行: " + e.getMessage());
                    errorMsgList.add("第" + rowIndex + "行: " + e.getMessage());
                } finally {
                    int percent = (int) (((rowIndex + 1) * 100.0) / totalRows);
                    asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, Math.max(0, Math.min(100, percent)));
                }
            });
            log.debug("上传订单成功， 成功{}条，失败{}条\n", successIds.size(), errorMsgList.size());
            task.setMessage("上传成功订单列表: \n" + ArrayUtil.join(successIds.toArray(), "\n") + "\n上传失败列表: \n" + ArrayUtil.join(errorMsgList.toArray(), "\n"));
            asyncTaskService.updateAsyncTask(task, TaskState.COMPLETED, COMPLETED_OR_FAILED_PROGRESS);
        } catch (Exception e) {
            log.error("执行失败: ", e);
            task.setMessage(e.getMessage() + (successIds.isEmpty() ? "" : ("\n上传成功订单列表: \n" + ArrayUtil.join(successIds.toArray(), "\n") + "\n上传失败列表: \n" + ArrayUtil.join(errorMsgList.toArray(), "\n"))));
            asyncTaskService.updateAsyncTask(task, TaskState.FAILED, COMPLETED_OR_FAILED_PROGRESS);
        }
    }

    /**
     * 执行订单下载
     *
     * @param task  订单下载任务
     * @param owner 执行者
     */
    private void executeOrderDownload(AsyncTask task, SystemUserDto owner) {
        try {
            task.setMessage("正在处理");
            asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, RUNNING_PROGRESS);
            // 1、获取订单, 默认2000， 初始化分页
            String parameters = task.getParameters();
            DownloadOrderRequest request = JSONUtil.toBean(parameters, DownloadOrderRequest.class);
            request.setPageNo(1);
            request.setPageSize(BATCH_SIZE);

            BigExcelWriter writer = ExcelUtil.getBigWriter(BATCH_SIZE);

            // 设置字体为 Calibri
            Font font = writer.createFont();
            font.setFontName("Calibri");
            font.setFontHeightInPoints((short) 11); // 设置字体大小
            writer.getStyleSet().setFont(font, false);

            writer.getStyleSet().setBorder(BorderStyle.NONE, IndexedColors.GREY_25_PERCENT);
            writer.getStyleSet().setAlign(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

            // 2、创建内存excel,并写入表头和别名

            Map<String, String> headerAliasMap = orderTemplateService.getHeaderAliasMap(request.getTemplateId(), request.getIsAudit());
            writer.setHeaderAlias(headerAliasMap);
            writer.setOnlyAlias(true);   // 在写入数据时应用样式
            while (true) {
                // 3、查询当前分页数据
                Page<Order> paginated = orderService.findPaginated(OrderQueryHelper.convertOrderQueryPageRequest(request, orderService), owner, task.getViewMode());

                // 4、更新进度
                asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, printPaginationProgressMax99(paginated));

                // 5、批量写入excel
                writer.write(paginated.map(OrderDownloadDto::convert).stream().map(BeanUtil::trimStrFields).toList());

                if (!paginated.hasNext()) {
                    // 已完成，退出
                    break;
                }
                // 6、未完成，继续查询下一页数据
                request.setPageNo(request.getPageNo() + 1);
            }

            task.setMessage("执行成功");
            // 已完成，更新状态
            asyncTaskService.updateAsyncTask(task, TaskState.RESOLVED, RESOLVE_PROGRESS);

            // 使用 Hutool 生成 Excel 并上传s3
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

    /**
     * 同步第三方订单
     *
     * @param task  任务
     * @param owner 操作用户
     */
    private void executeThirdPartyOrderSyncUpload(AsyncTask task, SystemUserDto owner) {
        try {
            task.setMessage("正在处理");
            asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, RUNNING_PROGRESS);
            // 1、获取订单, 默认2000， 初始化分页
            String parameters = task.getParameters();
            SyncThirdPartyOrdersRequest request = JSONUtil.toBean(parameters, SyncThirdPartyOrdersRequest.class);
            String pageInfo = "";
            while (pageInfo != null) {
                // 3、查询当前分页数据
                thirdPartyWebsiteService.loadOrders(request, pageInfo);

                // 4、更新进度
                asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 0);

                pageInfo = null;
            }
            task.setMessage("执行成功");
            // 已完成，更新状态
            asyncTaskService.updateAsyncTask(task, TaskState.RESOLVED, RESOLVE_PROGRESS);
        } catch (Throwable e) {
            log.error("执行失败: ", e);
            task.setMessage(e.getMessage());
            asyncTaskService.updateAsyncTask(task, TaskState.FAILED, COMPLETED_OR_FAILED_PROGRESS);
        }
    }

    private void executeProductAITranslate(AsyncTask task) {
        long taskStart = System.currentTimeMillis();
        try {
            TranslateByAIRequest request = JSONUtil.toBean(task.getParameters(), TranslateByAIRequest.class);
            log.info("[batchTranslate] taskId={} 开始批量翻译, productId={}, languageId={}",
                    task.getId(), request.getProductId(), request.getLanguageId());

            task.setMessage("正在准备翻译内容...");
            asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 2);

            TranslateContext ctx = prepareTranslateContext(task, request);
            Product product = ctx.getProduct();
            Language language = ctx.getLanguage();
            Country country = ctx.getCountry();
            SystemUser owner = ctx.getOwner();
            String langName = ctx.getLangName();
            String introduction = ctx.getIntroduction();
            Map<String, String> uniqueTextMap = ctx.getUniqueTextMap();
            List<String> imgIds = ctx.getImgIds();
            Map<String, byte[]> originalImageBytes = ctx.getOriginalImageBytes();
            Map<String, String> imageMimeTypes = ctx.getImageMimeTypes();
            List<String> translatableImgIds = ctx.getTranslatableImgIds();
            Map<String, MultimediaFile> preCachedImageMap = ctx.getPreCachedImageMap();
            Map<String, String> imageHashByImgId = ctx.getImageHashByImgId();
            Map<String, String> uncachedTextMap = ctx.getUncachedTextMap();
            Map<String, String> cachedTextMap = ctx.getCachedTextMap();
            String cachedTranslatedHtml = ctx.getCachedTranslatedHtml();

            // === 构建 JSONL（跳过已缓存项） ===
            StringBuilder jsonl = new StringBuilder();
            int totalRequests = 0;

            for (Map.Entry<String, String> entry : uncachedTextMap.entrySet()) {
                jsonl.append(geminiTranslateService.buildTextTranslateJsonlEntry(
                        "text-" + entry.getKey(), entry.getValue(), langName)).append("\n");
                totalRequests++;
            }

            if (cachedTranslatedHtml == null && introduction != null && !introduction.isBlank()) {
                jsonl.append(geminiTranslateService.buildHtmlTranslateJsonlEntry("html", introduction, langName)).append("\n");
                totalRequests++;
            }

            for (String imgId : translatableImgIds) {
                jsonl.append(geminiTranslateService.buildImageTranslateJsonlEntry(
                        "img-" + imgId, originalImageBytes.get(imgId), imageMimeTypes.get(imgId), langName)).append("\n");
                totalRequests++;
            }

            log.info("[batchTranslate] taskId={} JSONL 构建完成: totalRequests={} (跳过缓存后), jsonlSize={}bytes",
                    task.getId(), totalRequests, jsonl.length());

            if (totalRequests == 0) {
                log.info("[batchTranslate] taskId={} 全部命中缓存, 跳过 Batch API, 直接保存", task.getId());
                task.setMessage("全部缓存命中, 保存结果...");
                asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 97);

                Map<String, String> translatedTextMap = new HashMap<>(cachedTextMap);
                Map<String, MultimediaFile> translatedImageMap = buildTranslatedImageMap(
                        imgIds, imageHashByImgId, new HashMap<>(), preCachedImageMap);

                productService.assembleTranslatedProduct(
                        product, language, country, owner, translatedTextMap,
                        cachedTranslatedHtml != null ? cachedTranslatedHtml : introduction,
                        translatedImageMap);

                task.setMessage("翻译完成");
                asyncTaskService.updateAsyncTask(task, TaskState.COMPLETED, COMPLETED_OR_FAILED_PROGRESS);
            } else {
                request.setTotalRequests(totalRequests);
                task.setParameters(JSONUtil.toJsonStr(request));

                task.setMessage("正在上传翻译请求...");
                asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 5);

                String uploadedFileName = geminiTranslateService.uploadBatchFile(jsonl.toString());
                log.info("[batchTranslate] taskId={} JSONL 已上传: fileName={}", task.getId(), uploadedFileName);

                BatchJob batchJob = geminiTranslateService.createBatchJob(uploadedFileName);
                String jobName = batchJob.name().orElseThrow(() -> new RuntimeException("Batch Job 创建后无 name"));
                task.setBatchJobName(jobName);
                log.info("[batchTranslate] taskId={} Batch Job 已创建: jobName={}, 准备耗时={}ms",
                        task.getId(), jobName, System.currentTimeMillis() - taskStart);

                task.setMessage("AI翻译中: 等待Batch完成...");
                asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 10);

                String batchState = pollBatchJobUntilDone(task, jobName, uploadedFileName, totalRequests);
                if (batchState == null) {
                    log.info("[batchTranslate] taskId={} 轮询期间任务被取消", task.getId());
                    return;
                }

                BatchJob currentJob = geminiTranslateService.getBatchJob(jobName);
                processBatchResultAndSave(task, batchState, currentJob, jobName, uploadedFileName, ctx);
            }

            long elapsed = System.currentTimeMillis() - taskStart;
            translateTaskMetrics.recordDuration(elapsed);
            if (task.getState() == TaskState.COMPLETED) {
                translateTaskMetrics.recordCompleted();
            }
            log.info("[batchTranslate] taskId={} 批量翻译流程结束, 总耗时={}ms, 最终状态={}",
                    task.getId(), elapsed, task.getState());

        } catch (Throwable e) {
            long elapsed = System.currentTimeMillis() - taskStart;
            translateTaskMetrics.recordFailed();
            translateTaskMetrics.recordDuration(elapsed);
            log.error("[batchTranslate] taskId={} 批量翻译任务异常, 总耗时={}ms",
                    task.getId(), elapsed, e);
            task.setMessage(e.getMessage());
            asyncTaskService.updateAsyncTask(task, TaskState.FAILED, COMPLETED_OR_FAILED_PROGRESS);
        }
    }

    /**
     * 即时翻译模式：不走 Batch API，逐条调用 Gemini 直接翻译。
     * 文本和图片均按 hash 去重，同 hash 内容只翻译一次。
     */
    private void executeProductAITranslateDirect(AsyncTask task) {
        long taskStart = System.currentTimeMillis();
        try {
            TranslateByAIRequest request = JSONUtil.toBean(task.getParameters(), TranslateByAIRequest.class);
            log.info("[directTranslate] taskId={} 开始即时翻译, productId={}, languageId={}, countryId={}",
                    task.getId(), request.getProductId(), request.getLanguageId(), request.getCountryId());

            task.setMessage("即时翻译: 正在准备...");
            asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 2);

            TranslateContext ctx = prepareTranslateContext(task, request);
            Product product = ctx.getProduct();
            Language language = ctx.getLanguage();
            Country country = ctx.getCountry();
            SystemUser owner = ctx.getOwner();
            String langName = ctx.getLangName();
            String introduction = ctx.getIntroduction();
            Map<String, String> uniqueTextMap = ctx.getUniqueTextMap();
            List<String> imgIds = ctx.getImgIds();
            Map<String, byte[]> originalImageBytes = ctx.getOriginalImageBytes();
            Map<String, String> imageMimeTypes = ctx.getImageMimeTypes();
            List<String> translatableImgIds = ctx.getTranslatableImgIds();
            Map<String, MultimediaFile> preCachedImageMap = ctx.getPreCachedImageMap();
            Map<String, String> imageHashByImgId = ctx.getImageHashByImgId();

            int totalSteps = uniqueTextMap.size() + (introduction != null && !introduction.isBlank() ? 1 : 0) + translatableImgIds.size() + 1;
            int doneSteps = 0;

            // --- 逐条翻译去重后的文本（带缓存） ---
            long stepStart = System.currentTimeMillis();
            task.setMessage("即时翻译: 翻译文本...");
            asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 5);

            Map<String, String> translatedTextMap = new HashMap<>();
            for (Map.Entry<String, String> entry : uniqueTextMap.entrySet()) {
                String translated = translateTextWithCache(entry.getValue(), language, langName);
                translatedTextMap.put(entry.getKey(), translated);
                doneSteps++;
                asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, progressOf(doneSteps, totalSteps));
            }
            log.info("[directTranslate] taskId={} 文本翻译完成: {} 条(去重后), 耗时={}ms",
                    task.getId(), translatedTextMap.size(), System.currentTimeMillis() - stepStart);

            // --- 翻译 HTML（带缓存） ---
            String translatedHtml = introduction;
            if (introduction != null && !introduction.isBlank()) {
                AsyncTask check = asyncTaskService.getById(task.getId());
                if (check.getState() == TaskState.CANCELLED) {
                    log.info("[directTranslate] taskId={} HTML翻译前任务已取消", task.getId());
                    return;
                }
                stepStart = System.currentTimeMillis();
                task.setMessage("即时翻译: 翻译 HTML...");
                asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, progressOf(doneSteps, totalSteps));
                translatedHtml = translateHtmlWithCache(introduction, language, langName);
                doneSteps++;
                log.info("[directTranslate] taskId={} HTML翻译完成: 输入长度={}, 输出长度={}, 耗时={}ms",
                        task.getId(), introduction.length(),
                        translatedHtml != null ? translatedHtml.length() : 0,
                        System.currentTimeMillis() - stepStart);
                asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, progressOf(doneSteps, totalSteps));
            }

            // --- 翻译图片（translatableImgIds 仅包含未缓存且去重后的，直接调 API） ---
            Map<String, MultimediaFile> translatedImageHashMap = new HashMap<>();
            int imgTranslated = 0, imgSkipped = 0, imgFailed = 0;
            for (int i = 0; i < translatableImgIds.size(); i++) {
                AsyncTask check = asyncTaskService.getById(task.getId());
                if (check.getState() == TaskState.CANCELLED) {
                    log.info("[directTranslate] taskId={} 图片翻译阶段任务已取消, 已完成 {}/{}", task.getId(), i, translatableImgIds.size());
                    return;
                }

                String imgId = translatableImgIds.get(i);
                String imageHash = imageHashByImgId.get(imgId);
                task.setMessage("即时翻译: 翻译图片 (" + (i + 1) + "/" + translatableImgIds.size() + ")...");
                asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, progressOf(doneSteps, totalSteps));
                try {
                    byte[] imgBytes = originalImageBytes.get(imgId);
                    String mimeType = imageMimeTypes.get(imgId);
                    if (imgBytes != null && mimeType != null) {
                        stepStart = System.currentTimeMillis();
                        byte[] result = geminiTranslateService.translateImage(imgBytes, mimeType, langName);
                        MultimediaFile sourceFile = multimediaFileService.getById(Long.valueOf(imgId));
                        if (result != null) {
                            MultimediaFile newFile = multimediaFileService.saveTranslatedImage(result, sourceFile.getSuffix(), owner);
                            translatedImageHashMap.put(imageHash, newFile);
                            saveImageTranslationCache(imageHash, sourceFile, language, newFile, false, owner);
                            imgTranslated++;
                            log.info("[directTranslate] taskId={} 图片[{}/{}] imgId={} 翻译成功: 原始={}bytes, 结果={}bytes, newFileId={}, 耗时={}ms",
                                    task.getId(), i + 1, translatableImgIds.size(), imgId,
                                    imgBytes.length, result.length, newFile.getId(), System.currentTimeMillis() - stepStart);
                        } else {
                            saveImageTranslationCache(imageHash, sourceFile, language, null, true, owner);
                            imgSkipped++;
                            log.info("[directTranslate] taskId={} 图片[{}/{}] imgId={} 无需翻译(无文字), 耗时={}ms",
                                    task.getId(), i + 1, translatableImgIds.size(), imgId,
                                    System.currentTimeMillis() - stepStart);
                        }
                    }
                } catch (Exception e) {
                    imgFailed++;
                    log.warn("[directTranslate] taskId={} 图片[{}/{}] imgId={} 翻译失败, 使用原图",
                            task.getId(), i + 1, translatableImgIds.size(), imgId, e);
                }
                doneSteps++;
                asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, progressOf(doneSteps, totalSteps));
            }

            log.info("[directTranslate] taskId={} 图片翻译汇总: 总计={}, 缓存预命中={}, 已翻译={}, 无需翻译={}, 失败={}",
                    task.getId(), imgIds.size(), preCachedImageMap.size(), imgTranslated, imgSkipped, imgFailed);

            // --- 保存结果 ---
            AsyncTask preSave = asyncTaskService.getById(task.getId());
            if (preSave.getState() == TaskState.CANCELLED) {
                log.info("[directTranslate] taskId={} 保存前任务已取消", task.getId());
                return;
            }

            stepStart = System.currentTimeMillis();
            task.setMessage("即时翻译: 保存结果...");
            asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 97);

            Map<String, MultimediaFile> translatedImageMap = buildTranslatedImageMap(
                    imgIds, imageHashByImgId, translatedImageHashMap, preCachedImageMap);

            productService.assembleTranslatedProduct(
                    product, language, country, owner, translatedTextMap,
                    translatedHtml != null ? translatedHtml : introduction,
                    translatedImageMap);

            log.info("[directTranslate] taskId={} 保存完成, 耗时={}ms", task.getId(), System.currentTimeMillis() - stepStart);

            task.setMessage("翻译完成");
            asyncTaskService.updateAsyncTask(task, TaskState.COMPLETED, COMPLETED_OR_FAILED_PROGRESS);
            translateTaskMetrics.recordCompleted();

            long elapsed = System.currentTimeMillis() - taskStart;
            translateTaskMetrics.recordDuration(elapsed);
            log.info("[directTranslate] taskId={} 即时翻译全部完成, 总耗时={}ms", task.getId(), elapsed);
        } catch (Throwable e) {
            long elapsed = System.currentTimeMillis() - taskStart;
            translateTaskMetrics.recordFailed();
            translateTaskMetrics.recordDuration(elapsed);
            log.error("[directTranslate] taskId={} 即时翻译任务异常, 总耗时={}ms",
                    task.getId(), elapsed, e);
            task.setMessage(e.getMessage());
            asyncTaskService.updateAsyncTask(task, TaskState.FAILED, COMPLETED_OR_FAILED_PROGRESS);
        }
    }

    private int progressOf(int doneSteps, int totalSteps) {
        return 5 + (int) (90.0 * doneSteps / Math.max(1, totalSteps));
    }

    /**
     * 统一准备翻译所需的上下文数据：加载产品、收集文本/图片、执行缓存前置过滤。
     */
    private TranslateContext prepareTranslateContext(AsyncTask task, TranslateByAIRequest request) {
        Product product = productService.getByIdWithSpecifications(Long.parseLong(request.getProductId()));
        Language language = languageService.getById(Long.valueOf(request.getLanguageId()));
        Country country = request.getCountryId() != null
                ? countryService.getById(Long.valueOf(request.getCountryId()))
                : product.getCountry();
        SystemUser owner = task.getOwner();
        String langName = language.getName();
        String introduction = product.getIntroduction();

        Map<String, String> uniqueTextMap = collectTextsToTranslate(product);
        List<String> imgIds = collectImageIds(product);

        Map<String, byte[]> originalImageBytes = new HashMap<>();
        Map<String, String> imageMimeTypes = new HashMap<>();
        List<String> translatableImgIds = new ArrayList<>();
        Map<String, MultimediaFile> preCachedImageMap = new HashMap<>();
        Map<String, String> imageHashByImgId = new HashMap<>();
        Map<String, List<String>> hashToDeferredImgIds = new HashMap<>();

        downloadAndFilterImages(imgIds, language, originalImageBytes, imageMimeTypes,
                translatableImgIds, preCachedImageMap, imageHashByImgId, hashToDeferredImgIds);

        Map<String, String> cachedTextMap = new LinkedHashMap<>();
        Map<String, String> uncachedTextMap = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : uniqueTextMap.entrySet()) {
            String cached = lookupSingleTextCache(entry.getValue(), language);
            if (cached != null) {
                cachedTextMap.put(entry.getKey(), cached);
            } else {
                uncachedTextMap.put(entry.getKey(), entry.getValue());
            }
        }

        String cachedTranslatedHtml = null;
        if (introduction != null && !introduction.isBlank()) {
            cachedTranslatedHtml = lookupHtmlCache(introduction, language);
        }

        log.info("[prepareContext] taskId={} 产品: title='{}', targetLang='{}', targetCountry='{}', " +
                        "uniqueTexts={}, htmlLength={}, imgIds={}, " +
                        "缓存: text={}/{}, img={}/{}, html={}",
                task.getId(), product.getTitle(), langName, country != null ? country.getName() : "null",
                uniqueTextMap.size(), introduction != null ? introduction.length() : 0, imgIds.size(),
                cachedTextMap.size(), uniqueTextMap.size(),
                preCachedImageMap.size(), imgIds.size(),
                cachedTranslatedHtml != null ? "HIT" : "MISS");

        return TranslateContext.builder()
                .product(product)
                .language(language)
                .country(country)
                .owner(owner)
                .langName(langName)
                .introduction(introduction)
                .uniqueTextMap(uniqueTextMap)
                .imgIds(imgIds)
                .originalImageBytes(originalImageBytes)
                .imageMimeTypes(imageMimeTypes)
                .translatableImgIds(translatableImgIds)
                .preCachedImageMap(preCachedImageMap)
                .imageHashByImgId(imageHashByImgId)
                .hashToDeferredImgIds(hashToDeferredImgIds)
                .cachedTextMap(cachedTextMap)
                .uncachedTextMap(uncachedTextMap)
                .cachedTranslatedHtml(cachedTranslatedHtml)
                .build();
    }

    /**
     * 恢复已提交 Batch Job 的 AI 翻译任务。
     * 延迟加载策略：先只用 jobName 轮询 Batch 状态，完成后再加载产品/图片等重量级数据。
     */
    private void resumeProductAITranslate(AsyncTask task) {
        long taskStart = System.currentTimeMillis();
        try {
            String jobName = task.getBatchJobName();
            log.info("[resumeTranslate] taskId={} 开始恢复, jobName={} (延迟加载模式，暂不加载产品/图片)", task.getId(), jobName);

            TranslateByAIRequest resumeRequest = JSONUtil.toBean(task.getParameters(), TranslateByAIRequest.class);
            int savedTotalRequests = resumeRequest.getTotalRequests() != null ? resumeRequest.getTotalRequests() : 0;

            task.setMessage("正在恢复AI翻译任务，等待Batch完成...");
            asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 10);

            String batchState = pollBatchJobUntilDone(task, jobName, null, savedTotalRequests);
            if (batchState == null) {
                return;
            }

            log.info("[resumeTranslate] taskId={} Batch轮询结束, state={}, 轮询耗时={}ms, 开始加载产品数据...",
                    task.getId(), batchState, System.currentTimeMillis() - taskStart);

            task.setMessage("正在加载产品数据...");
            asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 82);

            TranslateByAIRequest request = JSONUtil.toBean(task.getParameters(), TranslateByAIRequest.class);
            TranslateContext ctx = prepareTranslateContext(task, request);

            BatchJob currentJob = geminiTranslateService.getBatchJob(jobName);
            processBatchResultAndSave(task, batchState, currentJob, jobName, null, ctx);

            long elapsed = System.currentTimeMillis() - taskStart;
            translateTaskMetrics.recordDuration(elapsed);
            if (task.getState() == TaskState.COMPLETED) {
                translateTaskMetrics.recordCompleted();
            }
            log.info("[resumeTranslate] taskId={} 恢复流程全部完成, 总耗时={}ms, 最终状态={}",
                    task.getId(), elapsed, task.getState());

        } catch (Throwable e) {
            long elapsed = System.currentTimeMillis() - taskStart;
            translateTaskMetrics.recordFailed();
            translateTaskMetrics.recordDuration(elapsed);
            log.error("[resumeTranslate] taskId={} 恢复任务失败, 总耗时={}ms",
                    task.getId(), elapsed, e);
            task.setMessage(e.getMessage());
            asyncTaskService.updateAsyncTask(task, TaskState.FAILED, COMPLETED_OR_FAILED_PROGRESS);
        }
    }

    /**
     * 轻量级轮询：只查询 Batch Job 状态直到完成，不加载任何业务数据。
     * @param totalRequests 提交的总请求数，0 表示未知（恢复场景）
     * @return 最终的 batchState，如果任务被取消则返回 null
     */
    private String pollBatchJobUntilDone(AsyncTask task, String jobName, String uploadedFileName, int totalRequests) {
        long startTime = System.currentTimeMillis();
        int pollCount = 0;

        log.info("[pollBatchJob] taskId={} 开始轮询: jobName={}, totalRequests={}, pollInterval={}ms, timeout={}ms",
                task.getId(), jobName, totalRequests, BATCH_POLL_INTERVAL_MS, BATCH_TIMEOUT_MS);

        while (true) {
            AsyncTask freshTask = asyncTaskService.getById(task.getId());
            if (freshTask.getState() == TaskState.CANCELLED) {
                log.info("[pollBatchJob] taskId={} 任务已被用户取消", task.getId());
                geminiTranslateService.cancelBatchJob(jobName);
                cleanupBatchResources(jobName, uploadedFileName);
                return null;
            }
            if (freshTask.getBatchJobName() == null || freshTask.getBatchJobName().isBlank()) {
                log.info("[pollBatchJob] taskId={} batchJobName 已被清除（任务已切换模式），退出轮询", task.getId());
                cleanupBatchResources(jobName, uploadedFileName);
                return null;
            }

            pollCount++;
            BatchJob currentJob = geminiTranslateService.getBatchJob(jobName);
            String batchState = currentJob.state().map(Object::toString).orElse("UNKNOWN");
            long elapsed = System.currentTimeMillis() - startTime;

            int completed = 0;
            long successCount = 0, failedCount = 0;
            if (currentJob.completionStats().isPresent()) {
                var stats = currentJob.completionStats().get();
                successCount = stats.successfulCount().orElse(0L);
                failedCount = stats.failedCount().orElse(0L);
                completed = (int) (successCount + failedCount);
            }

            log.info("[pollBatchJob] taskId={} 轮询#{}: state={}, completed={} (success={}, failed={}), elapsed={}ms",
                    task.getId(), pollCount, batchState, completed, successCount, failedCount, elapsed);

            String progressDetail;
            if (totalRequests > 0) {
                progressDetail = String.format("AI翻译中: 已完成 %d/%d (成功 %d, 失败 %d)",
                        completed, totalRequests, successCount, failedCount);
            } else {
                progressDetail = String.format("AI翻译中: 已完成 %d (成功 %d, 失败 %d)",
                        completed, successCount, failedCount);
            }
            task.setMessage(progressDetail);
            asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, Math.min(10 + pollCount * 5, 80));

            if (BATCH_COMPLETED_STATES.contains(batchState)) {
                log.info("[pollBatchJob] taskId={} Batch Job 已结束: state={}, 共轮询 {} 次, 耗时={}ms",
                        task.getId(), batchState, pollCount, elapsed);
                return batchState;
            }

            if (elapsed >= BATCH_TIMEOUT_MS) {
                translateTaskMetrics.recordTimeout();
                log.warn("[pollBatchJob] taskId={} 超时({}ms), 取消 Batch Job: {}", task.getId(), BATCH_TIMEOUT_MS, jobName);
                geminiTranslateService.cancelBatchJob(jobName);
                ThreadUtil.sleep(10_000);
                currentJob = geminiTranslateService.getBatchJob(jobName);
                batchState = currentJob.state().map(Object::toString).orElse("UNKNOWN");
                log.info("[pollBatchJob] taskId={} 超时取消后状态: {}", task.getId(), batchState);
                return batchState;
            }

            ThreadUtil.sleep(BATCH_POLL_INTERVAL_MS);
        }
    }

    /**
     * Batch Job 完成后：下载结果、按需补刀、组装保存。
     * 文本结果按 text-{hash} 逐条提取，图片结果按 img-{imgId} 提取。
     */
    private void processBatchResultAndSave(AsyncTask task, String batchState, BatchJob currentJob,
                                           String jobName, String uploadedFileName,
                                           TranslateContext ctx) throws Exception {
        Product product = ctx.getProduct();
        Language language = ctx.getLanguage();
        Country country = ctx.getCountry();
        SystemUser owner = ctx.getOwner();
        String langName = ctx.getLangName();
        String introduction = ctx.getIntroduction();
        List<String> imgIds = ctx.getImgIds();
        Map<String, String> uniqueTextMap = ctx.getUniqueTextMap();
        Map<String, byte[]> preloadedImageBytes = ctx.getOriginalImageBytes();
        Map<String, String> preloadedMimeTypes = ctx.getImageMimeTypes();
        List<String> preloadedTranslatableImgIds = ctx.getTranslatableImgIds();
        Map<String, String> preloadedImageHashByImgId = ctx.getImageHashByImgId();
        Map<String, String> uncachedTextMap = ctx.getUncachedTextMap();
        Map<String, String> cachedTextMap = ctx.getCachedTextMap();
        String cachedTranslatedHtml = ctx.getCachedTranslatedHtml();
        Map<String, MultimediaFile> preCachedImageMap = ctx.getPreCachedImageMap();

        boolean hasPreloadedImages = (preloadedImageBytes != null && !preloadedImageBytes.isEmpty());

        log.info("[processResult] taskId={} 开始处理Batch结果: batchState={}, 预加载图片={}, 文本缓存={}, HTML缓存={}, 图片缓存={}",
                task.getId(), batchState, hasPreloadedImages,
                cachedTextMap != null ? cachedTextMap.size() : 0,
                cachedTranslatedHtml != null ? "HIT" : "MISS",
                preCachedImageMap != null ? preCachedImageMap.size() : 0);

        // --- 合并前置缓存 ---
        Map<String, String> translatedTextMap = new HashMap<>();
        if (cachedTextMap != null) {
            translatedTextMap.putAll(cachedTextMap);
        }
        String translatedHtml = cachedTranslatedHtml;
        Map<String, MultimediaFile> translatedImageHashMap = new HashMap<>();
        List<String> batchFailedImgIds = new ArrayList<>();

        if (uncachedTextMap == null) {
            uncachedTextMap = new LinkedHashMap<>(uniqueTextMap);
        }

        if ("JOB_STATE_SUCCEEDED".equals(batchState) || "JOB_STATE_CANCELLED".equals(batchState)) {
            String resultFileName = currentJob.dest()
                    .flatMap(BatchJobDestination::fileName)
                    .orElse(null);

            log.info("[processResult] taskId={} 结果文件: {}, batchState={}", task.getId(), resultFileName, batchState);

            if (resultFileName != null) {
                try {
                    long dlStart = System.currentTimeMillis();
                    String resultContent = geminiTranslateService.downloadBatchResult(resultFileName);
                    log.info("[processResult] taskId={} 结果文件下载完成: size={}bytes, 耗时={}ms",
                            task.getId(), resultContent.length(), System.currentTimeMillis() - dlStart);

                    Map<String, JsonNode> resultMap = new HashMap<>();
                    int duplicateResultKeys = 0;
                    for (String line : resultContent.split("\n")) {
                        if (line.isBlank()) continue;
                        try {
                            JsonNode node = OBJECT_MAPPER.readTree(line);
                            String key = node.has("key") ? node.get("key").asText() : null;
                            if (key != null) {
                                if (resultMap.containsKey(key)) {
                                    duplicateResultKeys++;
                                }
                                resultMap.put(key, node);
                            }
                        } catch (Exception e) {
                            log.warn("[processResult] taskId={} 解析结果行失败: {}", task.getId(), e.getMessage());
                        }
                    }
                    if (duplicateResultKeys > 0) {
                        translateTaskMetrics.recordDedupHit();
                        log.warn("[processResult] taskId={} Batch结果存在重复key: count={}", task.getId(), duplicateResultKeys);
                    }
                    log.info("[processResult] taskId={} 结果解析完成: keys={}", task.getId(), resultMap.keySet());

                    // 文本结果：按 text-{hash} 逐条提取
                    int textHit = 0, textMiss = 0;
                    for (Map.Entry<String, String> entry : uncachedTextMap.entrySet()) {
                        String hash = entry.getKey();
                        JsonNode textNode = resultMap.get("text-" + hash);
                        if (textNode != null && textNode.has("response")) {
                            String translated = extractTextFromResponse(textNode.get("response"));
                            if (translated != null && !translated.isBlank()) {
                                translatedTextMap.put(hash, translated);
                                writeSingleTextCache(entry.getValue(), translated, language);
                                textHit++;
                                continue;
                            }
                        }
                        textMiss++;
                        log.warn("[processResult] taskId={} 文本 hash={} 无Batch结果, 需补刀", task.getId(), hash);
                    }
                    log.info("[processResult] taskId={} 文本Batch结果: 命中={}, 缺失={}", task.getId(), textHit, textMiss);

                    // HTML（跳过已有前置缓存的）
                    if (translatedHtml == null) {
                        JsonNode htmlNode = resultMap.get("html");
                        if (htmlNode != null && htmlNode.has("response")) {
                            translatedHtml = extractTextFromResponse(htmlNode.get("response"));
                            if (translatedHtml != null && introduction != null) {
                                writeHtmlTranslationCache(introduction, translatedHtml, language, owner);
                            }
                            log.info("[processResult] taskId={} HTML翻译结果: length={}",
                                    task.getId(), translatedHtml != null ? translatedHtml.length() : 0);
                        } else if (introduction != null && !introduction.isBlank()) {
                            log.warn("[processResult] taskId={} HTML翻译结果缺失, 走fallback", task.getId());
                        }
                    } else {
                        log.info("[processResult] taskId={} HTML使用前置缓存: length={}", task.getId(), translatedHtml.length());
                    }

                    // 图片
                    List<String> imgIdsInBatch = new ArrayList<>();
                    for (String key : resultMap.keySet()) {
                        if (key.startsWith("img-")) {
                            imgIdsInBatch.add(key.substring(4));
                        }
                    }

                    List<String> translatableImgIds = (preloadedTranslatableImgIds != null)
                            ? preloadedTranslatableImgIds : imgIdsInBatch;

                    for (String imgId : translatableImgIds) {
                        String imageHash = resolveImageHash(imgId, preloadedImageHashByImgId, preloadedImageBytes);
                        if (imageHash == null) {
                            batchFailedImgIds.add(imgId);
                            log.warn("[processResult] taskId={} 图片 imgId={} 无法计算源图hash, 需补刀", task.getId(), imgId);
                            continue;
                        }

                        Optional<ImageTranslationCache> cached = imageTranslationCacheRepository
                                .findByImageHashAndLanguageId(imageHash, language.getId());
                        if (cached.isPresent()) {
                            ImageTranslationCache it = cached.get();
                            if (!it.isSkipped() && it.getTranslatedFile() != null) {
                                translatedImageHashMap.put(imageHash, it.getTranslatedFile());
                            }
                            log.info("[processResult] taskId={} 图片 imgId={} 命中缓存, skipped={}", task.getId(), imgId, it.isSkipped());
                            continue;
                        }

                        JsonNode imgNode = resultMap.get("img-" + imgId);
                        if (imgNode == null || !imgNode.has("response")) {
                            batchFailedImgIds.add(imgId);
                            log.info("[processResult] taskId={} 图片 imgId={} 无结果, 需补刀", task.getId(), imgId);
                            continue;
                        }
                        byte[] imgBytes = extractImageFromResponse(imgNode.get("response"));
                        if (imgBytes != null) {
                            try {
                                MultimediaFile sourceFile = multimediaFileService.getById(Long.valueOf(imgId));
                                MultimediaFile newFile = multimediaFileService.saveTranslatedImage(imgBytes, sourceFile.getSuffix(), owner);
                                translatedImageHashMap.put(imageHash, newFile);
                                saveImageTranslationCache(imageHash, sourceFile, language, newFile, false, owner);
                                log.info("[processResult] taskId={} 图片 imgId={} 翻译成功: {}bytes, newFileId={}", task.getId(), imgId, imgBytes.length, newFile.getId());
                            } catch (Exception e) {
                                log.warn("[processResult] taskId={} 图片 imgId={} 保存翻译图片失败", task.getId(), imgId, e);
                                batchFailedImgIds.add(imgId);
                            }
                        } else {
                            try {
                                MultimediaFile sourceFile = multimediaFileService.getById(Long.valueOf(imgId));
                                saveImageTranslationCache(imageHash, sourceFile, language, null, true, owner);
                            } catch (Exception e) {
                                log.warn("[processResult] taskId={} 图片 imgId={} 保存跳过记录失败", task.getId(), imgId, e);
                            }
                            log.info("[processResult] taskId={} 图片 imgId={} 无需翻译", task.getId(), imgId);
                        }
                    }

                    geminiTranslateService.deleteFile(resultFileName);
                } catch (Exception e) {
                    log.error("[processResult] taskId={} 下载/解析结果失败, 全部补刀", task.getId(), e);
                    batchFailedImgIds.addAll(imgIds);
                }
            } else {
                log.warn("[processResult] taskId={} 无结果文件, 全部补刀", task.getId());
                batchFailedImgIds.addAll(imgIds);
            }
        } else {
            log.warn("[processResult] taskId={} Batch Job 非成功状态: {}, 全部补刀", task.getId(), batchState);
            batchFailedImgIds.addAll(imgIds);
        }

        cleanupBatchResources(jobName, uploadedFileName);

        // --- Fallback: 文本（逐条补刀缺失的 hash） ---
        List<String> missingTextHashes = new ArrayList<>();
        for (String hash : uniqueTextMap.keySet()) {
            if (!translatedTextMap.containsKey(hash)) {
                missingTextHashes.add(hash);
            }
        }
        if (!missingTextHashes.isEmpty()) {
            translateTaskMetrics.recordFallbackText();
            long fbStart = System.currentTimeMillis();
            task.setMessage("正在补充翻译文本...");
            asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 84);
            for (String hash : missingTextHashes) {
                String original = uniqueTextMap.get(hash);
                String translated = translateTextWithCache(original, language, langName);
                translatedTextMap.put(hash, translated);
            }
            log.info("[processResult] taskId={} 文本补刀完成: {} 条, 耗时={}ms",
                    task.getId(), missingTextHashes.size(), System.currentTimeMillis() - fbStart);
        }

        boolean needHtmlFallback = (translatedHtml == null && introduction != null && !introduction.isBlank());
        boolean needImageFallback = !batchFailedImgIds.isEmpty();

        log.info("[processResult] taskId={} 结果汇总: texts=OK({}), html={}, images(hash)={}, 需补刀图片={}",
                task.getId(), translatedTextMap.size(),
                translatedHtml != null ? "OK" : (needHtmlFallback ? "FALLBACK" : "N/A"),
                translatedImageHashMap.size(), batchFailedImgIds.size());

        // --- Fallback: HTML（带缓存） ---
        if (needHtmlFallback) {
            translateTaskMetrics.recordFallbackHtml();
            long fbStart = System.currentTimeMillis();
            task.setMessage("正在补充翻译HTML...");
            asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 86);
            translatedHtml = translateHtmlWithCache(introduction, language, langName);
            log.info("[processResult] taskId={} HTML补刀完成: length={}, 耗时={}ms",
                    task.getId(), translatedHtml != null ? translatedHtml.length() : 0,
                    System.currentTimeMillis() - fbStart);
        }

        // --- Fallback: 图片 ---
        if (needImageFallback) {
            translateTaskMetrics.recordFallbackImage();
            Map<String, byte[]> fbImageBytes;
            Map<String, String> fbMimeTypes;
            List<String> fbImgIds;
            Map<String, String> fbImageHashByImgId;

            if (hasPreloadedImages) {
                fbImageBytes = preloadedImageBytes;
                fbMimeTypes = preloadedMimeTypes;
                fbImgIds = batchFailedImgIds;
                fbImageHashByImgId = preloadedImageHashByImgId != null ? preloadedImageHashByImgId : new HashMap<>();
                log.info("[processResult] taskId={} 使用预加载图片数据补刀 {} 张", task.getId(), fbImgIds.size());
            } else {
                log.info("[processResult] taskId={} 需补刀 {} 张图片, 开始按需下载原图...", task.getId(), batchFailedImgIds.size());
                task.setMessage("正在下载待补刀的图片...");
                asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 88);

                fbImageBytes = new HashMap<>();
                fbMimeTypes = new HashMap<>();
                fbImgIds = new ArrayList<>();
                Map<String, MultimediaFile> fbCachedImages = new HashMap<>();
                fbImageHashByImgId = new HashMap<>();
                downloadAndFilterImages(batchFailedImgIds, language, fbImageBytes, fbMimeTypes, fbImgIds, fbCachedImages, fbImageHashByImgId, null);
                for (Map.Entry<String, MultimediaFile> e : fbCachedImages.entrySet()) {
                    String hash = fbImageHashByImgId.get(e.getKey());
                    if (hash != null) {
                        translatedImageHashMap.put(hash, e.getValue());
                    }
                }
            }

            int fbSuccess = 0, fbFail = 0, fbCached = 0;
            for (int i = 0; i < fbImgIds.size(); i++) {
                AsyncTask checkTask = asyncTaskService.getById(task.getId());
                if (checkTask.getState() == TaskState.CANCELLED) {
                    log.info("[processResult] taskId={} 补刀阶段任务被取消, 已补刀 {}/{}", task.getId(), i, fbImgIds.size());
                    return;
                }

                String imgId = fbImgIds.get(i);
                String imageHash = resolveImageHash(imgId, fbImageHashByImgId, fbImageBytes);
                if (imageHash == null) {
                    fbFail++;
                    log.warn("[processResult] taskId={} 补刀图片[{}/{}] imgId={} 无法计算源图hash",
                            task.getId(), i + 1, fbImgIds.size(), imgId);
                    continue;
                }
                task.setMessage("正在补充翻译图片 (" + (i + 1) + "/" + fbImgIds.size() + ")...");
                int fallbackProgress = 88 + (int) (8.0 * (i + 1) / fbImgIds.size());
                asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, Math.min(fallbackProgress, 96));

                try {
                    if (translatedImageHashMap.containsKey(imageHash)) {
                        fbCached++;
                        log.info("[processResult] taskId={} 补刀图片[{}/{}] imgId={} 已有翻译(同hash)",
                                task.getId(), i + 1, fbImgIds.size(), imgId);
                        continue;
                    }

                    Optional<ImageTranslationCache> cached = imageTranslationCacheRepository
                            .findByImageHashAndLanguageId(imageHash, language.getId());
                    if (cached.isPresent()) {
                        ImageTranslationCache it = cached.get();
                        if (!it.isSkipped() && it.getTranslatedFile() != null) {
                            translatedImageHashMap.put(imageHash, it.getTranslatedFile());
                        }
                        fbCached++;
                        log.info("[processResult] taskId={} 补刀图片[{}/{}] imgId={} 命中缓存",
                                task.getId(), i + 1, fbImgIds.size(), imgId);
                        continue;
                    }

                    long fbStart = System.currentTimeMillis();
                    byte[] imgBytesRaw = fbImageBytes.get(imgId);
                    String mimeType = fbMimeTypes.get(imgId);
                    if (imgBytesRaw != null && mimeType != null) {
                        byte[] result = geminiTranslateService.translateImage(imgBytesRaw, mimeType, langName);
                        MultimediaFile sourceFile = multimediaFileService.getById(Long.valueOf(imgId));
                        if (result != null) {
                            MultimediaFile newFile = multimediaFileService.saveTranslatedImage(result, sourceFile.getSuffix(), owner);
                            translatedImageHashMap.put(imageHash, newFile);
                            saveImageTranslationCache(imageHash, sourceFile, language, newFile, false, owner);
                            fbSuccess++;
                            log.info("[processResult] taskId={} 补刀图片[{}/{}] imgId={} 成功: {}bytes, newFileId={}, 耗时={}ms",
                                    task.getId(), i + 1, fbImgIds.size(), imgId, result.length, newFile.getId(), System.currentTimeMillis() - fbStart);
                        } else {
                            saveImageTranslationCache(imageHash, sourceFile, language, null, true, owner);
                            log.info("[processResult] taskId={} 补刀图片[{}/{}] imgId={} 无需翻译, 耗时={}ms",
                                    task.getId(), i + 1, fbImgIds.size(), imgId, System.currentTimeMillis() - fbStart);
                        }
                    }
                } catch (Exception e) {
                    fbFail++;
                    log.warn("[processResult] taskId={} 补刀图片[{}/{}] imgId={} 失败, 使用原图",
                            task.getId(), i + 1, fbImgIds.size(), imgId, e);
                }
            }
            log.info("[processResult] taskId={} 补刀完成: 成功={}, 缓存命中={}, 失败={}", task.getId(), fbSuccess, fbCached, fbFail);
        }

        // === 组装保存 ===
        AsyncTask preStep5 = asyncTaskService.getById(task.getId());
        if (preStep5.getState() == TaskState.CANCELLED) {
            log.info("[processResult] taskId={} 保存前任务已被取消", task.getId());
            return;
        }

        long saveStart = System.currentTimeMillis();
        task.setMessage("正在保存翻译结果...");
        asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 97);

        Map<String, MultimediaFile> translatedImageMap = buildTranslatedImageMap(
                imgIds, preloadedImageHashByImgId, translatedImageHashMap, preCachedImageMap);

        log.info("[processResult] taskId={} 开始组装保存: textMap={} 条, html={}, images={} 张",
                task.getId(), translatedTextMap.size(),
                translatedHtml != null ? translatedHtml.length() + "chars" : "null",
                translatedImageMap.size());

        productService.assembleTranslatedProduct(
                product, language, country, owner, translatedTextMap,
                translatedHtml != null ? translatedHtml : introduction,
                translatedImageMap);

        log.info("[processResult] taskId={} 保存完成, 耗时={}ms", task.getId(), System.currentTimeMillis() - saveStart);

        task.setMessage("翻译完成");
        asyncTaskService.updateAsyncTask(task, TaskState.COMPLETED, COMPLETED_OR_FAILED_PROGRESS);
    }

    /**
     * 下载原图并过滤动图，填充输出参数。
     * 基于源图内容 hash 查 ImageTranslationCache 缓存：
     * 命中则不加入 translatableImgIds，而是直接放入 preCachedImageMap。
     */
    /**
     * 下载原图并过滤动图，填充输出参数。
     * 基于源图内容 hash 查 ImageTranslationCache 缓存并进行内存去重：
     * 同 hash 的图片只保留第一个进入 translatableImgIds，其余记录在 hashToDeferredImgIds 中。
     *
     * @param hashToDeferredImgIds 同 hash 但延迟处理的 imgId 列表（hash -> List<imgId>），可为 null 表示不需要去重跟踪
     */
    private void downloadAndFilterImages(List<String> imgIds, Language language,
                                         Map<String, byte[]> originalImageBytes,
                                         Map<String, String> imageMimeTypes,
                                         List<String> translatableImgIds,
                                         Map<String, MultimediaFile> preCachedImageMap,
                                         Map<String, String> imageHashByImgId,
                                         Map<String, List<String>> hashToDeferredImgIds) {
        log.info("[downloadImages] 开始处理 {} 张图片 (按hash缓存+内存去重)", imgIds.size());
        int cachedCount = 0, skippedAnimated = 0, dedupCount = 0;
        Set<String> seenHashes = new HashSet<>();
        for (int i = 0; i < imgIds.size(); i++) {
            String imgId = imgIds.get(i);
            try {
                MultimediaFile file = multimediaFileService.getById(Long.valueOf(imgId));
                String suffix = file.getSuffix().toLowerCase();

                if ("gif".equalsIgnoreCase(suffix)) {
                    log.info("[downloadImages] [{}/{}] imgId={} 跳过动图 gif", i + 1, imgIds.size(), imgId);
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
                imageHashByImgId.put(imgId, imageHash);

                Optional<ImageTranslationCache> cached = imageTranslationCacheRepository
                        .findByImageHashAndLanguageId(imageHash, language.getId());
                if (cached.isPresent()) {
                    ImageTranslationCache it = cached.get();
                    if (!it.isSkipped() && it.getTranslatedFile() != null) {
                        preCachedImageMap.put(imgId, it.getTranslatedFile());
                        log.info("[downloadImages] [{}/{}] imgId={} 命中hash缓存, translatedFileId={}",
                                i + 1, imgIds.size(), imgId, it.getTranslatedFile().getId());
                    } else {
                        log.info("[downloadImages] [{}/{}] imgId={} 命中hash缓存(skipped=true, 无需翻译)",
                                i + 1, imgIds.size(), imgId);
                    }
                    cachedCount++;
                    continue;
                }

                if (!seenHashes.add(imageHash)) {
                    if (hashToDeferredImgIds != null) {
                        hashToDeferredImgIds.computeIfAbsent(imageHash, k -> new ArrayList<>()).add(imgId);
                    }
                    dedupCount++;
                    log.info("[downloadImages] [{}/{}] imgId={} 内存去重(hash已存在), hash={}",
                            i + 1, imgIds.size(), imgId, imageHash);
                    continue;
                }

                String mimeType = "image/" + ("jpg".equalsIgnoreCase(suffix) ? "jpeg" : suffix);
                originalImageBytes.put(imgId, bytes);
                imageMimeTypes.put(imgId, mimeType);
                translatableImgIds.add(imgId);
                log.info("[downloadImages] [{}/{}] imgId={} 待翻译: suffix={}, mimeType={}, size={}bytes",
                        i + 1, imgIds.size(), imgId, suffix, mimeType, bytes.length);
            } catch (Exception e) {
                log.warn("[downloadImages] [{}/{}] imgId={} 下载失败, 使用原图", i + 1, imgIds.size(), imgId, e);
            }
        }
        if (dedupCount > 0) {
            translateTaskMetrics.recordDedupHit();
        }
        log.info("[downloadImages] 处理完成: 总计={}, 缓存命中={}, 跳过动图={}, 内存去重={}, 待翻译={}",
                imgIds.size(), cachedCount, skippedAnimated, dedupCount, translatableImgIds.size());
    }

    /**
     * 收集产品中所有需要翻译的文本，按内容 hash 去重。
     * @return hash -> 原文 的有序 Map（LinkedHashMap）
     */
    private Map<String, String> collectTextsToTranslate(Product product) {
        Map<String, String> uniqueTextMap = new LinkedHashMap<>();
        addTextIfPresent(uniqueTextMap, product.getTitle());
        addTextIfPresent(uniqueTextMap, product.getSummary());
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

    /**
     * 收集产品中所有需要翻译的图片 ID（去重）。
     * 包括：商品主图、规格图片、规格属性图片、详情 HTML 中引用的图片。
     */
    private List<String> collectImageIds(Product product) {
        Set<String> dedup = new java.util.LinkedHashSet<>();
        int rawCount = 0;

        if (product.getImageFiles() != null) {
            for (MultimediaFile img : product.getImageFiles()) {
                if (img != null && img.getId() != null) {
                    rawCount++;
                    dedup.add(String.valueOf(img.getId()));
                }
            }
        }

        for (ProductSpecification spec : product.getSpecificationList()) {
            MultimediaFile specImg = spec.getSpecificationImage();
            if (specImg != null && specImg.getId() != null) {
                rawCount++;
                dedup.add(String.valueOf(specImg.getId()));
            }
            for (ProductSpecificationAttributes attr : spec.getAttributes()) {
                MultimediaFile attrImg = attr.getMultimediaFile();
                if (attrImg != null && attrImg.getId() != null) {
                    rawCount++;
                    dedup.add(String.valueOf(attrImg.getId()));
                }
            }
        }

        String introduction = product.getIntroduction();
        if (introduction != null) {
            Matcher matcher = IMG_ID_PATTERN.matcher(introduction);
            while (matcher.find()) {
                rawCount++;
                dedup.add(matcher.group(1));
            }
        }

        List<String> imgIds = new ArrayList<>(dedup);
        int duplicated = rawCount - imgIds.size();
        if (duplicated > 0) {
            translateTaskMetrics.recordDedupHit();
            log.info("[collectImageIds] 发现重复图片引用: raw={}, unique={}, duplicated={}", rawCount, imgIds.size(), duplicated);
        }
        return imgIds;
    }

    /**
     * 检测 WebP 是否为动图（检查 VP8X 头的 animation flag 或 ANIM chunk）。
     */
    private boolean isAnimatedWebp(byte[] data) {
        if (data.length < 20) return false;
        // RIFF....WEBP
        if (data[0] != 'R' || data[1] != 'I' || data[2] != 'F' || data[3] != 'F') return false;
        if (data[8] != 'W' || data[9] != 'E' || data[10] != 'B' || data[11] != 'P') return false;

        // VP8X chunk starts at offset 12
        if (data.length > 20 && data[12] == 'V' && data[13] == 'P' && data[14] == '8' && data[15] == 'X') {
            // animation flag is bit 1 of the flags byte at offset 20
            return (data[20] & 0x02) != 0;
        }

        // Scan for ANIM chunk
        for (int i = 12; i < data.length - 4; i++) {
            if (data[i] == 'A' && data[i + 1] == 'N' && data[i + 2] == 'I' && data[i + 3] == 'M') {
                return true;
            }
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
                        if (part.has("text")) {
                            sb.append(part.get("text").asText());
                        }
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
                            if (base64 != null && !base64.isEmpty()) {
                                return Base64.getDecoder().decode(base64);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[extractImageFromResponse] 解析失败", e);
        }
        return null;
    }

    private String resolveImageHash(String imgId,
                                    Map<String, String> imageHashByImgId,
                                    Map<String, byte[]> preloadedImageBytes) {
        try {
            if (imageHashByImgId != null) {
                String existing = imageHashByImgId.get(imgId);
                if (existing != null && !existing.isBlank()) {
                    return existing;
                }
            }
            if (preloadedImageBytes != null) {
                byte[] bytes = preloadedImageBytes.get(imgId);
                if (bytes != null && bytes.length > 0) {
                    String hash = DigestUtil.sha256Hex(bytes);
                    if (imageHashByImgId != null) {
                        imageHashByImgId.put(imgId, hash);
                    }
                    return hash;
                }
            }
            MultimediaFile file = multimediaFileService.getById(Long.valueOf(imgId));
            try (InputStream stream = s3Service.download(file.getRelativePath())) {
                byte[] bytes = stream.readAllBytes();
                String hash = DigestUtil.sha256Hex(bytes);
                if (imageHashByImgId != null) {
                    imageHashByImgId.put(imgId, hash);
                }
                return hash;
            }
        } catch (Exception e) {
            log.warn("[resolveImageHash] imgId={} 计算hash失败", imgId, e);
            return null;
        }
    }

    /**
     * 将按 hash 存储的翻译结果映射回 imgId -> MultimediaFile。
     * 遍历原始 imgIds，通过 imageHashByImgId 取 hash，再从 translatedImageHashMap / preCachedImageMap 取翻译结果。
     */
    private Map<String, MultimediaFile> buildTranslatedImageMap(
            List<String> imgIds,
            Map<String, String> imageHashByImgId,
            Map<String, MultimediaFile> translatedImageHashMap,
            Map<String, MultimediaFile> preCachedImageMap) {
        Map<String, MultimediaFile> result = new HashMap<>();
        if (preCachedImageMap != null) {
            result.putAll(preCachedImageMap);
        }
        if (imageHashByImgId != null && translatedImageHashMap != null) {
            for (String imgId : imgIds) {
                if (result.containsKey(imgId)) continue;
                String hash = imageHashByImgId.get(imgId);
                if (hash != null) {
                    MultimediaFile translated = translatedImageHashMap.get(hash);
                    if (translated != null) {
                        result.put(imgId, translated);
                    }
                }
            }
        }
        return result;
    }

    private void cleanupBatchResources(String jobName, String uploadedFileName) {
        try {
            if (uploadedFileName != null) {
                geminiTranslateService.deleteFile(uploadedFileName);
            }
        } catch (Exception e) {
            log.warn("[cleanupBatch] 清理上传文件失败", e);
        }
        try {
            if (jobName != null) {
                geminiTranslateService.deleteBatchJob(jobName);
            }
        } catch (Exception e) {
            log.warn("[cleanupBatch] 删除 batch job 失败", e);
        }
    }


    /**
     * 获取当前分页进度
     *
     * @param paginated 分页数据
     * @return 进度
     */
    private <T> int printPaginationProgressMax99(Page<T> paginated) {
        // 获取当前页码（从0开始）
        int currentPage = paginated.getNumber();
        // 获取总页数
        int totalPages = paginated.getTotalPages();
        // 当前进度
        double progress = (currentPage + 1.0) / totalPages;
        // 映射的进度： RUNNING_PROGRESS ~ RESOLVE_PROGRESS
        double mappingProgress = RUNNING_PROGRESS + (RESOLVE_PROGRESS - RUNNING_PROGRESS) * progress;
        // 转成RUNNING_PROGRESS-99的进度
        return (int) Math.max(RUNNING_PROGRESS, Math.min(RESOLVE_PROGRESS, mappingProgress));
    }

    // 用 Apache POI 快速获取总行数（不会加载全部数据）
    public static int getTotalRowCount(String filePath, int sheetIndex) {
        try (InputStream is = new FileInputStream(filePath)) {
            Workbook workbook = WorkbookFactory.create(is);
            Sheet sheet = workbook.getSheetAt(sheetIndex);
            return sheet.getLastRowNum() + 1;
        } catch (Exception e) {
            throw new RuntimeException("读取总行数失败", e);
        }
    }

    // ======================== 翻译缓存辅助方法 ========================

    private void saveImageTranslationCache(String imageHash, MultimediaFile sourceFile, Language language,
                                           MultimediaFile translatedFile, boolean skipped, SystemUser owner) {
        try {
            if (imageHash == null || imageHash.isBlank()) {
                log.warn("[imageTranslationCache] imageHash 为空, 跳过写缓存: sourceFileId={}, langId={}",
                        sourceFile.getId(), language.getId());
                return;
            }
            ImageTranslationCache record = ImageTranslationCache.builder()
                    .imageHash(imageHash)
                    .sourceFile(sourceFile)
                    .language(language)
                    .translatedFile(translatedFile)
                    .skipped(skipped)
                    .build();
            imageTranslationCacheRepository.save(record);
        } catch (DataIntegrityViolationException e) {
            log.debug("[imageTranslationCache] 缓存已存在(并发写入): sourceFileId={}, langId={}", sourceFile.getId(), language.getId());
        } catch (Exception e) {
            log.warn("[imageTranslationCache] 写入缓存失败: sourceFileId={}, langId={}", sourceFile.getId(), language.getId(), e);
        }
    }

    /**
     * 查询单条文本的翻译缓存，命中返回译文，未命中返回 null。
     */
    private String lookupSingleTextCache(String text, Language language) {
        try {
            String hash = DigestUtil.sha256Hex(text);
            Optional<TextTranslationCache> cached = textTranslationCacheRepository
                    .findByContentHashAndLanguageIdAndContentType(hash, language.getId(), TranslationContentType.TEXT);
            if (cached.isPresent()) {
                String translated = cached.get().getTranslatedText();
                if (translated == null || translated.isBlank()) {
                    log.warn("[textCache] 命中脏缓存(空内容): hash={}, langId={}", hash, language.getId());
                    return null;
                }
                log.info("[textCache] 单条文本缓存命中: hash={}, langId={}", hash, language.getId());
                return translated;
            }
        } catch (Exception e) {
            log.warn("[textCache] 单条文本缓存查询失败: text.length={}", text.length(), e);
        }
        return null;
    }

    /**
     * 翻译单条文本并写入缓存，返回译文。
     */
    private String translateTextWithCache(String text, Language language, String langName) {
        String cached = lookupSingleTextCache(text, language);
        if (cached != null) return cached;
        String translated = geminiTranslateService.translateText(text, langName);
        writeSingleTextCache(text, translated, language);
        return translated;
    }

    private void writeSingleTextCache(String source, String translated, Language language) {
        try {
            String hash = DigestUtil.sha256Hex(source);
            Optional<TextTranslationCache> existing = textTranslationCacheRepository
                    .findByContentHashAndLanguageIdAndContentType(hash, language.getId(), TranslationContentType.TEXT);
            if (existing.isPresent()) return;
            TextTranslationCache cache = TextTranslationCache.builder()
                    .contentHash(hash)
                    .language(language)
                    .contentType(TranslationContentType.TEXT)
                    .sourceText(source)
                    .translatedText(translated)
                    .build();
            textTranslationCacheRepository.save(cache);
            log.info("[textCache] 写入单条文本缓存: hash={}, langId={}", hash, language.getId());
        } catch (DataIntegrityViolationException e) {
            log.debug("[textCache] 缓存已存在(并发写入): langId={}", language.getId());
        } catch (Exception e) {
            log.warn("[textCache] 写入缓存失败", e);
        }
    }

    /**
     * 仅查询 HTML 翻译缓存（不调 API），用于 Batch 提交前的前置过滤。
     * 命中返回翻译结果，未命中或异常返回 null。
     */
    private String lookupHtmlCache(String html, Language language) {
        try {
            String hash = DigestUtil.sha256Hex(html);
            Optional<TextTranslationCache> cached = textTranslationCacheRepository
                    .findByContentHashAndLanguageIdAndContentType(hash, language.getId(), TranslationContentType.HTML);
            if (cached.isPresent()) {
                if (cached.get().getTranslatedText() == null || cached.get().getTranslatedText().isBlank()) {
                    log.warn("[lookupHtmlCache] 命中脏缓存(空内容): hash={}, langId={}", hash, language.getId());
                    return null;
                }
                log.info("[lookupHtmlCache] 命中: hash={}, langId={}", hash, language.getId());
                return cached.get().getTranslatedText();
            }
        } catch (Exception e) {
            log.warn("[lookupHtmlCache] 查询失败", e);
        }
        return null;
    }

    private String translateHtmlWithCache(String html, Language language, String langName) {
        try {
            String hash = DigestUtil.sha256Hex(html);
            Optional<TextTranslationCache> cached = textTranslationCacheRepository
                    .findByContentHashAndLanguageIdAndContentType(hash, language.getId(), TranslationContentType.HTML);
            if (cached.isPresent()) {
                if (cached.get().getTranslatedText() == null || cached.get().getTranslatedText().isBlank()) {
                    log.warn("[htmlCache] 命中脏缓存(空内容), 重新翻译: hash={}, langId={}", hash, language.getId());
                } else {
                    log.info("[htmlCache] 命中HTML翻译缓存: hash={}, langId={}", hash, language.getId());
                    return cached.get().getTranslatedText();
                }
            }
            String translated = geminiTranslateService.translateHtml(html, langName);
            writeHtmlTranslationCache(html, translated, language, null);
            return translated;
        } catch (Exception e) {
            log.warn("[htmlCache] 缓存查询失败, 改走直调并尝试回写缓存", e);
            String translated = geminiTranslateService.translateHtml(html, langName);
            writeHtmlTranslationCache(html, translated, language, null);
            return translated;
        }
    }

    private void writeHtmlTranslationCache(String source, String translated, Language language, SystemUser owner) {
        try {
            String hash = DigestUtil.sha256Hex(source);
            Optional<TextTranslationCache> existing = textTranslationCacheRepository
                    .findByContentHashAndLanguageIdAndContentType(hash, language.getId(), TranslationContentType.HTML);
            if (existing.isPresent()) return;
            TextTranslationCache cache = TextTranslationCache.builder()
                    .contentHash(hash)
                    .language(language)
                    .contentType(TranslationContentType.HTML)
                    .sourceText(source.length() > 65535 ? source.substring(0, 65535) : source)
                    .translatedText(translated)
                    .build();
            textTranslationCacheRepository.save(cache);
            log.info("[htmlCache] 写入HTML翻译缓存: hash={}, langId={}", hash, language.getId());
        } catch (DataIntegrityViolationException e) {
            log.debug("[htmlCache] 缓存已存在(并发写入): langId={}", language.getId());
        } catch (Exception e) {
            log.warn("[htmlCache] 写入缓存失败", e);
        }
    }


    /**
     * 在异步线程中恢复多租户上下文。
     * @Async 方法运行在独立线程，ThreadLocal 中的 TenantContext 不会自动传递，
     * 需要根据 AsyncTask 所属的 companyId 手动设置。
     */
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
}
