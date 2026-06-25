package cn.v7soft.admin.controller.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(title = "订单统计员工或部门候选")
public class OrderStatisticsOptionResponse {
    private String id;
    private String name;
    private String parentId;
    private String departmentId;
    private String departmentName;
    private String telephone;
    private String status;
    private boolean historical;
    private boolean disabled;
}
