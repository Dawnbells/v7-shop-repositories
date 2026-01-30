package cn.v7soft.admin.controller.resp;

import io.swagger.v3.oas.annotations.media.Schema;

public class OwnerResponse {
    @Schema(title = "归属人")
    private String username;

    @Schema(title = "归属部门")
    private String departmentName;
}
