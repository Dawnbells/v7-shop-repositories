package cn.v7soft.admin.controller.resp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "风险IP")
@JsonIgnoreProperties(ignoreUnknown = true)
public class RiskIpResponse {
    @Schema(title = "IP地址")
    private String ip;
    @Schema(title = "IP归属国家")
    private String country;
    @Schema(title = "IP归属国家代码")
    private String countryCode;
    @Schema(title = "纬度")
    private String latitude;
    @Schema(title = "经度")
    private String longitude;
}
