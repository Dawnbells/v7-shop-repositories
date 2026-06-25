package cn.v7soft.admin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.v7soft.admin.controller.req.OrderStatisticsQueryRequest;
import cn.v7soft.admin.controller.req.SaveOrderStatisticsConfigRequest;
import cn.v7soft.admin.controller.resp.OrderStatisticsConfigResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsContextResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsCurrencyOptionResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsOptionResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsResultResponse;
import cn.v7soft.admin.service.IOrderStatisticsConfigService;
import cn.v7soft.admin.service.IOrderStatisticsService;
import cn.v7soft.admin.service.impl.OrderStatisticsOptionService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/order-statistics")
@Tag(name = "订单统计分析")
public class OrderStatisticsController {
    private final IOrderStatisticsConfigService configService;
    private final IOrderStatisticsService statisticsService;
    private final OrderStatisticsOptionService optionService;

    public OrderStatisticsController(
            IOrderStatisticsConfigService configService,
            IOrderStatisticsService statisticsService,
            OrderStatisticsOptionService optionService
    ) {
        this.configService = configService;
        this.statisticsService = statisticsService;
        this.optionService = optionService;
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

    @SaCheckLogin
    @GetMapping("/options/context")
    @Operation(summary = "获取订单统计页面上下文")
    public OrderStatisticsContextResponse context() {
        return optionService.context();
    }

    @SaCheckLogin
    @GetMapping("/options/currencies")
    @Operation(summary = "获取订单统计目标币种")
    public List<OrderStatisticsCurrencyOptionResponse> currencies() {
        return optionService.currencies();
    }

    @SaCheckLogin
    @GetMapping("/options/employees")
    @Operation(summary = "搜索订单统计员工候选")
    public List<OrderStatisticsOptionResponse> employees(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "includeHistorical", defaultValue = "false")
            boolean includeHistorical
    ) {
        return optionService.employees(keyword, includeHistorical);
    }

    @SaCheckLogin
    @GetMapping("/options/departments")
    @Operation(summary = "搜索订单统计部门候选")
    public List<OrderStatisticsOptionResponse> departments(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "includeHistorical", defaultValue = "false")
            boolean includeHistorical
    ) {
        return optionService.departments(keyword, includeHistorical);
    }

    @SaCheckLogin
    @GetMapping("/options/domains")
    @Operation(summary = "搜索订单统计历史域名候选")
    public List<String> domains(
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        return optionService.domains(keyword);
    }
}
