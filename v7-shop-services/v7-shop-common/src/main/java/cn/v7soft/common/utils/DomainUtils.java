package cn.v7soft.common.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.enums.ServiceResponseEnum;
import cn.v7soft.dao.tenant.WebsiteContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.TextUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.StringUtils;

import java.net.URL;
import java.util.Enumeration;

@Slf4j
public class DomainUtils {

    public static String getOriginTopLevelDomain(HttpServletRequest request) {
        String origin = getDomain(request);
        return getTopLevelDomain(origin);
    }

    public static Long getOriginShopId(HttpServletRequest request) {
        String domain = getDomain(request);
        return getAdminShopId(domain);
    }

    @NotNull
    private static String getDomain(HttpServletRequest request) {
        try {
            String defaultDomain = "xyzdwd.com";
            String origin = request.getHeader("Origin");
            String referer = request.getHeader("Referer");
//            log.debug("origin is " + origin + ", referer is " + referer + ", " + request.getRequestURI());
            if (StrUtil.isBlank(origin) && StrUtil.isBlank(referer)) {
//                log.debug("=====" + request.getRequestURI() + "====");
//                log.debug("===== Request Headers =====");
                Enumeration<String> headerNames = request.getHeaderNames();
                while (headerNames.hasMoreElements()) {
                    String headerName = headerNames.nextElement();
                    String headerValue = request.getHeader(headerName);
                }
            }
            if (!StringUtils.hasText(origin)) {
                origin = referer;
            }
            if (!StringUtils.hasText(origin) || origin.contains("localhost") || origin.contains("127.0.0.1")) {
                if (origin == null || origin.contains("localhost") || origin.contains("127.0.0.1")) {
                    if (origin.contains("admin")) {
                        origin = origin.replace("localhost:5200", defaultDomain).replace("127.0.0.1:5200", defaultDomain);
                        origin = origin.replace("localhost:5201", defaultDomain).replace("127.0.0.1:5201", defaultDomain);
                    } else {
                        origin = "admin." + defaultDomain;
                    }
                } else {
                    origin = "admin." + defaultDomain;
                }
            }
            return origin;
        } catch (Exception e) {
            ServiceResponseEnum.ERR_FORBIDDEN.throwException();
        }
        return "";
    }

    /**
     * 获取域名中的一级域名
     *
     * @param domain 域名
     * @return 一级域名
     */
    private static String getTopLevelDomain(String domain) {
        try {
            URL url = new URL((domain.startsWith("https://") || domain.startsWith("http://") ? "" : "https://") + domain);
            String host = url.getHost();
            String[] hostParts = host.split("\\.");
            analyzeWebsite(domain, hostParts);
            if (hostParts.length > 2 ) {
                return hostParts[hostParts.length - 2] + "." + hostParts[hostParts.length - 1];
            }
            ClientResponseEnum.PARAMETER_ILLEGAL.throwException("无法获取一级域名: " + domain);
            return "无法获取一级域名: " + domain;
        } catch (Exception e) {
//            e.printStackTrace();
            ClientResponseEnum.PARAMETER_ILLEGAL.throwException("URL格式错误: " + domain);
            return "URL格式错误: " + domain;
        }
    }

    private static void analyzeWebsite(String domain, String[] hostParts) {
//        log.debug("analyze website: " + JSONUtil.toJsonStr(hostParts));
        if (hostParts.length > 1 && hostParts[0].startsWith("admin")) {
            boolean isWebsiteAdmin = false;
            long shopId = 0;
            try {
                String idStr = hostParts[0].replace("admin", "");
                if (!TextUtils.isBlank(idStr)) {
                    shopId = Long.parseLong(idStr);
                    isWebsiteAdmin = true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            WebsiteContext.set(domain, isWebsiteAdmin, shopId);
        }
    }

    private static Long getAdminShopId(String domain) {
        try {
            URL url = new URL((domain.startsWith("https://") || domain.startsWith("http://") ? "" : "https://") + domain);
            String host = url.getHost();
            String[] hostParts = host.split("\\.");
            if (hostParts.length > 1 && (hostParts[0].startsWith("admin") || hostParts[0].startsWith("shop"))) {
                String idStr = hostParts[0].replace("admin", "").replace("shop", "");
                if (TextUtils.isBlank(idStr)) {
                    return 0L;
                }
                return Long.parseLong(idStr);
            }
            return -1L;
        } catch (Exception e) {
            e.printStackTrace();
            return -2L;
        }
    }
}
