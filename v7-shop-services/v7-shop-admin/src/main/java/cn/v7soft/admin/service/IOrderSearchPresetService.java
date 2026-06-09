package cn.v7soft.admin.service;

import cn.v7soft.admin.controller.req.SaveOrderSearchPresetRequest;
import cn.v7soft.core.service.IBaseService;
import cn.v7soft.dao.entities.primary.OrderSearchPreset;
import cn.v7soft.dao.enums.OrderSearchPresetPageType;

import java.util.List;

public interface IOrderSearchPresetService extends IBaseService<OrderSearchPreset> {

    List<OrderSearchPreset> listCurrentUserPresets(OrderSearchPresetPageType pageType);

    OrderSearchPreset savePreset(SaveOrderSearchPresetRequest request);

    OrderSearchPreset usePreset(Long id);

    void deletePreset(Long id);
}
