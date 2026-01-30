package cn.v7soft.admin.utils;

import java.util.regex.Pattern;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.v7soft.admin.controller.resp.RiskIpResponse;

public class IpUtils {
    // IPv4 正则
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}" +
            "(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)$");

    // IPv6 简单正则
    private static final Pattern IPV6_PATTERN = Pattern.compile(
            "([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}");

    /**
     * 判断是否是 IP 地址
     */
    public static boolean isIp(String record) {
        return IPV4_PATTERN.matcher(record).matches() || IPV6_PATTERN.matcher(record).matches();
    }
    public static RiskIpResponse getIpInfo(String ip) {
        String url = "https://pro.ip-api.com/json/" + ip + "?fields=536608767&key=lzh6YeMJjhueSdZ";
        String response = HttpUtil.get(url, 3000);
        JSONObject entries = JSONUtil.parseObj(response);
        return RiskIpResponse.builder()
                .ip(ip)
                .countryCode(entries.getStr("countryCode"))
                .country(entries.getStr("country"))
                .latitude(entries.getStr("lat"))
                .longitude(entries.getStr("lon"))
                .build();
    }

}
