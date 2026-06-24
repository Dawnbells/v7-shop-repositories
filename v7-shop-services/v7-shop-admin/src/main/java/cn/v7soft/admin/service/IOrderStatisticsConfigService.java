package cn.v7soft.admin.service;

import cn.v7soft.admin.controller.req.SaveOrderStatisticsConfigRequest;
import cn.v7soft.dao.entities.primary.OrderStatisticsUserConfig;

public interface IOrderStatisticsConfigService {
    OrderStatisticsUserConfig getOrCreate(String browserTimeZoneId);

    OrderStatisticsUserConfig save(SaveOrderStatisticsConfigRequest request);
}
