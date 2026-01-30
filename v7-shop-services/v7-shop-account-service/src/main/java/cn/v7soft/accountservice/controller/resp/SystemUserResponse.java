package cn.v7soft.accountservice.controller.resp;

import cn.v7soft.common.controller.resp.CurrencyResponse;
import cn.v7soft.common.controller.resp.WebsiteResponse;
import cn.v7soft.dao.enums.Gender;
import cn.v7soft.dao.enums.SystemUserType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemUserResponse {

    @Schema(title = "用户ID", example = "1")
    private Long id;

    @Schema(title = "姓名", example = "张三")
    private String username;

    @Schema(title = "性别", example = "男")
    private Gender gender;

    @Schema(title = "电话号码", example = "13812345678")
    private String telephone;

    @Schema(title = "用户类型", example = "EMPLOYEE")
    private SystemUserType userType;

    @Schema(title = "公司名称", example = "维启商城")
    private String displayName;

    @Schema(title = "是否是网站管理后台", example = "true")
    private boolean isWebsiteManager;

    @Schema(title = "当前网站使用货币", example = "{}")
    private CurrencyResponse currency;

    @Schema(title = "当前网站信息，只有当是网站管理后台才返回值", example = "{}")
    private WebsiteResponse website;

    @Schema(title = "图片基础地址", example = "https://image.example.com")
    private String imageBaseUrl;
}
