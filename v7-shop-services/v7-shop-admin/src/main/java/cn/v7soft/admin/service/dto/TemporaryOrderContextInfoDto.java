package cn.v7soft.admin.service.dto;

import java.math.BigDecimal;

import cn.hutool.core.bean.BeanUtil;
import cn.v7soft.dao.dto.IdDto;
import cn.v7soft.dao.entities.primary.OrderContextInfo;
import cn.v7soft.dao.entities.primary.TemporaryOrderContextInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class TemporaryOrderContextInfoDto extends IdDto {

    /**
     * 销售UID
     */
    private Long salesUid;

    /**
     * 销售名字
     */
    private String salesPerson;

    /**
     * 销售部门ID
     */
    private Long departmentId;

    /**
     * 销售部门
     */
    private String department;

    /**
     * 网站ID
     */
    private Long websiteId;


    /**
     * 网站名称
     */
    private String websiteName;

    /**
     * 网站域名
     */
    private String websiteUrl;

    /**
     * 语言ID
     */
    private String languageId;

    /**
     * 语言名称
     */
    private String language;

    /**
     * 语言代码
     */
    private String languageCode;

    /**
     * 货币ID
     */
    private Long currencyId;

    /**
     * 货币名称
     */
    private String currencyName;

    /**
     * 货币符号
     */
    private String currencySymbol;

    /**
     * 货币代码（ISO-4217）
     */
    private String currencyCode;

    /**
     * 美元兑换汇率
     */
    private BigDecimal currencyExchangeRate;

    /**
     * 有效小数位
     */
    private int currencyFractionDigits;

    /**
     * 国家ID
     */
    private Long countryId;

    /**
     * 国家名称
     */
    private String country;

    /**
     * 国家代码
     */
    private String countryCode;

    /**
     * 电话号码正则
     */
    private String phoneRule;
    /**
     * 电话前缀，国际区号：+xx??
     */
    private String phonePrefix;
    /**
     * 地址规则
     */
    private String addressRule;
    public static TemporaryOrderContextInfoDto convert(TemporaryOrderContextInfo contextInfo) {
        TemporaryOrderContextInfoDto contextInfoDto = new TemporaryOrderContextInfoDto();
        BeanUtil.copyProperties(contextInfo, contextInfoDto);
        return contextInfoDto;
    }

    public OrderContextInfo toOrderContextInfo() {
        OrderContextInfo orderContextInfo = new OrderContextInfo();
        BeanUtil.copyProperties(this, orderContextInfo);
        orderContextInfo.setId(null);
        return orderContextInfo;
    }
}
