package cn.v7soft.admin.service;

import cn.hutool.json.JSONObject;

import java.util.Optional;

/**
 * 动态配置服务接口
 */
public interface IDynamicConfigService {

    /**
     * 获取部门配置，如果当前部门没有配置，则向上级部门查找，直到找到配置或到达公司级别
     *
     * @param configName   配置名称
     * @param departmentId 部门ID
     * @param companyId    公司ID
     * @return 配置值
     */
    Optional<JSONObject> getConfigWithFallback(String configName, Long departmentId, Long companyId);

    /**
     * 获取配置值
     *
     * @param configName   配置名称
     * @param departmentId 部门ID，为 null 表示公司级别配置
     * @param companyId    公司ID
     * @return 配置值
     */
    JSONObject getConfigValue(String configName, Long departmentId, Long companyId);

    /**
     * 保存配置
     *
     * @param configName   配置名称
     * @param departmentId 部门ID，为 null 表示公司级别配置
     * @param configValue  配置值
     */
    void saveConfig(String configName, Long departmentId, JSONObject configValue);
}

