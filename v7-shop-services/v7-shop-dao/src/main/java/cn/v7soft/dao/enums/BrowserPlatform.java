package cn.v7soft.dao.enums;

import cn.hutool.http.useragent.Platform;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import org.apache.catalina.User;

public enum BrowserPlatform {
    ANDROID,
    IOS,
    IPAD,
    WINDOWS,
    MAC,
    LINUX,
    MOBILE,
    DESKTOP,
    UNKNOWN;

    public static BrowserPlatform fromUaStr(String ua) {
        return fromUa(UserAgentUtil.parse(ua));
    }
    public static BrowserPlatform fromUa(UserAgent ua) {
        Platform platform = ua.getPlatform();
        if (platform.isAndroid()) {
            return ANDROID;
        }
        if (platform.isIos()) {
            return IOS;
        }
        if (platform.isIPad()) {
            return IPAD;
        }
        if (platform.isMobile()) {
            return MOBILE;
        }
        if ("Windows".equals(platform.getName())) {
            return WINDOWS;
        }
        if ("Mac".equals(platform.getName())) {
            return MAC;
        }
        if ("Linux".equals(platform.getName())) {
            return LINUX;
        }
        if (Platform.desktopPlatforms.contains(platform)) {
            return DESKTOP;
        }
        return UNKNOWN;
    }
}
