package cn.v7soft.admin.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.bean.copier.ValueProvider;
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
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.entities.primary.Order;
import cn.v7soft.dao.entities.primary.Product;
import cn.v7soft.dao.entities.primary.ProductSpecification;
import cn.v7soft.dao.entities.primary.ProductSpecificationAttributes;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
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

    private final IOrderService orderService;
    private final IS3Service s3Service;
    private final IThirdPartyWebsiteService thirdPartyWebsiteService;
    private final IAsyncTaskService asyncTaskService;
    private final IOrderTemplateService orderTemplateService;
    private final IProductService productService;
    private final GeminiTranslateService geminiTranslateService;
    private final IMultimediaFileService multimediaFileService;
    private final cn.v7soft.admin.service.ILanguageService languageService;
    private final AsyncTaskRepository asyncTaskRepository;
    private final ITaskService self;

    public TaskService(IAsyncTaskService asyncTaskService, @Lazy IOrderService orderService, IS3Service s3Service,
                       @Lazy IThirdPartyWebsiteService thirdPartyWebsiteService, IOrderTemplateService orderTemplateService,
                       @Lazy IProductService productService, GeminiTranslateService geminiTranslateService,
                       IMultimediaFileService multimediaFileService, cn.v7soft.admin.service.ILanguageService languageService,
                       AsyncTaskRepository asyncTaskRepository, @Lazy ITaskService self) {
        this.asyncTaskService = asyncTaskService;
        this.orderService = orderService;
        this.s3Service = s3Service;
        this.thirdPartyWebsiteService = thirdPartyWebsiteService;
        this.orderTemplateService = orderTemplateService;
        this.productService = productService;
        this.geminiTranslateService = geminiTranslateService;
        this.multimediaFileService = multimediaFileService;
        this.languageService = languageService;
        this.asyncTaskRepository = asyncTaskRepository;
        this.self = self;
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
        log.info("[retry] taskId={} 已重置为 PENDING, 重新提交任务", taskId);
        submitAsyncTask(task.getId());
        return AsyncTaskResponse.convert(task);
    }

    @Override
    @Async("threadPoolTaskExecutor")
    public void executeDirectTranslateAsync(Long taskId) {
        ThreadUtil.sleep(500);
        Pair<AsyncTask, SystemUserDto> pair = asyncTaskService.getAndInitializeOwner(taskId);
        AsyncTask task = pair.getKey();
        executeProductAITranslateDirect(task);
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
                    resumeTranslateTask(task.getId());
                } else {
                    log.info("[recoverTasks] 重新执行任务: taskId={}, type={}, state={}", task.getId(), task.getTaskType(), task.getState());
                    asyncTaskService.updateAsyncTask(task, TaskState.PENDING, 0);
                    submitAsyncTask(task.getId());
                }
            } catch (Exception e) {
                log.error("[recoverTasks] 恢复任务失败: taskId={}", task.getId(), e);
                task.setMessage("恢复任务失败: " + e.getMessage());
                asyncTaskService.updateAsyncTask(task, TaskState.FAILED, COMPLETED_OR_FAILED_PROGRESS);
            }
        }
    }

    @Async("threadPoolTaskExecutor")
    void resumeTranslateTask(Long taskId) {
        Pair<AsyncTask, SystemUserDto> pair = asyncTaskService.getAndInitializeOwner(taskId);
        AsyncTask task = pair.getKey();
        log.info("[resumeTranslateTask] 开始恢复AI翻译: taskId={}", taskId);
        resumeProductAITranslate(task);
    }

    @Override
    @Async("threadPoolTaskExecutor")
    public void submitAsyncTask(Long taskId) {
        ThreadUtil.sleep(500);
        Pair<AsyncTask, SystemUserDto> pair = asyncTaskService.getAndInitializeOwner(taskId);
        AsyncTask task = pair.getKey();
        SystemUserDto owner = pair.getValue();
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

            Product product = productService.getByIdWithSpecifications(Long.parseLong(request.getProductId()));
            Language language = languageService.getById(Long.valueOf(request.getLanguageId()));
            SystemUser owner = task.getOwner();
            String langName = language.getName();

            log.info("[batchTranslate] taskId={} 产品: title='{}', targetLang='{}'",
                    task.getId(), product.getTitle(), langName);

            // === Step 1: 收集并提交 ===
            task.setMessage("正在准备翻译内容...");
            asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 2);

            List<String> textsToTranslate = collectTextsToTranslate(product);
            String introduction = product.getIntroduction();
            List<String> imgIds = collectImageIds(introduction);

            log.info("[batchTranslate] taskId={} 收集内容: texts={} 条, htmlLength={}, 详情图片={} 张",
                    task.getId(), textsToTranslate.size(),
                    introduction != null ? introduction.length() : 0, imgIds.size());

            Map<String, byte[]> originalImageBytes = new HashMap<>();
            Map<String, String> imageMimeTypes = new HashMap<>();
            List<String> translatableImgIds = new ArrayList<>();

            downloadAndFilterImages(imgIds, originalImageBytes, imageMimeTypes, translatableImgIds);

            log.info("[batchTranslate] taskId={} 图片过滤完成: 原始={} 张, 可翻译={} 张 (跳过动图 {} 张)",
                    task.getId(), imgIds.size(), translatableImgIds.size(), imgIds.size() - translatableImgIds.size());

            StringBuilder jsonl = new StringBuilder();
            int totalRequests = 0;

            jsonl.append(geminiTranslateService.buildTextsTranslateJsonlEntry("texts", textsToTranslate, langName)).append("\n");
            totalRequests++;

            if (introduction != null && !introduction.isBlank()) {
                jsonl.append(geminiTranslateService.buildHtmlTranslateJsonlEntry("html", introduction, langName)).append("\n");
                totalRequests++;
            }

            for (String imgId : translatableImgIds) {
                jsonl.append(geminiTranslateService.buildImageTranslateJsonlEntry(
                        "img-" + imgId, originalImageBytes.get(imgId), imageMimeTypes.get(imgId), langName)).append("\n");
                totalRequests++;
            }

            log.info("[batchTranslate] taskId={} JSONL 构建完成: totalRequests={}, jsonlSize={}bytes",
                    task.getId(), totalRequests, jsonl.length());

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

            // Step 2: 轮询等待 Batch 完成
            String batchState = pollBatchJobUntilDone(task, jobName, uploadedFileName, totalRequests);
            if (batchState == null) {
                log.info("[batchTranslate] taskId={} 轮询期间任务被取消", task.getId());
                return;
            }

            // Step 3 ~ 5: 处理结果、补刀、保存（已有图片数据，直接传入）
            BatchJob currentJob = geminiTranslateService.getBatchJob(jobName);
            processBatchResultAndSave(task, batchState, currentJob, jobName, uploadedFileName,
                    product, language, owner, textsToTranslate, introduction, imgIds,
                    originalImageBytes, imageMimeTypes, translatableImgIds);

            log.info("[batchTranslate] taskId={} 批量翻译流程结束, 总耗时={}ms, 最终状态={}",
                    task.getId(), System.currentTimeMillis() - taskStart, task.getState());

        } catch (Throwable e) {
            log.error("[batchTranslate] taskId={} 批量翻译任务异常, 总耗时={}ms",
                    task.getId(), System.currentTimeMillis() - taskStart, e);
            task.setMessage(e.getMessage());
            asyncTaskService.updateAsyncTask(task, TaskState.FAILED, COMPLETED_OR_FAILED_PROGRESS);
        }
    }

    /**
     * 即时翻译模式：不走 Batch API，逐条调用 Gemini 直接翻译。
     * Token 消耗更多，但速度更快。
     */
    private void executeProductAITranslateDirect(AsyncTask task) {
        long taskStart = System.currentTimeMillis();
        try {
            TranslateByAIRequest request = JSONUtil.toBean(task.getParameters(), TranslateByAIRequest.class);
            log.info("[directTranslate] taskId={} 开始即时翻译, productId={}, languageId={}",
                    task.getId(), request.getProductId(), request.getLanguageId());

            Product product = productService.getByIdWithSpecifications(Long.parseLong(request.getProductId()));
            Language language = languageService.getById(Long.valueOf(request.getLanguageId()));
            SystemUser owner = task.getOwner();
            String langName = language.getName();

            log.info("[directTranslate] taskId={} 产品: title='{}', targetLang='{}'",
                    task.getId(), product.getTitle(), langName);

            task.setMessage("即时翻译: 正在准备...");
            asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 2);

            List<String> textsToTranslate = collectTextsToTranslate(product);
            String introduction = product.getIntroduction();
            List<String> imgIds = collectImageIds(introduction);

            Map<String, byte[]> originalImageBytes = new HashMap<>();
            Map<String, String> imageMimeTypes = new HashMap<>();
            List<String> translatableImgIds = new ArrayList<>();
            downloadAndFilterImages(imgIds, originalImageBytes, imageMimeTypes, translatableImgIds);

            log.info("[directTranslate] taskId={} 收集内容: texts={} 条, htmlLength={}, 可翻译图片={} 张",
                    task.getId(), textsToTranslate.size(),
                    introduction != null ? introduction.length() : 0, translatableImgIds.size());

            int totalSteps = 1 + (introduction != null && !introduction.isBlank() ? 1 : 0) + translatableImgIds.size() + 1;
            int doneSteps = 0;

            // --- 翻译文本 ---
            long stepStart = System.currentTimeMillis();
            task.setMessage("即时翻译: 翻译文本...");
            asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 5);
            List<String> translatedTexts = geminiTranslateService.translateTexts(textsToTranslate, langName);
            doneSteps++;
            log.info("[directTranslate] taskId={} 文本翻译完成: {} 条, 耗时={}ms",
                    task.getId(), translatedTexts.size(), System.currentTimeMillis() - stepStart);
            asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, progressOf(doneSteps, totalSteps));

            // --- 翻译 HTML ---
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
                translatedHtml = geminiTranslateService.translateHtml(introduction, langName);
                doneSteps++;
                log.info("[directTranslate] taskId={} HTML翻译完成: 输入长度={}, 输出长度={}, 耗时={}ms",
                        task.getId(), introduction.length(),
                        translatedHtml != null ? translatedHtml.length() : 0,
                        System.currentTimeMillis() - stepStart);
                asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, progressOf(doneSteps, totalSteps));
            }

            // --- 翻译图片 ---
            Map<String, byte[]> translatedImageMap = new HashMap<>();
            int imgTranslated = 0, imgSkipped = 0, imgFailed = 0;
            for (int i = 0; i < translatableImgIds.size(); i++) {
                AsyncTask check = asyncTaskService.getById(task.getId());
                if (check.getState() == TaskState.CANCELLED) {
                    log.info("[directTranslate] taskId={} 图片翻译阶段任务已取消, 已完成 {}/{}", task.getId(), i, translatableImgIds.size());
                    return;
                }

                String imgId = translatableImgIds.get(i);
                task.setMessage("即时翻译: 翻译图片 (" + (i + 1) + "/" + translatableImgIds.size() + ")...");
                asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, progressOf(doneSteps, totalSteps));
                try {
                    byte[] imgBytes = originalImageBytes.get(imgId);
                    String mimeType = imageMimeTypes.get(imgId);
                    if (imgBytes != null && mimeType != null) {
                        stepStart = System.currentTimeMillis();
                        byte[] result = geminiTranslateService.translateImage(imgBytes, mimeType, langName);
                        if (result != null) {
                            translatedImageMap.put(imgId, result);
                            imgTranslated++;
                            log.info("[directTranslate] taskId={} 图片[{}/{}] imgId={} 翻译成功: 原始={}bytes, 结果={}bytes, 耗时={}ms",
                                    task.getId(), i + 1, translatableImgIds.size(), imgId,
                                    imgBytes.length, result.length, System.currentTimeMillis() - stepStart);
                        } else {
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

            log.info("[directTranslate] taskId={} 图片翻译汇总: 总计={}, 已翻译={}, 无需翻译={}, 失败={}",
                    task.getId(), translatableImgIds.size(), imgTranslated, imgSkipped, imgFailed);

            // --- 保存结果 ---
            AsyncTask preSave = asyncTaskService.getById(task.getId());
            if (preSave.getState() == TaskState.CANCELLED) {
                log.info("[directTranslate] taskId={} 保存前任务已取消", task.getId());
                return;
            }

            stepStart = System.currentTimeMillis();
            task.setMessage("即时翻译: 保存结果...");
            asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 97);

            productService.assembleTranslatedProduct(
                    product, language, owner, translatedTexts,
                    translatedHtml != null ? translatedHtml : introduction,
                    translatedImageMap);

            log.info("[directTranslate] taskId={} 保存完成, 耗时={}ms", task.getId(), System.currentTimeMillis() - stepStart);

            task.setMessage("翻译完成");
            asyncTaskService.updateAsyncTask(task, TaskState.COMPLETED, COMPLETED_OR_FAILED_PROGRESS);

            log.info("[directTranslate] taskId={} 即时翻译全部完成, 总耗时={}ms", task.getId(), System.currentTimeMillis() - taskStart);
        } catch (Throwable e) {
            log.error("[directTranslate] taskId={} 即时翻译任务异常, 总耗时={}ms",
                    task.getId(), System.currentTimeMillis() - taskStart, e);
            task.setMessage(e.getMessage());
            asyncTaskService.updateAsyncTask(task, TaskState.FAILED, COMPLETED_OR_FAILED_PROGRESS);
        }
    }

    private int progressOf(int doneSteps, int totalSteps) {
        return 5 + (int) (90.0 * doneSteps / Math.max(1, totalSteps));
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

            // === Phase 1: 轻量轮询，只查 Batch Job 状态 ===
            String batchState = pollBatchJobUntilDone(task, jobName, null, savedTotalRequests);
            if (batchState == null) {
                // 任务已被取消
                return;
            }

            log.info("[resumeTranslate] taskId={} Batch轮询结束, state={}, 轮询耗时={}ms, 开始加载产品数据...",
                    task.getId(), batchState, System.currentTimeMillis() - taskStart);

            // === Phase 2: Batch 完成，现在才加载产品信息和图片 ===
            task.setMessage("正在加载产品数据...");
            asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 82);

            TranslateByAIRequest request = JSONUtil.toBean(task.getParameters(), TranslateByAIRequest.class);
            Product product = productService.getByIdWithSpecifications(Long.parseLong(request.getProductId()));
            Language language = languageService.getById(Long.valueOf(request.getLanguageId()));
            SystemUser owner = task.getOwner();
            String langName = language.getName();

            List<String> textsToTranslate = collectTextsToTranslate(product);
            String introduction = product.getIntroduction();
            List<String> imgIds = collectImageIds(introduction);

            log.info("[resumeTranslate] taskId={} 产品数据加载完成: title='{}', texts={} 条, htmlLength={}, 详情图片={} 张",
                    task.getId(), product.getTitle(), textsToTranslate.size(),
                    introduction != null ? introduction.length() : 0, imgIds.size());

            // === Phase 3: 下载结果并解析（无预加载图片，需要时懒加载） ===
            BatchJob currentJob = geminiTranslateService.getBatchJob(jobName);
            processBatchResultAndSave(task, batchState, currentJob, jobName, null,
                    product, language, owner, textsToTranslate, introduction, imgIds,
                    null, null, null);

            log.info("[resumeTranslate] taskId={} 恢复流程全部完成, 总耗时={}ms, 最终状态={}",
                    task.getId(), System.currentTimeMillis() - taskStart, task.getState());

        } catch (Throwable e) {
            log.error("[resumeTranslate] taskId={} 恢复任务失败, 总耗时={}ms",
                    task.getId(), System.currentTimeMillis() - taskStart, e);
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
     *
     * @param preloadedImageBytes 预下载的原图数据，null 表示需要补刀时再懒加载（恢复场景）
     * @param preloadedMimeTypes  预下载的 MIME 类型，与 preloadedImageBytes 配对
     * @param preloadedTranslatableImgIds 预下载时筛选出的可翻译图片 ID，null 则从结果中推断
     */
    private void processBatchResultAndSave(AsyncTask task, String batchState, BatchJob currentJob,
                                           String jobName, String uploadedFileName,
                                           Product product, Language language, SystemUser owner,
                                           List<String> textsToTranslate, String introduction,
                                           List<String> imgIds,
                                           Map<String, byte[]> preloadedImageBytes,
                                           Map<String, String> preloadedMimeTypes,
                                           List<String> preloadedTranslatableImgIds) throws Exception {
        String langName = language.getName();
        boolean hasPreloadedImages = (preloadedImageBytes != null && !preloadedImageBytes.isEmpty());

        log.info("[processResult] taskId={} 开始处理Batch结果: batchState={}, 预加载图片={}", task.getId(), batchState, hasPreloadedImages);

        // --- 解析 Batch 结果 ---
        List<String> translatedTexts = null;
        String translatedHtml = null;
        Map<String, byte[]> translatedImageMap = new HashMap<>();
        List<String> batchFailedImgIds = new ArrayList<>();

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
                    log.info("[processResult] taskId={} 结果解析完成: keys={}", task.getId(), resultMap.keySet());

                    // 文本
                    JsonNode textsNode = resultMap.get("texts");
                    if (textsNode != null && textsNode.has("response")) {
                        try {
                            String textsJson = extractTextFromResponse(textsNode.get("response"));
                            List<String> parsed = OBJECT_MAPPER.readValue(textsJson,
                                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
                            if (parsed.size() == textsToTranslate.size()) {
                                translatedTexts = parsed;
                                log.info("[processResult] taskId={} 文本翻译结果OK: {} 条", task.getId(), parsed.size());
                            } else {
                                log.warn("[processResult] taskId={} 文本数量不匹配: expected={}, actual={}, 走fallback",
                                        task.getId(), textsToTranslate.size(), parsed.size());
                            }
                        } catch (Exception e) {
                            log.error("[processResult] taskId={} 解析文本翻译结果失败", task.getId(), e);
                        }
                    } else {
                        log.warn("[processResult] taskId={} 文本翻译结果缺失, 走fallback", task.getId());
                    }

                    // HTML
                    JsonNode htmlNode = resultMap.get("html");
                    if (htmlNode != null && htmlNode.has("response")) {
                        translatedHtml = extractTextFromResponse(htmlNode.get("response"));
                        log.info("[processResult] taskId={} HTML翻译结果: length={}",
                                task.getId(), translatedHtml != null ? translatedHtml.length() : 0);
                    } else if (introduction != null && !introduction.isBlank()) {
                        log.warn("[processResult] taskId={} HTML翻译结果缺失, 走fallback", task.getId());
                    }

                    // 图片：确定哪些图片ID在 Batch 中有结果
                    List<String> imgIdsInBatch = new ArrayList<>();
                    for (String key : resultMap.keySet()) {
                        if (key.startsWith("img-")) {
                            imgIdsInBatch.add(key.substring(4));
                        }
                    }

                    // 使用预加载的列表或从结果推断
                    List<String> translatableImgIds = (preloadedTranslatableImgIds != null)
                            ? preloadedTranslatableImgIds : imgIdsInBatch;

                    for (String imgId : translatableImgIds) {
                        JsonNode imgNode = resultMap.get("img-" + imgId);
                        if (imgNode == null || !imgNode.has("response")) {
                            batchFailedImgIds.add(imgId);
                            log.info("[processResult] taskId={} 图片 imgId={} 无结果, 需补刀", task.getId(), imgId);
                            continue;
                        }
                        byte[] imgBytes = extractImageFromResponse(imgNode.get("response"));
                        if (imgBytes != null) {
                            translatedImageMap.put(imgId, imgBytes);
                            log.info("[processResult] taskId={} 图片 imgId={} 翻译成功: {}bytes", task.getId(), imgId, imgBytes.length);
                        } else {
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

        boolean needTextFallback = (translatedTexts == null);
        boolean needHtmlFallback = (translatedHtml == null && introduction != null && !introduction.isBlank());
        boolean needImageFallback = !batchFailedImgIds.isEmpty();

        log.info("[processResult] taskId={} 结果汇总: texts={}, html={}, batchImages={}, 需补刀图片={}",
                task.getId(),
                translatedTexts != null ? "OK(" + translatedTexts.size() + ")" : "FALLBACK",
                translatedHtml != null ? "OK" : (needHtmlFallback ? "FALLBACK" : "N/A"),
                translatedImageMap.size(), batchFailedImgIds.size());

        // --- Fallback: 文本 ---
        if (needTextFallback) {
            long fbStart = System.currentTimeMillis();
            task.setMessage("正在补充翻译文本...");
            asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 84);
            translatedTexts = geminiTranslateService.translateTexts(textsToTranslate, langName);
            log.info("[processResult] taskId={} 文本补刀完成: {} 条, 耗时={}ms",
                    task.getId(), translatedTexts.size(), System.currentTimeMillis() - fbStart);
        }
        // --- Fallback: HTML ---
        if (needHtmlFallback) {
            long fbStart = System.currentTimeMillis();
            task.setMessage("正在补充翻译HTML...");
            asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 86);
            translatedHtml = geminiTranslateService.translateHtml(introduction, langName);
            log.info("[processResult] taskId={} HTML补刀完成: length={}, 耗时={}ms",
                    task.getId(), translatedHtml != null ? translatedHtml.length() : 0,
                    System.currentTimeMillis() - fbStart);
        }

        // --- Fallback: 图片 ---
        if (needImageFallback) {
            Map<String, byte[]> fbImageBytes;
            Map<String, String> fbMimeTypes;
            List<String> fbImgIds;

            if (hasPreloadedImages) {
                // 新提交场景：图片已在内存，直接用
                fbImageBytes = preloadedImageBytes;
                fbMimeTypes = preloadedMimeTypes;
                fbImgIds = batchFailedImgIds;
                log.info("[processResult] taskId={} 使用预加载图片数据补刀 {} 张", task.getId(), fbImgIds.size());
            } else {
                // 恢复场景：只下载需要补刀的图片
                log.info("[processResult] taskId={} 需补刀 {} 张图片, 开始按需下载原图...", task.getId(), batchFailedImgIds.size());
                task.setMessage("正在下载待补刀的图片...");
                asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 88);

                fbImageBytes = new HashMap<>();
                fbMimeTypes = new HashMap<>();
                fbImgIds = new ArrayList<>();
                downloadAndFilterImages(batchFailedImgIds, fbImageBytes, fbMimeTypes, fbImgIds);
            }

            int fbSuccess = 0, fbFail = 0;
            for (int i = 0; i < fbImgIds.size(); i++) {
                AsyncTask checkTask = asyncTaskService.getById(task.getId());
                if (checkTask.getState() == TaskState.CANCELLED) {
                    log.info("[processResult] taskId={} 补刀阶段任务被取消, 已补刀 {}/{}", task.getId(), i, fbImgIds.size());
                    return;
                }

                String imgId = fbImgIds.get(i);
                task.setMessage("正在补充翻译图片 (" + (i + 1) + "/" + fbImgIds.size() + ")...");
                int fallbackProgress = 88 + (int) (8.0 * (i + 1) / fbImgIds.size());
                asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, Math.min(fallbackProgress, 96));

                try {
                    long fbStart = System.currentTimeMillis();
                    byte[] imgBytes = fbImageBytes.get(imgId);
                    String mimeType = fbMimeTypes.get(imgId);
                    if (imgBytes != null && mimeType != null) {
                        byte[] result = geminiTranslateService.translateImage(imgBytes, mimeType, langName);
                        if (result != null) {
                            translatedImageMap.put(imgId, result);
                            fbSuccess++;
                            log.info("[processResult] taskId={} 补刀图片[{}/{}] imgId={} 成功: {}bytes, 耗时={}ms",
                                    task.getId(), i + 1, fbImgIds.size(), imgId, result.length, System.currentTimeMillis() - fbStart);
                        } else {
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
            log.info("[processResult] taskId={} 补刀完成: 成功={}, 失败={}", task.getId(), fbSuccess, fbFail);
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

        log.info("[processResult] taskId={} 开始组装保存: texts={} 条, html={}, images={} 张",
                task.getId(), translatedTexts.size(),
                translatedHtml != null ? translatedHtml.length() + "chars" : "null",
                translatedImageMap.size());

        productService.assembleTranslatedProduct(
                product, language, owner, translatedTexts,
                translatedHtml != null ? translatedHtml : introduction,
                translatedImageMap);

        log.info("[processResult] taskId={} 保存完成, 耗时={}ms", task.getId(), System.currentTimeMillis() - saveStart);

        task.setMessage("翻译完成");
        asyncTaskService.updateAsyncTask(task, TaskState.COMPLETED, COMPLETED_OR_FAILED_PROGRESS);
    }

    /**
     * 下载原图并过滤动图，填充输出参数。
     */
    private void downloadAndFilterImages(List<String> imgIds,
                                         Map<String, byte[]> originalImageBytes,
                                         Map<String, String> imageMimeTypes,
                                         List<String> translatableImgIds) {
        log.info("[downloadImages] 开始下载 {} 张图片", imgIds.size());
        for (int i = 0; i < imgIds.size(); i++) {
            String imgId = imgIds.get(i);
            try {
                MultimediaFile file = multimediaFileService.getById(Long.valueOf(imgId));
                String suffix = file.getSuffix().toLowerCase();

                if ("gif".equalsIgnoreCase(suffix)) {
                    log.info("[downloadImages] [{}/{}] imgId={} 跳过动图 gif", i + 1, imgIds.size(), imgId);
                    continue;
                }

                byte[] bytes;
                try (InputStream stream = s3Service.download(file.getRelativePath())) {
                    bytes = stream.readAllBytes();
                }

                if ("webp".equalsIgnoreCase(suffix) && isAnimatedWebp(bytes)) {
                    log.info("[downloadImages] [{}/{}] imgId={} 跳过动图 webp", i + 1, imgIds.size(), imgId);
                    continue;
                }

                String mimeType = "image/" + ("jpg".equalsIgnoreCase(suffix) ? "jpeg" : suffix);
                originalImageBytes.put(imgId, bytes);
                imageMimeTypes.put(imgId, mimeType);
                translatableImgIds.add(imgId);
                log.info("[downloadImages] [{}/{}] imgId={} 下载完成: suffix={}, mimeType={}, size={}bytes",
                        i + 1, imgIds.size(), imgId, suffix, mimeType, bytes.length);
            } catch (Exception e) {
                log.warn("[downloadImages] [{}/{}] imgId={} 下载失败, 使用原图", i + 1, imgIds.size(), imgId, e);
            }
        }
    }

    private List<String> collectTextsToTranslate(Product product) {
        List<String> texts = new ArrayList<>();
        texts.add(product.getTitle() != null ? product.getTitle() : "");
        texts.add(product.getSummary() != null ? product.getSummary() : "");
        for (ProductSpecification spec : product.getSpecificationList()) {
            for (ProductSpecificationAttributes attr : spec.getAttributes()) {
                texts.add(attr.getName() != null ? attr.getName() : "");
                texts.add(attr.getValue() != null ? attr.getValue() : "");
            }
        }
        return texts;
    }

    private List<String> collectImageIds(String introduction) {
        List<String> imgIds = new ArrayList<>();
        if (introduction == null) return imgIds;
        Matcher matcher = IMG_ID_PATTERN.matcher(introduction);
        while (matcher.find()) {
            imgIds.add(matcher.group(1));
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
            return sheet.getLastRowNum() + 1; // 注意：getLastRowNum 是从 0 开始的
        } catch (Exception e) {
            throw new RuntimeException("读取总行数失败", e);
        }
    }
}
