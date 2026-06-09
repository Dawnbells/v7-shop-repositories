package cn.v7soft.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.v7soft.admin.controller.req.SaveOrderSearchPresetRequest;
import cn.v7soft.admin.service.IOrderSearchPresetService;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.core.service.impl.BaseService;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.OrderSearchPreset;
import cn.v7soft.dao.enums.OrderSearchPresetPageType;
import cn.v7soft.dao.repositories.primary.OrderSearchPresetRepository;
import cn.v7soft.dao.utils.SaSessionUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderSearchPresetService
        extends BaseService<OrderSearchPreset, OrderSearchPresetRepository>
        implements IOrderSearchPresetService {

    public OrderSearchPresetService(OrderSearchPresetRepository repository) {
        super(repository);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderSearchPreset> listCurrentUserPresets(OrderSearchPresetPageType pageType) {
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(pageType, "页面类型不能为空");
        return repository.findValidByOwnerAndPageTypeOrderByUsage(currentUserId(), pageType);
    }

    @Override
    @Transactional
    public OrderSearchPreset savePreset(SaveOrderSearchPresetRequest request) {
        validateSaveRequest(request);
        Long ownerId = currentUserId();
        String name = request.getName().trim();
        OrderSearchPreset preset = repository
                .findValidByOwnerAndPageTypeAndName(ownerId, request.getPageType(), name)
                .orElseGet(() -> OrderSearchPreset.builder()
                        .owner(currentUser().toOwner())
                        .pageType(request.getPageType())
                        .build());

        preset.setName(name);
        preset.setTimeMode(request.getTimeMode());
        preset.setQueryParams(request.getQueryParams() == null ? new JSONObject() : request.getQueryParams());
        return repository.save(preset);
    }

    @Override
    @Transactional
    public OrderSearchPreset usePreset(Long id) {
        OrderSearchPreset preset = findCurrentUserPreset(id);
        preset.setLastUsedTime(LocalDateTime.now());
        return repository.save(preset);
    }

    @Override
    @Transactional
    public void deletePreset(Long id) {
        OrderSearchPreset preset = findCurrentUserPreset(id);
        preset.setStatus(StatusEnum.DELETED);
        repository.save(preset);
    }

    private OrderSearchPreset findCurrentUserPreset(Long id) {
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(id, "ID不能为空");
        return repository.findValidByIdAndOwnerId(id, currentUserId())
                .orElseThrow(() -> ClientResponseEnum.PARAMETER_ILLEGAL.newException("条件预设不存在或无权访问"));
    }

    private void validateSaveRequest(SaveOrderSearchPresetRequest request) {
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(request, "请求不能为空");
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(request.getPageType(), "页面类型不能为空");
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(request.getTimeMode(), "时间保存方式不能为空");
        ClientResponseEnum.PARAMETER_ILLEGAL.notBlank(request.getName(), "条件名称不能为空");
        ClientResponseEnum.PARAMETER_ILLEGAL.isTrue(
                StrUtil.length(request.getName().trim()) <= 50,
                "条件名称不能超过50个字符"
        );
    }

    private SystemUserDto currentUser() {
        return SaSessionUtil.getLoginUser();
    }

    private Long currentUserId() {
        return currentUser().getLongId();
    }
}
