package cn.v7soft.admin.service.email;

import cn.hutool.json.JSONObject;

public record ResolvedOrderEmailConfig(
        boolean enabled,
        JSONObject smtp,
        String smtpSource,
        JSONObject template,
        String templateSource) {

    public static ResolvedOrderEmailConfig disabled(String source) {
        return new ResolvedOrderEmailConfig(false, null, source, null, "built-in");
    }
}
