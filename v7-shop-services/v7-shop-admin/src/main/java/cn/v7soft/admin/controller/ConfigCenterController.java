package cn.v7soft.admin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.hutool.json.JSONObject;
import cn.v7soft.admin.controller.req.SaveDynamicConfigRequest;
import cn.v7soft.admin.controller.resp.DepartmentResponse;
import cn.v7soft.admin.service.IDepartmentService;
import cn.v7soft.admin.service.IDynamicConfigService;
import cn.v7soft.admin.utils.ConfigCenterLoader;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.tenant.TenantContext;
import cn.v7soft.dao.utils.SaSessionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/config-center")
@Tag(name = "配置中心")
@RequiredArgsConstructor
public class ConfigCenterController {

    private final IDepartmentService departmentService;
    private final IDynamicConfigService dynamicConfigService;

    @SaCheckLogin
    @GetMapping("/{configName}")
    @Operation(summary = "获取配置模板")
    public JSONObject getConfig(@PathVariable String configName) {
        JSONObject config = ConfigCenterLoader.getConfig(configName);
        if (config == null) {
            throw new RuntimeException("配置不存在: " + configName);
        }
        return config;
    }

    @SaCheckLogin
    @GetMapping("/{configName}/value")
    @Operation(summary = "获取已保存的配置值")
    public JSONObject getConfigValue(@PathVariable String configName,
                                     @RequestParam(required = false) Long departmentId) {
        Long companyId = TenantContext.getCurrentTenant();
        return dynamicConfigService.getConfigValue(configName, departmentId, companyId);
    }

    @SaCheckLogin
    @PostMapping("/save")
    @Operation(summary = "保存配置")
    public void saveConfig(@Valid @RequestBody SaveDynamicConfigRequest request) {
        dynamicConfigService.saveConfig(request.getConfigName(), request.getDepartmentId(), request.getConfigValue());
    }

    @SaCheckLogin
    @PostMapping("/{configName}/refresh")
    @Operation(summary = "刷新配置模板")
    public void refreshConfig(@PathVariable String configName) {
        ConfigCenterLoader.refreshConfig(configName);
    }

    @SaCheckLogin
    @PostMapping("/refresh-all")
    @Operation(summary = "刷新所有配置模板")
    public void refreshAllConfigs() {
        ConfigCenterLoader.refreshAllConfigs();
    }

    @SaCheckLogin
    @GetMapping("/departmentInfo")
    @Operation(summary = "获取部门信息")
    public DepartmentResponse getDepartmentInfo() {
        SystemUserDto loginUser = SaSessionUtil.getLoginUser();
        if (loginUser.isAdmin()) {
            return null;
        }
        return DepartmentResponse.convertEntity(departmentService.getById(SaSessionUtil.getLoginUser().getDepartmentId()));
    }

}

