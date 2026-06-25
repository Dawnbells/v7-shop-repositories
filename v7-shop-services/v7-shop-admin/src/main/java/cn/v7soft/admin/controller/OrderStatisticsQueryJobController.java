package cn.v7soft.admin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.v7soft.admin.controller.resp.OrderStatisticsQueryJobResponse;
import cn.v7soft.admin.service.impl.OrderStatisticsQueryJobService;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.utils.SaSessionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/order-statistics")
@Tag(name = "订单统计查询任务")
public class OrderStatisticsQueryJobController {

    private final OrderStatisticsQueryJobService jobService;

    public OrderStatisticsQueryJobController(
            OrderStatisticsQueryJobService jobService
    ) {
        this.jobService = jobService;
    }

    @SaCheckLogin
    @GetMapping("/query-jobs/{queryJobId}")
    @Operation(summary = "查询订单统计任务状态")
    public OrderStatisticsQueryJobResponse status(
            @PathVariable String queryJobId
    ) {
        SystemUserDto user = SaSessionUtil.getLoginUser();
        return OrderStatisticsQueryJobResponse.convert(jobService.status(
                user.getCompanyId(),
                user.getLongId(),
                queryJobId
        ));
    }

    @SaCheckLogin
    @PostMapping("/query-jobs/{queryJobId}/cancel")
    @Operation(summary = "取消订单统计查询任务")
    public OrderStatisticsQueryJobResponse cancel(
            @PathVariable String queryJobId
    ) {
        SystemUserDto user = SaSessionUtil.getLoginUser();
        return OrderStatisticsQueryJobResponse.convert(jobService.cancel(
                user.getCompanyId(),
                user.getLongId(),
                queryJobId
        ));
    }
}
