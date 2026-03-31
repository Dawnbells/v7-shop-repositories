package cn.v7soft.admin.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.entities.primary.Order;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TaskService implements ITaskService {

    private final static int BATCH_SIZE = 1000;
    /**
     * 任务开始执行时的任务进度
     */
    private final static int RUNNING_PROGRESS = 1;
    private static final int RESOLVE_PROGRESS = 99;
    private static final int COMPLETED_OR_FAILED_PROGRESS = 100;
    private final IOrderService orderService;
    private final IS3Service s3Service;
    private final IThirdPartyWebsiteService thirdPartyWebsiteService;
    private final IAsyncTaskService asyncTaskService;
    private final IOrderTemplateService orderTemplateService;
    private final IProductService productService;

    public TaskService(IAsyncTaskService asyncTaskService, @Lazy IOrderService orderService, IS3Service s3Service,
                       @Lazy IThirdPartyWebsiteService thirdPartyWebsiteService, IOrderTemplateService orderTemplateService,
                       @Lazy IProductService productService) {
        this.asyncTaskService = asyncTaskService;
        this.orderService = orderService;
        this.s3Service = s3Service;
        this.thirdPartyWebsiteService = thirdPartyWebsiteService;
        this.orderTemplateService = orderTemplateService;
        this.productService = productService;
    }

    @Override
    public AsyncTaskResponse status(Long taskId) {
        AsyncTask task = asyncTaskService.getById(taskId);
        return AsyncTaskResponse.convert(task);
    }

    @Override
    public InputStream download(Long id) {
        AsyncTask task = asyncTaskService.getById(id);
        return s3Service.download(task.getExportRelativePath());
    }

    @Override
    @Async("threadPoolTaskExecutor")
    public void submitAsyncTask(Long taskId) {
        // 处理任务逻辑,等待500ms，等待保存的事务提交完成
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
        try {
            task.setMessage("正在翻译");
            asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, RUNNING_PROGRESS);
            TranslateByAIRequest request = JSONUtil.toBean(task.getParameters(), TranslateByAIRequest.class);
            productService.translateByAI(request, task.getOwner());
            task.setMessage("翻译完成");
            asyncTaskService.updateAsyncTask(task, TaskState.COMPLETED, COMPLETED_OR_FAILED_PROGRESS);
        } catch (Throwable e) {
            log.error("AI翻译任务执行失败: ", e);
            task.setMessage(e.getMessage());
            asyncTaskService.updateAsyncTask(task, TaskState.FAILED, COMPLETED_OR_FAILED_PROGRESS);
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
