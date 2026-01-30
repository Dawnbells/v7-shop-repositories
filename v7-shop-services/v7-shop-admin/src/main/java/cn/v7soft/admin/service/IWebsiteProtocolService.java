package cn.v7soft.admin.service;

import cn.v7soft.common.service.IBaseDataRangeService;
import cn.v7soft.core.controller.request.DeleteRequest;
import cn.v7soft.dao.entities.primary.ProtocolArticleGroup;

public interface IWebsiteProtocolService extends IBaseDataRangeService<ProtocolArticleGroup> {

    void bindArticleToProtocolGroup(Long protocolId, Long articleId);

    void unbindArticleFromProtocolGroup(Long protocolId, Long articleId);

    void deleteAllArticleUnderProtocolGroup(DeleteRequest request);
}
