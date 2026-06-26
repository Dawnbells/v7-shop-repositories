package cn.v7soft.admin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.v7soft.admin.controller.req.OrderStatisticsPageRequest;
import cn.v7soft.admin.controller.resp.OrderStatisticsBucketGroupResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsGroupResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsPageResponse;
import cn.v7soft.admin.controller.req.OrderStatisticsQueryRequest;
import cn.v7soft.admin.controller.req.SaveOrderStatisticsConfigRequest;
import cn.v7soft.admin.controller.resp.OrderStatisticsConfigResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsContextResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsCurrencyOptionResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsOptionResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsQueryResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsResultResponse;
import cn.v7soft.admin.service.IOrderStatisticsConfigService;
import cn.v7soft.admin.service.impl.OrderStatisticsOptionService;
import cn.v7soft.admin.service.impl.OrderStatisticsSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final OrderStatisticsOptionService optionService;
    private final OrderStatisticsSubmissionService submissionService;

    public OrderStatisticsController(
            IOrderStatisticsConfigService configService,
            OrderStatisticsOptionService optionService,
            OrderStatisticsSubmissionService submissionService
    ) {
        this.configService = configService;
        this.optionService = optionService;
        this.submissionService = submissionService;
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
    public ResponseEntity<OrderStatisticsQueryResponse> query(
            @RequestBody OrderStatisticsQueryRequest request
    ) {
        OrderStatisticsQueryResponse response = submissionService.submit(request);
        if ("PROCESSING".equals(response.getState())) {
            return ResponseEntity.accepted().body(response);
        }
        return ResponseEntity.ok(response);
    }

    @SaCheckLogin
    @GetMapping("/results/{resultToken}")
    @Operation(summary = "读取订单统计结果快照")
    public OrderStatisticsResultResponse result(
            @PathVariable String resultToken
    ) {
        return submissionService.result(resultToken);
    }

    @SaCheckLogin
    @PostMapping("/results/{resultToken}/groups/page")
    @Operation(summary = "分页读取订单统计分组汇总快照")
    public OrderStatisticsPageResponse<OrderStatisticsGroupResponse> groupsPage(
            @PathVariable String resultToken,
            @Valid @RequestBody OrderStatisticsPageRequest request
    ) {
        return submissionService.groupsPage(resultToken, request);
    }

    @SaCheckLogin
    @PostMapping("/results/{resultToken}/bucket-groups/page")
    @Operation(summary = "分页读取订单统计时间分组明细快照")
    public OrderStatisticsPageResponse<OrderStatisticsBucketGroupResponse> bucketGroupsPage(
            @PathVariable String resultToken,
            @Valid @RequestBody OrderStatisticsPageRequest request
    ) {
        return submissionService.bucketGroupsPage(resultToken, request);
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
