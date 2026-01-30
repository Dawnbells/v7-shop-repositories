package cn.v7soft.accountservice.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONUtil;
import cn.v7soft.accountservice.controller.req.LoginRequest;
import cn.v7soft.accountservice.controller.req.SystemUserAddRequest;
import cn.v7soft.accountservice.controller.resp.LoginResponse;
import cn.v7soft.accountservice.controller.resp.SystemUserResponse;
import cn.v7soft.accountservice.controller.resp.TicketResponse;
import cn.v7soft.accountservice.service.ISystemUserService;
import cn.v7soft.common.service.IWebsiteContextService;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.enums.ViewMode;
import cn.v7soft.dao.tenant.TenantContext;
import cn.v7soft.dao.tenant.WebsiteContext;
import cn.v7soft.dao.utils.SaSessionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/systemUser")
@Tag(name = "账户中心/系统用户")
@Validated
@AllArgsConstructor
public class SystemUserController {

    private final ISystemUserService systemUserService;
    private final IWebsiteContextService websiteContextService;

    @PostMapping("/login")
    @Operation(summary = "系统用户登录")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        String tokenValue = this.systemUserService.login(request);
        return LoginResponse.builder().token(tokenValue).build();
    }

    @PostMapping("/add")
    @Operation(summary = "添加系统用户")
    public SystemUserResponse add(@Valid @NotNull @RequestBody SystemUserAddRequest request) {
        SystemUser systemUser = this.systemUserService.addUser(request);
        return SystemUserResponse.builder()
                .id(systemUser.getId())
                .username(systemUser.getName())
                .gender(systemUser.getGender())
                .telephone(systemUser.getTelephone())
                .userType(systemUser.getUserType())
                .build();
    }

    @SaCheckLogin
    @GetMapping("/userInfo")
    @Operation(summary = "获取系统用户信息")
    public SystemUserResponse getUserInfo() {
        SystemUserDto systemUser = SaSessionUtil.getLoginUser();
        SystemUserResponse.SystemUserResponseBuilder systemUserResponseBuilder = SystemUserResponse.builder()
                .id(Long.valueOf(systemUser.getId()))
                .username(systemUser.getName())
                .gender(systemUser.getGender())
                .telephone(systemUser.getTelephone())
                .userType(systemUser.getUserType())
                .displayName(TenantContext.getCurrentTenantEntity().getNick())
                .imageBaseUrl(TenantContext.getCurrentTenantEntity().getImageBaseUrl())
                .isWebsiteManager(false)
                .currency(null);
        if (WebsiteContext.getCurrentWebsite() != null && WebsiteContext.isWebsiteAdmin()) {
            systemUserResponseBuilder
                    .website(websiteContextService.getCurrentWebsite())
                    .displayName(websiteContextService.getCurrentWebsiteName())
                    .isWebsiteManager(true)
                    .currency(websiteContextService.getCurrentWebsiteCurrency());
        }
        log.debug("userInfo: {}", JSONUtil.toJsonStr(systemUserResponseBuilder.build()));
        StpUtil.renewTimeout(2592000);
        return systemUserResponseBuilder.build();
    }

    @GetMapping("/logout")
    @Operation(summary = "退出登录")
    public void logout() {
        if (StpUtil.isLogin()) {
            StpUtil.logout();
        }
    }

    @SaCheckLogin
    @GetMapping("/refreshToken")
    @Operation(summary = "刷新token")
    public LoginResponse refreshToken() {
        // 返回新的 Token
        return LoginResponse.builder().token(StpUtil.getTokenValue()).build();
    }

    @SaCheckLogin
    @GetMapping("/getTicket")
    @Operation(summary = "获取一次性ticket")
    public TicketResponse getTicket() {
        return TicketResponse.builder().ticket(systemUserService.getTicket()).build();
    }

    @GetMapping("/loginByTicket/{ticket}")
    @Operation(summary = "根据ticket进行登录,返回tokenValue")
    public LoginResponse loginByTicket(@PathVariable("ticket") String ticket) {
        return LoginResponse.builder().token(systemUserService.loginByTicket(ticket)).build();
    }

    @PostMapping("/switchViewMode/{viewMode}")
    @Operation(summary = "切换数据视角，personal-个人，team-团队")
    public void switchViewMode(@PathVariable("viewMode") ViewMode viewMode) {
        SaSessionUtil.setViewMode(viewMode);
        log.debug("switchViewMode >> {} >> {}", SaSessionUtil.getLoginUser().getName(), SaSessionUtil.getViewMode() );
    }

    @PostMapping("/getViewMode")
    @Operation(summary = "获取数据视角，personal-个人，team-团队")
    public ViewMode getViewMode() {
        return SaSessionUtil.getViewMode();
    }
}
