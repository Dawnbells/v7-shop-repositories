package cn.v7soft.admin.controller.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class GetCertificateResp {
    @Schema(title = "证书链")
    private String fullChain;
    @Schema(title = "私钥")
    private String privateKey;
    @Schema(title = "过期时间")
    private LocalDateTime expiredDateTime;
}
