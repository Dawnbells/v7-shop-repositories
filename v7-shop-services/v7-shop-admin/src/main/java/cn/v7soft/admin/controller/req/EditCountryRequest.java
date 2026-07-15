package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import cn.v7soft.core.controller.validator.annotation.ListPattern;
import cn.v7soft.dao.enums.AddressOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 用于编辑国家信息的请求类。
 */
@Getter
@Setter
public class EditCountryRequest extends IdRequest {
    @NotBlank(message = "国家名称不能为空")
    @Schema(title = "国家名称", example = "中国", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(title = "国家代码", example = "CN")
    private String code;

    @Schema(title = "归属大陆", example = "EU")
    private String continentCode;

    @Schema(title = "货币ID", example = "1")
    @Pattern(regexp = "^[0-9]+$", message = "货币ID不正确")
    private String currencyId;

    @Schema(title = "语言ID", example = "1")
    @ListPattern(regexp = "^[0-9]+$", message = "语言ID列表不正确")
    private List<String> languageIds;

    @NotBlank(message = "前端服务器ID")
    @Schema(title = "前端服务器ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private String frontServerId;

    // ========== CountryMeta 字段 ==========

    @Schema(title = "电话前缀", example = "+86")
    private String phonePrefix;

    @Schema(title = "电话验证规则", example = "^1[3-9]\\d{9}$")
    private String phoneRule;

    @Schema(title = "地址验证规则", example = "^.{5,200}$")
    private String addressRule;

    @Schema(title = "地址包含字段", example = "province,city,district,postal_code")
    private String addressFields;

    @Schema(title = "地址拼接顺序", example = "REVERSE")
    private AddressOrder addressOrder;

    @Schema(title = "是否使用全名", example = "true", description = "true-只输入一个名字，false-分开first name和last name")
    private Boolean useFullName;

    @Schema(title = "底部版权信息", example = "© 2024 V7Shop. All rights reserved.")
    private String footerCopyrightInfo;

    @Schema(title = "是否必填电话", example = "true")
    private Boolean requiredPhone;

    @Schema(title = "是否必填邮箱", example = "false")
    private Boolean requiredEmail;
}
