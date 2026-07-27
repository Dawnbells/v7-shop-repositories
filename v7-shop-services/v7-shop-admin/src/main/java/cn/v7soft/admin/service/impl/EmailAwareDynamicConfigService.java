package cn.v7soft.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.v7soft.admin.service.IDynamicConfigService;
import cn.v7soft.admin.service.email.DepartmentEmailState;
import cn.v7soft.admin.service.email.EmailSmtpMode;
import cn.v7soft.admin.service.email.EmailSmtpSupport;
import cn.v7soft.dao.entities.primary.Department;
import cn.v7soft.dao.entities.primary.DynamicConfig;
import cn.v7soft.dao.repositories.primary.DepartmentRepository;
import cn.v7soft.dao.repositories.primary.DynamicConfigRepository;
import cn.v7soft.dao.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Primary
@Service
@RequiredArgsConstructor
public class EmailAwareDynamicConfigService implements IDynamicConfigService {

    private static final String EMAIL_CONFIG_NAME = "email";

    private final DynamicConfigRepository dynamicConfigRepository;
    private final DepartmentRepository departmentRepository;
    private final EmailSmtpSupport smtpSupport;

    @Override
    @Transactional(readOnly = true)
    public Optional<JSONObject> getConfigWithFallback(String configName, Long departmentId, Long companyId) {
        if (departmentId == null) {
            return findCompanyConfig(configName, companyId);
        }
        Optional<JSONObject> current = findDepartmentConfig(configName, departmentId, companyId);
        if (current.isPresent()) {
            return current;
        }
        Optional<Department> department = departmentRepository.findById(departmentId);
        Department parent = department.map(Department::getParent).orElse(null);
        while (parent != null) {
            Optional<JSONObject> inherited = findDepartmentConfig(configName, parent.getId(), companyId);
            if (inherited.isPresent()) {
                return inherited;
            }
            parent = parent.getParent();
        }
        return findCompanyConfig(configName, companyId);
    }

    @Override
    @Transactional(readOnly = true)
    public JSONObject getConfigValue(String configName, Long departmentId, Long companyId) {
        Optional<DynamicConfig> config = departmentId == null
                ? dynamicConfigRepository.findCompanyConfig(configName, companyId)
                : dynamicConfigRepository.findDepartmentConfig(configName, departmentId, companyId);
        JSONObject value = config.map(DynamicConfig::getConfigValue).orElseGet(JSONObject::new);
        if (EMAIL_CONFIG_NAME.equals(configName) && departmentId != null && config.isPresent()) {
            normalizeDepartmentState(value);
        }
        return value;
    }

    @Override
    @Transactional
    public void saveConfig(String configName, Long departmentId, JSONObject configValue) {
        if (EMAIL_CONFIG_NAME.equals(configName)) {
            if (departmentId == null) {
                validateCompanyEmailConfig(configValue);
            } else {
                normalizeDepartmentState(configValue);
            }
        }

        Long companyId = TenantContext.getCurrentTenant();
        DynamicConfig config = departmentId == null
                ? dynamicConfigRepository.findCompanyConfig(configName, companyId)
                    .orElseGet(() -> DynamicConfig.builder().configName(configName).build())
                : dynamicConfigRepository.findDepartmentConfig(configName, departmentId, companyId)
                    .orElseGet(() -> DynamicConfig.builder()
                            .configName(configName)
                            .departmentId(departmentId)
                            .build());
        config.setConfigValue(configValue);
        dynamicConfigRepository.save(config);
    }

    private void validateCompanyEmailConfig(JSONObject configValue) {
        JSONObject email = emailNode(configValue);
        EmailSmtpMode mode = EmailSmtpMode.from(email.getStr("smtp-mode"));
        email.set("smtp-mode", mode.name());
        if (mode != EmailSmtpMode.COMPANY_UNIFIED) {
            return;
        }
        if (!email.getBool("open", false)) {
            throw new IllegalArgumentException("开启公司统一 SMTP 前必须开启公司邮件通知");
        }
        String expectedSignature = smtpSupport.signature(email);
        if (!expectedSignature.equals(email.getStr("smtp-test-signature"))) {
            throw new IllegalArgumentException("公司 SMTP 配置尚未通过测试，或测试后配置已发生变化");
        }
    }

    private void normalizeDepartmentState(JSONObject configValue) {
        JSONObject email = emailNode(configValue);
        String state = email.getStr("state");
        if (StrUtil.isBlank(state)) {
            state = DepartmentEmailState.from(email).name();
        } else {
            state = DepartmentEmailState.from(new JSONObject().set("state", state)).name();
        }
        email.set("state", state);
        email.set("open", DepartmentEmailState.ENABLED.name().equals(state));
    }

    private JSONObject emailNode(JSONObject value) {
        JSONObject email = value.getJSONObject("email");
        if (email == null) {
            email = new JSONObject();
            value.set("email", email);
        }
        return email;
    }

    private Optional<JSONObject> findCompanyConfig(String configName, Long companyId) {
        return dynamicConfigRepository.findCompanyConfig(configName, companyId)
                .map(DynamicConfig::getConfigValue);
    }

    private Optional<JSONObject> findDepartmentConfig(String configName, Long departmentId, Long companyId) {
        return dynamicConfigRepository.findDepartmentConfig(configName, departmentId, companyId)
                .map(DynamicConfig::getConfigValue);
    }
}
