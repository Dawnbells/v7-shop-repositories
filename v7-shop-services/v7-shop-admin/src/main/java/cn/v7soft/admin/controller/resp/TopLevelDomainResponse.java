package cn.v7soft.admin.controller.resp;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;

import cn.v7soft.common.controller.resp.CloudPlatformAccountResponse;
import cn.v7soft.common.controller.resp.DataRangeResponse;
import cn.v7soft.dao.entities.primary.CloudPlatformAccount;
import cn.v7soft.dao.entities.primary.SSLCertificate;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import cn.v7soft.dao.enums.CertificateRequestStatus;
import cn.v7soft.dao.enums.CloakStrategy;
import cn.v7soft.dao.enums.DomainType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@Schema(description = "一级域名信息响应")
public class TopLevelDomainResponse extends DataRangeResponse {

    @Schema(title = "域名名称", example = "example.com")
    private String name;
    @Schema(title = "备注", example = "111")
    private String remark;

    @Schema(title = "域名到期时间", example = "2025-12-31T23:59:59")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDateTime expiryDate;

    @Schema(title = "证书到期时间", example = "2025-12-31T23:59:59")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDateTime sslExpiryDate;

    @Schema(title = "归属人")
    private String username;

    @Schema(title = "归属部门")
    private String departmentName;

    @Schema(title = "域名类型")
    private DomainType type;

    @Schema(title = "斗篷策略")
    private CloakStrategy cloakStrategy;

    @Schema(title = "云平台账户")
    private CloudPlatformAccountResponse cloudPlatformAccount;

    @Schema(title = "当前证书请求状态")
    private CertificateRequestStatus certificateRequestStatus;

    @Schema(title = "SSL证书，嵌入式对象。")
    private SSLCertificate sslCertificate;

    @Schema(title = "绑定的协议名称")
    private String protocolName;

    @Schema(title = "绑定的协议ID")
    private Long protocolId;

    @Schema(title = "占位符值")
    private Map<String, String> placeholderValues;

    @Schema(title = "已绑定像素列表")
    private List<PixelAccountResponse> pixels;

    @Schema(title = "证书申请队列位置（第几位，从1开始；仅排队中域名有值）")
    private Integer queuePosition;

    public static TopLevelDomainResponse convertEntity(TopLevelDomain entity) {
        if (entity == null) {
            return null;
        }
        SSLCertificate sslCertificate = entity.getSslCertificate();
        LocalDateTime sslExpiryDate = sslCertificate == null ? LocalDateTime.MIN : sslCertificate.getCertificateExpiryDate();
        TopLevelDomainResponseBuilder<?, ?> builder = TopLevelDomainResponse.builder()
                .name(entity.getName())
                .type(entity.getType())
                .remark(entity.getRemark())
                .expiryDate(entity.getExpiryDate())
                .sslExpiryDate(sslExpiryDate)
                .certificateRequestStatus(entity.getCertificateRequestStatus())
                .sslCertificate(sslCertificate)
                .cloakStrategy(entity.getCloakStrategy() == null? CloakStrategy.DEFAULT: entity.getCloakStrategy())
                .placeholderValues(entity.getPlaceholderValues() == null? new HashMap<>(): entity.getPlaceholderValues());
        CloudPlatformAccount cloudPlatformAccount = entity.getCloudPlatformAccount();
        if (cloudPlatformAccount != null) {
            builder.cloudPlatformAccount(CloudPlatformAccountResponse.convertEntity(cloudPlatformAccount));
        }
        if (entity.getProtocol() != null) {
            builder.protocolName(entity.getProtocol().getName());
            builder.protocolId(entity.getProtocol().getId());
        }
        if (entity.getPixelAccounts() != null && !entity.getPixelAccounts().isEmpty()) {
            builder.pixels(entity.getPixelAccounts().stream().map(PixelAccountResponse::convertEntity).toList());
        }
        return filling(entity, builder.build());
    }
}
