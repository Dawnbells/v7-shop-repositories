package cn.v7soft.core.utils;

import java.nio.charset.StandardCharsets;

import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import cn.hutool.core.util.IdUtil;
import cn.hutool.extra.servlet.JakartaServletUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class HttpRequestHelper {

    public static String getAcceptImageType() {
        HttpServletRequest request = getCurrentHttpRequest();
        String acceptHeader = JakartaServletUtil.getHeader(request, "Accept", StandardCharsets.UTF_8);
        if (acceptHeader != null && acceptHeader.contains("image/avif")) {
            // 浏览器支持 AVIF
            return "avif";
        }
        if (acceptHeader != null && acceptHeader.contains("image/webp")) {
            // 支持 WebP，但不支持 AVIF
            return "webp";
        }
        return "";
    }

    public static HttpServletRequest getCurrentHttpRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

        if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }

        return null;
    }

    public static HttpServletResponse getCurrentHttpResponse() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

        if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getResponse();
        }

        return null;
    }

    public static String ensureBrowserFingerprint(HttpServletRequest request, HttpServletResponse response) {
        Cookie fingerprintCookie = JakartaServletUtil.getCookie(request, "fingerprint");
        String fingerprint;
        if (fingerprintCookie == null || !StringUtils.hasText(fingerprintCookie.getValue())) {
            fingerprint = IdUtil.fastSimpleUUID();
            JakartaServletUtil.addCookie(response, "fingerprint", fingerprint, 365 * 24 * 60 * 60);
        } else {
            fingerprint = fingerprintCookie.getValue();
        }
        return fingerprint;
    }

    public static String getBrowserFingerprint(HttpServletRequest request) {
        Cookie fingerprintCookie = JakartaServletUtil.getCookie(request, "fingerprint");
        if (fingerprintCookie == null || !StringUtils.hasText(fingerprintCookie.getValue())) {
            return null;
        }
        return fingerprintCookie.getValue();
    }

    public static String getRemoteIp() {
        HttpServletRequest httpServletRequest = getCurrentHttpRequest();
        if (httpServletRequest == null) {
            return "Unknown";
        }
        return JakartaServletUtil.getClientIP(httpServletRequest);
    }
}
