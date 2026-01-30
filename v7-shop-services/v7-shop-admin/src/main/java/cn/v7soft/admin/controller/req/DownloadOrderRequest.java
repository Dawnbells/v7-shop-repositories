package cn.v7soft.admin.controller.req;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class DownloadOrderRequest extends QueryOrderRequest {
    private Boolean isDownload;
    private Boolean isAudit;

    @NotBlank(message = "请指定下载模板")
    private String templateId;
}
