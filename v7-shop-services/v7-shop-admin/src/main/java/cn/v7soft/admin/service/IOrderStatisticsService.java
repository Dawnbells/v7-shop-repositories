package cn.v7soft.admin.service;

import cn.v7soft.admin.controller.req.OrderStatisticsQueryRequest;
import cn.v7soft.admin.controller.resp.OrderStatisticsResultResponse;

public interface IOrderStatisticsService {

    OrderStatisticsResultResponse query(OrderStatisticsQueryRequest request);
}
