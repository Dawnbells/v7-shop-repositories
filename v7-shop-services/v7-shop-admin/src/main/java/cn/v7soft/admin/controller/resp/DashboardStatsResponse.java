package cn.v7soft.admin.controller.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(title = "首页统计数据")
public class DashboardStatsResponse {

    @Schema(title = "今日订单数")
    private long todayOrderCount;

    @Schema(title = "今日销售额")
    private BigDecimal todaySalesAmount;

    @Schema(title = "今日AI消耗积分")
    private int todayAiCreditsUsed;

    @Schema(title = "当前AI冻结积分")
    private int currentAiFrozenCredits;
}
