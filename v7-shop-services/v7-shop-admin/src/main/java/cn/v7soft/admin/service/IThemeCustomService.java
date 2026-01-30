package cn.v7soft.admin.service;

import cn.v7soft.admin.controller.req.PostThemeConfigRequest;
import cn.v7soft.common.service.IBaseDataRangeService;
import cn.v7soft.core.controller.request.DeleteRequest;
import cn.v7soft.dao.entities.primary.ThemeCustom;

public interface IThemeCustomService extends IBaseDataRangeService<ThemeCustom> {

    /**
     * 提交配置信息
     * @param request 配置
     */
    void postConfig(PostThemeConfigRequest request);

    /**
     * 清楚域名绑定被删除的主题
     *
     * @param request 删除的请求
     * @return
     */
    boolean clearDomainTheme(DeleteRequest request);
}

