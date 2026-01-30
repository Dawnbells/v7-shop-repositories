package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import cn.v7soft.dao.enums.CloakStrategy;
import cn.v7soft.dao.enums.DomainType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class EditTopLevelDomainRequest extends IdRequest {
    @NotBlank(message = "域名名称不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9-]+(\\.[a-zA-Z]{2,})$", message = "请输入正确的顶级域名")
    @Schema(title = "顶级域名", example = "example.com")
    private String name;

    @Schema(title = "备注", example = "xxx")
    private String remark;

    @Schema(title = "域名类型", example = "xxx")
    private DomainType type;

    @Schema(title = "斗篷策略", example = "GOOGLE")
    private CloakStrategy cloakStrategy;

    @Schema(title = "域名到期时间", example = "2025-12-31T23:59:59")
    private LocalDateTime expiryDate;

    @Schema(title = "云平台账户ID", example = "1")
    private Long cloudPlatformAccountId;
}
