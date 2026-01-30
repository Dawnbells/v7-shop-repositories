package cn.v7soft.admin.dao;

import cn.hutool.json.JSONObject;
import lombok.Getter;

@Getter
public class ThemeConfig {

    private final String name;
    private final String cname;
    private final String version;

    private final JSONObject baseSchema;
    private final JSONObject templateSchema;
    private final JSONObject i18nSchema;
    private final JSONObject themeSchema;
    private final JSONObject baseValues;
    private final JSONObject templateValues;
    private final JSONObject i18nValues;
    private final JSONObject themeValues;

    public ThemeConfig(JSONObject node) {
        this.name = node.getStr("name");
        this.cname = node.getStr("cname");
        this.version = node.getStr("version");
        this.baseSchema = node.getJSONObject("base-schema");
        this.templateSchema = node.getJSONObject("template-schema");
        this.i18nSchema = node.getJSONObject("i18n-schema");
        this.themeSchema = node.getJSONObject("theme-schema");

        JSONObject defaultValues = node.getJSONObject("default-values");
        JSONObject baseValues = defaultValues.getJSONObject("base");
        JSONObject templateValues = defaultValues.getJSONObject("template");
        JSONObject i18nValues = defaultValues.getJSONObject("i18n");
        JSONObject themeValues = defaultValues.getJSONObject("theme");

        this.baseValues = baseValues == null ? new JSONObject() : baseValues;
        this.templateValues = templateValues == null ? new JSONObject() : templateValues;
        this.i18nValues = i18nValues == null ? new JSONObject() : i18nValues;
        this.themeValues = themeValues == null ? new JSONObject() : themeValues;
    }

    @Override
    public String toString() {
        JSONObject json = new JSONObject();
        json.set("name", name);
        json.set("cname", cname);
        json.set("version", version);
        return json.toString();
    }
}
