package cn.v7soft.dao.utils;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.enums.ViewMode;

public class SaSessionUtil {

    private static final String KEY_VIEW_MODE = "ViewMode";

    public static SystemUserDto getLoginUser() {
        return (SystemUserDto) StpUtil.getSession().get(SaSession.USER);
    }

    public static SystemUser getLoginOwner() {
        return getLoginUser().toOwner();
    }

    public static void setViewMode(ViewMode viewMode) {
        StpUtil.getSession().set(KEY_VIEW_MODE, viewMode);
    }

    public static ViewMode getViewMode() {
        try {
            return StpUtil.getSession().get(KEY_VIEW_MODE, ViewMode.TEAM);
        } catch (Exception e) {
            return ViewMode.TEAM;
        }
    }

    public static boolean isAuditOrders() {
        return Boolean.TRUE.equals(getLoginUser().getIsAuditOrders());
    }
}
