package cn.v7soft.admin.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@Schema(description = "上下文信息请求实体类")
public class TemporaryOrderContextInfoRequest {

    @Schema(description = "销售员 ID")
    private Long salesUid;

    @Schema(description = "销售员姓名")
    private String salesPerson;

    @Schema(description = "部门 ID")
    private Long departmentId;

    @Schema(description = "部门名称")
    private String department;

    @Schema(description = "网站 ID")
    private Long websiteId;

    @Schema(description = "网站名称")
    private String websiteName;

    @Schema(description = "网站 URL")
    private String websiteUrl;

    @Schema(description = "语言 ID")
    private String languageId;

    @Schema(description = "语言")
    private String language;

    @Schema(description = "语言代码")
    private String languageCode;

    @Schema(description = "货币 ID")
    private Long currencyId;

    @Schema(description = "货币名称")
    private String currencyName;

    @Schema(description = "货币符号")
    private String currencySymbol;

    @Schema(description = "货币代码")
    private String currencyCode;

    @Schema(description = "货币汇率")
    private BigDecimal currencyExchangeRate;

    @Schema(description = "货币的小数位数")
    private int currencyFractionDigits;

    @Schema(description = "国家 ID")
    private Long countryId;

    @Schema(description = "国家")
    private String country;

    @Schema(description = "国家代码")
    private String countryCode;

    @Schema(description = "电话号码规则")
    private String phoneRule;

    @Schema(description = "电话号码前缀")
    private String phonePrefix;

    @Schema(description = "地址规则")
    private String addressRule;
}
