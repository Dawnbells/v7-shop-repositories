package cn.v7soft.admin.service.email;

import cn.hutool.json.JSONObject;

public enum DepartmentEmailState {
    INHERIT,
    ENABLED,
    DISABLED;

    public static DepartmentEmailState from(JSONObject emailConfig) {
        if (emailConfig == null) {
            return INHERIT;
        }
        String configuredState = emailConfig.getStr("state");
        if (configuredState != null && !configuredState.isBlank()) {
            try {
                return valueOf(configuredState);
            } catch (IllegalArgumentException ignored) {
                return INHERIT;
            }
        }
        return emailConfig.getBool("open", false) ? ENABLED : DISABLED;
    }
}
