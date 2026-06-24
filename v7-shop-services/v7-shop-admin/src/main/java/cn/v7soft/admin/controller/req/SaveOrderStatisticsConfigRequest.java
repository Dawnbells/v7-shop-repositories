package cn.v7soft.admin.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Schema(title = "保存订单统计个人配置")
public class SaveOrderStatisticsConfigRequest {

    @NotBlank(message = "默认目标币种不能为空")
    private String defaultTargetCurrencyCode;

    @NotBlank(message = "时区不能为空")
    private String timeZoneId;

    @NotNull(message = "个人汇率不能为空")
    @Builder.Default
    private Map<String, String> exchangeRates = new LinkedHashMap<>();
}
