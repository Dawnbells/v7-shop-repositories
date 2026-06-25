package cn.v7soft.admin.controller.resp;

import cn.v7soft.dao.enums.OrderStatisticsDimension;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(title = "订单统计页面上下文")
public class OrderStatisticsContextResponse {
    private String requesterUserId;
    private String requesterName;
    private List<OrderStatisticsDimension> dimensions;
    private boolean employeeLocked;
    private boolean allowUnassigned;
    private boolean websiteScoped;
    private String websiteId;
    private List<WebsiteTypeEnum> platforms;
    private int dayRangeMaxMonths;
    private int monthRangeMaxYears;
}
