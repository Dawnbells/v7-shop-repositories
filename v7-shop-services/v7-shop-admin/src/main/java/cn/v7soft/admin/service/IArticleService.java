package cn.v7soft.admin.service;

import cn.v7soft.common.service.IBaseDataRangeService;
import cn.v7soft.core.controller.request.DeleteRequest;
import cn.v7soft.dao.entities.primary.Article;

public interface IArticleService extends IBaseDataRangeService<Article> {

    void cleanupBeforeDelete(DeleteRequest request);
    // 可以在此定义与Article相关的其他服务方法
}
