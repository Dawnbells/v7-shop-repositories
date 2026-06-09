package cn.v7soft.admin.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import cn.hutool.core.lang.Pair;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.bean.copier.ValueProvider;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.poi.excel.BigExcelWriter;
import cn.hutool.poi.excel.ExcelUtil;
import cn.v7soft.admin.controller.req.DownloadOrderRequest;
import cn.v7soft.admin.controller.req.SyncThirdPartyOrdersRequest;
import cn.v7soft.admin.service.SyncMode;
import cn.v7soft.admin.service.IAddressService;
import cn.v7soft.admin.service.IAsyncTaskService;
import cn.v7soft.admin.service.IOrderService;
import cn.v7soft.admin.service.IOrderTemplateService;
import cn.v7soft.admin.service.IS3Service;
import cn.v7soft.admin.service.ITaskExecutorService;
import cn.v7soft.admin.service.IThirdPartyWebsiteService;
import cn.v7soft.admin.service.dto.OrderCheckInfoDto;
import cn.v7soft.admin.service.dto.OrderDownloadDto;
import cn.v7soft.admin.utils.OrderQueryHelper;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.entities.primary.Company;
import cn.v7soft.dao.entities.primary.Order;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import cn.v7soft.dao.tenant.TenantContext;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;

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

    private static final int BATCH_SIZE = 1000;
    private static final int RUNNING_PROGRESS = 1;
    private static final int RESOLVE_PROGRESS = 99;
    private static final int COMPLETED_OR_FAILED_PROGRESS = 100;

    private final IAddressService addressService;
    private final IOrderService orderService;
    private final IS3Service s3Service;
    private final IThirdPartyWebsiteService thirdPartyWebsiteService;
    private final IAsyncTaskService asyncTaskService;
    private final IOrderTemplateService orderTemplateService;
    private final AsyncTaskRepository asyncTaskRepository;
    private final ITaskExecutorService self;
    private final cn.v7soft.admin.service.ICompanyService companyService;

    public TaskExecutorService(IAsyncTaskService asyncTaskService, @Lazy IAddressService addressService,
                       @Lazy IOrderService orderService, IS3Service s3Service,
                       @Lazy IThirdPartyWebsiteService thirdPartyWebsiteService, IOrderTemplateService orderTemplateService,
                       AsyncTaskRepository asyncTaskRepository, @Lazy ITaskExecutorService self,
                       cn.v7soft.admin.service.ICompanyService companyService) {
        this.asyncTaskService = asyncTaskService;
        this.addressService = addressService;
        this.orderService = orderService;
        this.s3Service = s3Service;
        this.thirdPartyWebsiteService = thirdPartyWebsiteService;
        this.orderTemplateService = orderTemplateService;
        this.asyncTaskRepository = asyncTaskRepository;
        this.self = self;
        this.companyService = companyService;
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
                if (task.getTaskType() == TaskType.PRODUCT_AI_TRANSLATE) {
                    log.info("[recoverTasks] 跳过 AI 账号翻译任务（由 AiAccountTranslateTask 处理）: taskId={}", task.getId());
                    continue;
                }
                log.info("[recoverTasks] 重新执行任务: taskId={}, type={}, state={}", task.getId(), task.getTaskType(), task.getState());
                asyncTaskService.updateAsyncTask(task, TaskState.PENDING, 0);
                self.submitAsyncTask(task.getId());
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
        log.info("[resumeTranslateTask] 已废弃的翻译恢复请求: taskId={}", taskId);
    }

    @Override
    @Async("threadPoolTaskExecutor")
    public void submitAsyncTask(Long taskId) {
        cn.hutool.core.thread.ThreadUtil.sleep(500);
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
            } else if (task.getTaskType() == TaskType.ADDRESS_IMPORT) {
                executeAddressImport(task);
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

    private void executeAddressImport(AsyncTask task) {
        try {
            task.setMessage("正在准备导入...");
            asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, RUNNING_PROGRESS);

            String parameters = task.getParameters();
            String countryCode = JSONUtil.parseObj(parameters).getStr("countryCode");
            String filePath = task.getUploadFilePath();

            if (filePath == null || filePath.isBlank()) {
                task.setMessage("上传文件路径为空");
                asyncTaskService.updateAsyncTask(task, TaskState.FAILED, COMPLETED_OR_FAILED_PROGRESS);
                return;
            }
            if (!cn.hutool.core.io.FileUtil.exist(filePath)) {
                task.setMessage("上传文件不存在");
                asyncTaskService.updateAsyncTask(task, TaskState.FAILED, COMPLETED_OR_FAILED_PROGRESS);
                return;
            }

            Map<String, Object> result = addressService.importAddressesFromFile(countryCode, filePath,
                    (progress, message) -> {
                        task.setMessage(message);
                        asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING,
                                Math.max(RUNNING_PROGRESS, Math.min(RESOLVE_PROGRESS, progress)));
                    });

            task.setMessage((String) result.get("msg"));
            task.setParameters(JSONUtil.toJsonStr(result));
            asyncTaskService.updateAsyncTask(task, TaskState.COMPLETED, COMPLETED_OR_FAILED_PROGRESS);
        } catch (Throwable e) {
            log.error("[addressImport] taskId={} 导入失败", task.getId(), e);
            task.setMessage(e.getMessage());
            asyncTaskService.updateAsyncTask(task, TaskState.FAILED, COMPLETED_OR_FAILED_PROGRESS);
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
            String templateId = JSONUtil.parseObj(StrUtil.blankToDefault(task.getParameters(), "{}")).getStr("templateId");
            final Map<String, String> headerAliasMap = StrUtil.isBlank(templateId)
                    ? OrderCheckInfoDto.KEY_MAPPING
                    : orderTemplateService.getHeaderAliasMap(templateId, true);
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
                            return getUploadCellValue(rowCells, rowNameMap, headerAliasMap, key);
                        }
                        @Override
                        public boolean containsKey(String key) {
                            return hasUploadCell(rowCells, rowNameMap, headerAliasMap, key);
                        }
                    }, CopyOptions.create());
                    String orderId = StrUtil.blankToDefault(
                            orderCheckInfoDto.getOrderId(),
                            Objects.toString(getUploadCellValue(rowCells, rowNameMap, headerAliasMap, "originOrderId"), null));
                    orderService.applyCheckInfoAndSave(orderId, orderCheckInfoDto, owner);
                    successIds.add(orderId);
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

    private static boolean hasUploadCell(List<?> rowCells, Map<String, Integer> rowNameMap,
                                         Map<String, String> headerAliasMap, String key) {
        String headerName = headerAliasMap.get(key);
        Integer index = rowNameMap.get(headerName);
        return StrUtil.isNotBlank(headerName) && index != null && index < rowCells.size();
    }

    private static Object getUploadCellValue(List<?> rowCells, Map<String, Integer> rowNameMap,
                                             Map<String, String> headerAliasMap, String key) {
        if (!hasUploadCell(rowCells, rowNameMap, headerAliasMap, key)) {
            return null;
        }
        return rowCells.get(rowNameMap.get(headerAliasMap.get(key)));
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
                Page<OrderDownloadDto> paginated = orderService.findPaginatedForDownload(OrderQueryHelper.convertOrderQueryPageRequest(request, orderService), owner, task.getViewMode());
                asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, printPaginationProgressMax99(paginated));
                writer.write(paginated.stream().map(BeanUtil::trimStrFields).toList());
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
        int page = 0;
        int fetchedCount = 0;
        int successCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        try {
            task.setMessage("正在连接Shopline...");
            asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, RUNNING_PROGRESS);
            String parameters = task.getParameters();
            SyncThirdPartyOrdersRequest request = JSONUtil.toBean(parameters, SyncThirdPartyOrdersRequest.class);
            String pageInfo = "";
            while (pageInfo != null) {
                var result = thirdPartyWebsiteService.loadOrders(request, pageInfo, SyncMode.MANUAL);
                pageInfo = result.getNextPageInfo();
                page++;
                fetchedCount += result.getFetchedCount();
                successCount += result.getSuccessCount();
                failedCount += result.getFailedCount();
                skippedCount += result.getSkippedCount();
                int progress = Math.min(page * 10, 99);
                task.setMessage("正在同步第 " + page + " 页，已成功 " + successCount + " 条，失败 " + failedCount + " 条");
                asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, progress);
            }
            thirdPartyWebsiteService.updateLastManualSyncTime(request.getIdLongValue());
            task.setMessage("同步完成，成功: " + successCount + " 条，失败: " + failedCount + " 条（共拉取 " + fetchedCount + " 条，" + page + " 页）");
            asyncTaskService.updateAsyncTask(task, TaskState.COMPLETED, COMPLETED_OR_FAILED_PROGRESS);
        } catch (Throwable e) {
            log.error("Shopline手动同步任务异常: taskId={}, pages={}, fetched={}, success={}, failed={}, skipped={}",
                    task.getId(), page, fetchedCount, successCount, failedCount, skippedCount, e);
            String partialInfo = page > 0 ? "（已完成 " + page + " 页，成功 " + successCount + " 条，失败 " + failedCount + " 条）" : "";
            task.setMessage("同步失败: " + e.getMessage() + partialInfo);
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
