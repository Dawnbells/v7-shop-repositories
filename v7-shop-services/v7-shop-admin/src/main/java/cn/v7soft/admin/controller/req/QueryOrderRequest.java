package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.BasePageRequest;
import cn.v7soft.dao.enums.CheckStatus;
import cn.v7soft.dao.enums.OrderStatus;
import cn.v7soft.dao.enums.RepeatType;
import cn.v7soft.dao.enums.SearchType;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class QueryOrderRequest extends BasePageRequest {
    @Schema(title = "订单状态", example = "PENDING")
    private List<LocalDateTime> dateRange;

    @Schema(title = "订单状态", example = "PENDING")
    private String status;

    @Schema(title = "国家ID", example = "1")
    private String countryId;

    @Nullable
    @Schema(title = "是否是审单", example = "true", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Boolean isAudit;

    @Schema(title = "重单类型", example = "1", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private WebsiteTypeEnum platform;
    /**
     * 搜索类型
     * 其他值为常规搜索
     */
    @Schema(title = "搜索类型", example = "1")
    private SearchType searchType;

    @Nullable
    @Schema(title = "关键字", example = "1")
    private String keywords;

    @Schema(title = "订单状态", example = "1")
    private OrderStatus orderStatus;

    @Schema(title = "订单类型", example = "1")
    private CheckStatus botOrderStatus;

    @Schema(title = "重单类型", example = "1")
    private RepeatType repeatType;

    @Schema(title = "查询部门", example = "['121212']")
    private List<String> belongDepartmentIds;

    @Schema(title = "订单归属员工ID", example = "1")
    private List<String> belongEmployeeIds;

    @Nullable
    @Schema(title = "是否是建联页面", example = "true", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Boolean isContact;

    @Nullable
    @Schema(title = "建联状态过滤", example = "true", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Boolean contacted;

}
