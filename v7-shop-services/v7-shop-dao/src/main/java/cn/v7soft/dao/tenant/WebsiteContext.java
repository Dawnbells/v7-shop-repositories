package cn.v7soft.dao.tenant;

import cn.v7soft.dao.entities.primary.Website;
import cn.v7soft.dao.enums.RouterPlatform;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

@Slf4j
public abstract class WebsiteContext {
    /**
     * 当前访问的商城ID，仅在 {@link #isWebsiteAdmin} = true时有效
     */
    private static final ThreadLocal<Long> currentWebsiteId = new ThreadLocal<>();
    /**
     * 当前访问域名
     */
    private static final ThreadLocal<String> domainLocal = new ThreadLocal<>();
    /**
     * 当前访问的是否是商城管理页面
     * true-访问商城管理页面
     * false-访问总后台管理页面
     */
    private static final ThreadLocal<Boolean> isWebsiteAdmin = new ThreadLocal<>();

    public static boolean isWebsiteAdmin() {
        return Objects.equals(isWebsiteAdmin.get(), Boolean.TRUE);
    }

    public static Long getCurrentWebsiteId() {
        return currentWebsiteId.get();
    }
    public static String getDomain() {
        return domainLocal.get();
    }

    public static void clear() {
        currentWebsiteId.remove();
        isWebsiteAdmin.remove();
        domainLocal.remove();
    }

    public static void set(String domain, boolean websiteAdmin, long websiteId) {
        domainLocal.set(domain);
        isWebsiteAdmin.set(websiteAdmin);
        currentWebsiteId.set(websiteId);
    }

    public static RouterPlatform getCurrentPlatform() {
        return Boolean.TRUE.equals(isWebsiteAdmin.get())? RouterPlatform.MALL_MANAGER: RouterPlatform.MANAGER;
    }

    public static Website getCurrentWebsite() {
        return Website.builder().id(getCurrentWebsiteId()).build();
    }

}