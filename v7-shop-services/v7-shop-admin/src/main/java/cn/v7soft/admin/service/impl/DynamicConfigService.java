package cn.v7soft.admin.service.impl;

import cn.hutool.json.JSONObject;
import cn.v7soft.admin.service.IDynamicConfigService;
import cn.v7soft.dao.entities.primary.Department;
import cn.v7soft.dao.entities.primary.DynamicConfig;
import cn.v7soft.dao.repositories.primary.DepartmentRepository;
import cn.v7soft.dao.repositories.primary.DynamicConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicConfigService implements IDynamicConfigService {

    private final DynamicConfigRepository dynamicConfigRepository;
    private final DepartmentRepository departmentRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<JSONObject> getConfigWithFallback(String configName, Long departmentId, Long companyId) {
        if (departmentId == null) {
            // 没有部门ID，直接返回公司配置
            return findCompanyConfig(configName, companyId);
        }

        // 先查找当前部门的配置
        Optional<JSONObject> deptConfig = findDepartmentConfig(configName, departmentId, companyId);
        if (deptConfig.isPresent()) {
            return deptConfig;
        }

        // 向上级部门查找
        Optional<Department> departmentOpt = departmentRepository.findById(departmentId);
        if (departmentOpt.isPresent()) {
            Department department = departmentOpt.get();
            Department parent = department.getParent();
            while (parent != null) {
                Optional<JSONObject> parentConfig = findDepartmentConfig(configName, parent.getId(), companyId);
                if (parentConfig.isPresent()) {
                    return parentConfig;
                }
                parent = parent.getParent();
            }
        }

        // 最后查找公司配置
        return findCompanyConfig(configName, companyId);
    }

    @Override
    @Transactional(readOnly = true)
    public JSONObject getConfigValue(String configName, Long departmentId, Long companyId) {
        Optional<DynamicConfig> configOpt;
        if (departmentId != null) {
            configOpt = dynamicConfigRepository.findDepartmentConfig(configName, departmentId, companyId);
        } else {
            configOpt = dynamicConfigRepository.findCompanyConfig(configName, companyId);
        }
        return configOpt.map(DynamicConfig::getConfigValue).orElse(new JSONObject());
    }

    @Override
    @Transactional
    public void saveConfig(String configName, Long departmentId, JSONObject configValue) {
        DynamicConfig config;
        Long companyId = cn.v7soft.dao.tenant.TenantContext.getCurrentTenant();

        if (departmentId != null) {
            // 部门级别配置
            config = dynamicConfigRepository.findDepartmentConfig(configName, departmentId, companyId)
                    .orElseGet(() -> DynamicConfig.builder()
                            .configName(configName)
                            .departmentId(departmentId)
                            .build());
        } else {
            // 公司级别配置
            config = dynamicConfigRepository.findCompanyConfig(configName, companyId)
                    .orElseGet(() -> DynamicConfig.builder()
                            .configName(configName)
                            .build());
        }

        config.setConfigValue(configValue);
        dynamicConfigRepository.save(config);
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

