package cn.v7soft.admin.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.enums.SystemUserType;
import cn.v7soft.dao.properties.CloakCenterProperty;
import cn.v7soft.dao.tenant.TenantContext;
import cn.v7soft.dao.utils.SaSessionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/cloak-risk-control")
@Tag(name = "斗篷风控管理")
@RequiredArgsConstructor
public class CloakRiskControlController {
    private final CloakCenterProperty cloakCenterProperty;

    @SaCheckLogin
    @GetMapping("/temporary-url")
    @Operation(summary = "获取临时访问URL")
    public String getTemporaryUrl() {
        SystemUserDto currentUser = SaSessionUtil.getLoginUser();
        String accessKey = TenantContext.getCurrentTenantEntity().getAccessKey();
        String baseUrl = cloakCenterProperty.getBaseUrl();
        final String cloakTokenUrl = baseUrl + "/cloak/simple/token";
        final String cloakStatsUrl = baseUrl + "/cloak/simple/stats";
        // 构建请求参数
        Map<String, Object> params = new HashMap<>();

        SystemUserType userType = currentUser.getUserType();
        if (userType == SystemUserType.ADMIN || userType == SystemUserType.COMPANY_ADMIN) {
            // 公司管理员: 只传递accessKey
            params.put("accessKeys", accessKey);
        } else if (userType == SystemUserType.DEPARTMENT_MANAGER
                   || userType == SystemUserType.DEEP_DEPARTMENT_MANAGER
                   || userType == SystemUserType.DEPARTMENT_TREE) {
            // 部门管理员: 传管理的deptIds、accessKey
            params.put("accessKeys", accessKey);
            if (currentUser.getAccessDepartmentIds() != null && !currentUser.getAccessDepartmentIds().isEmpty()) {
                String deptIds = currentUser.getAccessDepartmentIds().stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(","));
                params.put("deptIds", deptIds);
            }
        } else {
            // 员工: 只传递userIds, accessKey
            params.put("accessKeys", accessKey);
            params.put("userIds", currentUser.getId());
        }

        // 请求token (使用表单参数)
        String response = HttpUtil.post(cloakTokenUrl, params);
        JSONObject result = JSONUtil.parseObj(response);

        if (!result.getBool("success", false)) {
            log.error("获取cloak token失败: {}", response);
            throw new RuntimeException("获取临时访问URL失败");
        }

        String token = result.getStr("token");
        return cloakStatsUrl + "?token=" + token;
    }
}

