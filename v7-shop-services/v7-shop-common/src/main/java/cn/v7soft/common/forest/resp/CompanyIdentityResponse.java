package cn.v7soft.common.forest.resp;

import cn.v7soft.dao.enums.IndustryType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyIdentityResponse {
    @Schema(title = "公司ID", example = "公司ID")
    private Long id;

    @Schema(title = "公司名称", example = "示例公司")
    private String name;

    @Schema(title = "公司昵称", example = "示例昵称")
    private String nick;

    @Schema(title = "公司Logo", example = "https://example.com/logo.png")
    private String logo;

    @Schema(title = "联系人", example = "张三")
    private String contacts;

    @Schema(title = "联系电话", example = "12345678901")
    private String contactsPhone;

    @Schema(title = "公司所在的行业领域", example = "TECHNOLOGY")
    private IndustryType industry;

    @Schema(title = "公司所在国家", example = "中国")
    private String country;

    @Schema(title = "公司所在省份", example = "浙江")
    private String province;

    @Schema(title = "公司所在城市", example = "杭州")
    private String city;

    @Schema(title = "公司地址", example = "杭州市西湖区某某街道123号")
    private String address;

    @Schema(title = "公司位置的邮政编码", example = "310000")
    private String postalCode;

    @Schema(title = "公司的联系电话", example = "0571-88000000")
    private String phone;

    @Schema(title = "公司的电子邮件地址", example = "contact@example.com")
    private String email;

    @Schema(title = "公司网站的一级域名, 全局唯一", example = "example.com")
    private String domain;

    @Schema(title = "公司创建时间", example = "2024-03-25 13:49:53")
    private LocalDateTime createTime;

    @Schema(title = "公司更新时间", example = "2024-03-25 13:49:53")
    private LocalDateTime updateTime;
}
