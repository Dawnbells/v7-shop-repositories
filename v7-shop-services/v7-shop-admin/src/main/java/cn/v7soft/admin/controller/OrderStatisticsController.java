package cn.v7soft.admin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.v7soft.admin.controller.req.OrderStatisticsQueryRequest;
import cn.v7soft.admin.controller.req.SaveOrderStatisticsConfigRequest;
import cn.v7soft.admin.controller.resp.OrderStatisticsConfigResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsResultResponse;
import cn.v7soft.admin.service.IOrderStatisticsConfigService;
import cn.v7soft.admin.service.IOrderStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/order-statistics")
@Tag(name = "订单统计分析")
public class OrderStatisticsController {
    private final IOrderStatisticsConfigService configService;
    private final IOrderStatisticsService statisticsService;

    public OrderStatisticsController(
            IOrderStatisticsConfigService configService,
            IOrderStatisticsService statisticsService
    ) {
        this.configService = configService;
        this.statisticsService = statisticsService;
    }

    @SaCheckLogin
    @GetMapping("/config")
    @Operation(summary = "获取当前用户订单统计配置")
    public OrderStatisticsConfigResponse getConfig(
            @RequestHeader(value = "X-Browser-Time-Zone", required = false) String browserTimeZoneId
    ) {
        return OrderStatisticsConfigResponse.convert(configService.getOrCreate(browserTimeZoneId));
    }

    @SaCheckLogin
    @PutMapping("/config")
    @Operation(summary = "保存当前用户订单统计配置")
    public OrderStatisticsConfigResponse saveConfig(
            @Valid @RequestBody SaveOrderStatisticsConfigRequest request
    ) {
        return OrderStatisticsConfigResponse.convert(configService.save(request));
    }

    @SaCheckLogin
    @PostMapping("/query")
    @Operation(summary = "查询订单统计")
    public OrderStatisticsResultResponse query(
            @RequestBody OrderStatisticsQueryRequest request
    ) {
        return statisticsService.query(request);
    }
}
