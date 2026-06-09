package cn.v7soft.admin.service.impl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import cn.v7soft.admin.controller.req.DownloadOrderRequest;
import cn.v7soft.admin.controller.req.attributes.OrderAccessDataRangeAttribute;
import cn.v7soft.admin.controller.req.UpdateContactStatusRequest;
import cn.v7soft.admin.controller.req.UpdateOrderStatusRequest;
import cn.v7soft.admin.controller.req.UpdateRemarkRequest;
import cn.v7soft.admin.service.IOrderService;
import cn.v7soft.admin.service.ITaskExecutorService;
import cn.v7soft.admin.service.dto.OrderCheckInfoDto;
import cn.v7soft.admin.service.dto.OrderDownloadDto;
import cn.v7soft.common.enums.AccessDataRangeLevel;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.common.utils.ConvertUtils;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.OrQueryAttribute;
import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.entities.primary.Order;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;
import cn.v7soft.dao.enums.ViewMode;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import cn.v7soft.dao.repositories.primary.OrderRepository;
import cn.v7soft.dao.repositories.primary.TemporaryOrderRepository;
import cn.v7soft.dao.utils.SaSessionUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OrderService extends BaseDataRangeService<Order, OrderRepository> implements IOrderService {

    private final AsyncTaskRepository asyncTaskRepository;
    private final ITaskExecutorService taskExecutorService;
    private final TemporaryOrderRepository temporaryOrderRepository;

    public OrderService(OrderRepository repository, AsyncTaskRepository asyncTaskRepository, ITaskExecutorService taskExecutorService, TemporaryOrderRepository temporaryOrderRepository) {
        super(repository);
        this.asyncTaskRepository = asyncTaskRepository;
        this.taskExecutorService = taskExecutorService;
        this.temporaryOrderRepository = temporaryOrderRepository;
    }

    @Override
    public List<Order> getOrdersByStatus(String status) {
//        return repository.findOrdersByStatus(status);
        return List.of();
    }

    @Override
    public Page<Order> getOrdersByUserId(Long userId, Pageable pageable) {
//        return repository.findAllByCreatedBy(userId, pageable);
        return Page.empty();
    }

    @Override
    @Transactional
    public List<String> updateOrderStatus(UpdateOrderStatusRequest request) {
        List<String> failedOrderReasons = new ArrayList<>();
        for (Long orderId : request.getIds() ) {
            Optional<Order> orderOptional = findById(orderId);
            if (orderOptional.isEmpty()) {
                failedOrderReasons.add(orderId + ": 订单不存在");
                continue;
            }
            Order order = orderOptional.get();
            order.setOrderStatus(request.getStatus());
            if (request.getRemark() != null) {
                order.setOrderCheckRemark(request.getRemark());
            }
            repository.save(order);
        }
        return failedOrderReasons;
    }

    @Override
    public void updateOrderCheckRemark(UpdateRemarkRequest request) {
        for (Long orderId : request.getIds() ) {
            Order order = getById(orderId);
            ClientResponseEnum.PARAMETER_ILLEGAL.notNull(order, "订单不存在");
            order.setOrderCheckRemark(request.getRemark());
            repository.save(order);
        }
    }

    @Override
    protected void checkKeyConstraint(Order data) {
        // 验证订单的关键字段，比如唯一性检查
    }

    @Override
    @Transactional
    public Long download(DownloadOrderRequest request) {
        AsyncTask asyncTask = AsyncTask.builder()
                .taskType(TaskType.ORDER_DOWNLOAD)
                .state(TaskState.PENDING)
                .progress(0)
                .parameters(JSONUtil.toJsonStr(request))
                .viewMode(SaSessionUtil.getViewMode())
                .build()
                .fillOwner();
        asyncTask = asyncTaskRepository.saveAndFlush(asyncTask);
        // 提交异步任务执行
        taskExecutorService.submitAsyncTask(asyncTask.getId());
        // 返回任务ID
        return asyncTask.getId();
    }

    @Override
    public Long upload(HttpServletRequest request) {
        log.debug("upload orders");
        // 获取上传的文件
        MultipartFile file = null;  // 从 request 中获取 MultipartFile 文件，假设是表单上传
        if (request instanceof MultipartHttpServletRequest multipartRequest) {
            file = multipartRequest.getFile("file");  // "file" 是表单中文件字段的名字
        }

        if (file == null) {
            return 0L; // 如果没有文件上传，直接返回失败
        }

        try {
            // 获取文件后缀
            String fileName = file.getOriginalFilename();
            String fileExtension = fileName != null ? fileName.substring(fileName.lastIndexOf(".")) : "";

            // 创建临时文件，后缀使用上传文件的后缀
            File tempFile = FileUtil.createTempFile("order", fileExtension, new File("./orders/"), true);

            // 将上传的文件写入临时文件
            file.transferTo(tempFile);
            String templateId = request.getParameter("templateId");
            AsyncTask asyncTask = AsyncTask.builder()
                    .taskType(TaskType.ORDER_UPLOAD)
                    .state(TaskState.PENDING)
                    .progress(0)
                    .parameters(JSONUtil.toJsonStr(new UploadOrderParameters(templateId)))
                    .uploadFilePath(tempFile.toString())
                    .build()
                    .fillOwner();
            asyncTask = asyncTaskRepository.saveAndFlush(asyncTask);
            // 提交异步任务执行
            taskExecutorService.submitAsyncTask(asyncTask.getId());
            // 文件上传成功，返回 true
            return asyncTask.getId();
        } catch (IOException e) {
            e.printStackTrace();
            return 0L;  // 如果发生错误，返回上传失败
        }
    }

    private record UploadOrderParameters(String templateId) {
    }

    @Override
    public Optional<Order> findByOriginOrderId(String orderId) {
        return repository.findByOriginOrderId(orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderDownloadDto> findPaginatedForDownload(QueryPageRequest<Order> request, SystemUserDto owner, ViewMode viewMode) {
        return findPaginated(request, owner, viewMode).map(OrderDownloadDto::convert);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Order> findPaginated(QueryPageRequest<Order> request, SystemUserDto systemUser, ViewMode viewMode) {
        OrQueryAttribute<Order> or = request.or();
        or.add(getOrderAccessDataRangeQueryAttribute(systemUser, viewMode));
        addIgnoreAccessDataRageCondition(or);
        or.next();
        return findOriginalPaginated(request);
    }

    @Override
    @Transactional
    public void applyCheckInfoAndSave(String orderId, OrderCheckInfoDto checkInfo, SystemUserDto owner) {
        Optional<Order> orderOption = Optional.empty();
        if (ConvertUtils.isLong(orderId)) {
            orderOption = repository.findById(ConvertUtils.parseLong(orderId));
        }
        if (orderOption.isEmpty()) {
            orderOption = repository.findByOriginOrderId(orderId);
        }
        if (orderOption.isEmpty()) {
            throw ClientResponseEnum.PARAMETER_ILLEGAL.newException("订单不存在");
        }
        Order order = orderOption.get();
        if (!owner.isSuperAdmin() && !Objects.equals(order.getCompanyId(), owner.getCompanyId())) {
            ClientResponseEnum.NO_PERMISSION.throwException("权限不足");
        }
        checkInfo.fillChangeOrder(order);
        this.saveAndFlush(order);
    }

    @Override
    @Transactional
    public void updateContactStatus(UpdateContactStatusRequest request) {
        for (Long orderId : request.getIds()) {
            Optional<Order> orderOptional = findById(orderId);
            if (orderOptional.isEmpty()) {
                continue;
            }
            Order order = orderOptional.get();
            order.setContacted(request.getContacted());
            repository.save(order);
        }
    }

    @Override
    public void updateContactRemark(UpdateRemarkRequest request) {
        for (Long orderId : request.getIds()) {
            Order order = getById(orderId);
            ClientResponseEnum.PARAMETER_ILLEGAL.notNull(order, "订单不存在");
            order.setContactRemark(request.getRemark());
            repository.save(order);
        }
    }

    @Override
    @Transactional
    public Order saveAndMarkReviewed(Order order, Long temporaryOrderId) {
        Order savedOrder = this.saveAndFlush(order);
        temporaryOrderRepository.markAsReviewed(temporaryOrderId);
        return savedOrder;
    }

    @Override
    public QueryAttribute getAccessDataRangeQueryAttribute() {
        return getOrderAccessDataRangeQueryAttribute(SaSessionUtil.getLoginUser(), SaSessionUtil.getViewMode());
    }

    private QueryAttribute getOrderAccessDataRangeQueryAttribute(SystemUserDto systemUser, ViewMode viewMode) {
        if (Boolean.TRUE.equals(systemUser.getIsCrossDepartment())) {
            return new OrderAccessDataRangeAttribute(AccessDataRangeLevel.SPECIFIED_DEPARTMENTS,
                    systemUser.getManageDepartmentIds(), Boolean.TRUE.equals(systemUser.getIsExcludeDepartment()),
                    systemUser, viewMode);
        }
        return new OrderAccessDataRangeAttribute(AccessDataRangeLevel.PERSON, List.of(), false, systemUser, viewMode);
    }
}
