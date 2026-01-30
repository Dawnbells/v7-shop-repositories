package cn.v7soft.admin.service.dto;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.function.Function;

@Getter
@Setter
@Builder
public class TextModerationData {
    private boolean detected;
    /**
     * profanity
     */
    private String labels;
    /**
     * "{\"riskLevel\":\"medium\",\"detectedLanguage\":\"en\",\"riskTips\":\"profanity_Oral\",\"riskWords\":\"fuck,FUCK,Fuck\",\"translatedContent\":\"fuck\"}"
     */
    private String reason;

    public List<String> getTranslatedLabels() {
        if (!detected || StrUtil.isBlank(labels)) {
            return Collections.emptyList();
        }
        Map<String, String> translatedMap = new HashMap<>();
        translatedMap.put("violence", "暴恐");
        translatedMap.put("contraband", "违禁品");
        translatedMap.put("sexuality", "色情");
        translatedMap.put("profanity", "亵渎辱骂");
        translatedMap.put("pullinTraffic", "广告引流");
        translatedMap.put("regional", "地域对立");
        translatedMap.put("C_customized", "用户库命中");
        return Arrays.stream(labels.split(",")).map(s -> translatedMap.getOrDefault(s, "未定义类型")).toList();
    }


    public List<String> riskWords() {
        if (!detected || StrUtil.isBlank(reason)) {
            return Collections.emptyList();
        }
        try {
            JSONObject reasonJson = JSONUtil.parseObj(reason);
            String riskWords = (String) reasonJson.get("riskWords");
            return Arrays.stream(riskWords.split(",")).map(String::trim).toList();
        } catch (Throwable ignored) {

        }
        return Collections.emptyList();
    }

    public String riskLevel() {
        if (!detected || StrUtil.isBlank(reason)) {
            return "";
        }
        try {
            JSONObject reasonJson = JSONUtil.parseObj(reason);
            return (String) reasonJson.get("riskLevel");
        } catch (Throwable ignored) {

        }
        return "";
    }
}
