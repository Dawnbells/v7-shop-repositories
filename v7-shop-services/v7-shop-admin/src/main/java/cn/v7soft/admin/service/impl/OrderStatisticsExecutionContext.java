package cn.v7soft.admin.service.impl;

import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.OrderStatisticsUserConfig;
import cn.v7soft.dao.enums.ViewMode;

public record OrderStatisticsExecutionContext(
        SystemUserDto user,
        ViewMode viewMode,
        boolean websiteScoped,
        Long websiteId,
        OrderStatisticsUserConfig config
) {
}
