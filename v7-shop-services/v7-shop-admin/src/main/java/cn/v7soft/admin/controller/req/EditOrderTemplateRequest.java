package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import cn.v7soft.dao.entities.primary.OrderTemplateColumn;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class EditOrderTemplateRequest extends IdRequest {

    @NotBlank(message = "模版名称不能为空")
    @Schema(title = "模版名称", example = "海外订单模版")
    private String templateName;

    @Schema(title = "是否是下载模板", example = "true/false")
    private boolean downloadTemplate;

    @Schema(title = "表头配置")
    private List<EditOrderTemplateColumnRequest> columns;
}
