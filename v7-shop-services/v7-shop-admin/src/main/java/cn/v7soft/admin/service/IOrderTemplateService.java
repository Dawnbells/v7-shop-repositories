package cn.v7soft.admin.service;

import java.util.List;
import java.util.Map;

import cn.v7soft.common.service.IBaseDataRangeService;
import cn.v7soft.dao.entities.primary.OrderTemplate;

public interface IOrderTemplateService extends IBaseDataRangeService<OrderTemplate> {
    List<OrderTemplate> query(String type, String keyword);

    Map<String, String> getHeaderAliasMap(String templateId, Boolean isAudit);
}
