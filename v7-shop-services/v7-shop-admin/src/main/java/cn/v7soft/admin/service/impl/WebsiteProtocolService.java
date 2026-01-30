package cn.v7soft.admin.service.impl;

import org.springframework.stereotype.Service;

import cn.v7soft.admin.service.IWebsiteProtocolService;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.core.controller.request.DeleteRequest;
import cn.v7soft.dao.entities.primary.ProtocolArticleGroup;
import cn.v7soft.dao.repositories.primary.ProtocolArticleGroupRepository;

@Service
public class WebsiteProtocolService extends BaseDataRangeService<ProtocolArticleGroup, ProtocolArticleGroupRepository> implements IWebsiteProtocolService {

    public WebsiteProtocolService(ProtocolArticleGroupRepository repository) {
        super(repository);
    }

    @Override
    public void bindArticleToProtocolGroup(Long protocolId, Long articleId) {
        repository.bindArticleToProtocolGroup(protocolId, articleId);
    }

    @Override
    public void unbindArticleFromProtocolGroup(Long protocolId, Long articleId) {
        repository.unbindArticleFromProtocolGroup(protocolId, articleId);
    }

    @Override
    public void deleteAllArticleUnderProtocolGroup(DeleteRequest request) {
        request.getIdList().forEach(repository::unbindArticlesInGroup);
    }
}
