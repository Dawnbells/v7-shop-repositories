package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import com.dtflys.forest.annotation.Get;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UpdateCertificateReq extends IdRequest {
    @Schema(title = "证书链")
    @NotBlank(message = "证书链不能为空")
    private String fullChain;
    @Schema(title = "私钥")
    @NotBlank(message = "私钥不能为空")
    private String privateKey;
}
