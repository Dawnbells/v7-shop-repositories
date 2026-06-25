package cn.v7soft.admin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.v7soft.admin.service.impl.OrderStatisticsExportSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/order-statistics")
@Tag(name = "订单统计导出")
public class OrderStatisticsExportController {

    private final OrderStatisticsExportSubmissionService exportService;

    public OrderStatisticsExportController(
            OrderStatisticsExportSubmissionService exportService
    ) {
        this.exportService = exportService;
    }

    @SaCheckLogin
    @PostMapping("/results/{resultToken}/export")
    @Operation(summary = "导出订单统计聚合结果")
    public Long export(@PathVariable String resultToken) {
        return exportService.submit(resultToken);
    }
}
