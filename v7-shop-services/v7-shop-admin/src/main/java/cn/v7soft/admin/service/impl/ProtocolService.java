package cn.v7soft.admin.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.v7soft.admin.controller.req.ArticleGroupRequest;
import cn.v7soft.admin.controller.req.EditProtocolTranslationRequest;
import cn.v7soft.admin.service.IProtocolService;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.dao.entities.primary.Article;
import cn.v7soft.dao.entities.primary.Protocol;
import cn.v7soft.dao.entities.primary.ProtocolArticleGroup;
import cn.v7soft.dao.entities.primary.ProtocolTranslation;
import cn.v7soft.dao.repositories.primary.ProtocolRepository;
import cn.v7soft.dao.repositories.primary.ProtocolTranslationRepository;

@Service
public class ProtocolService extends BaseDataRangeService<Protocol, ProtocolRepository> implements IProtocolService {
    private final ProtocolTranslationRepository protocolTranslationRepository;
    public ProtocolService(ProtocolRepository repository, ProtocolTranslationRepository protocolTranslationRepository) {
        super(repository);
        this.protocolTranslationRepository = protocolTranslationRepository;
    }

    @Override
    @Transactional
    public void editProtocolTranslation(EditProtocolTranslationRequest request) {
        ProtocolTranslation protocolTranslation = protocolTranslationRepository.getReferenceById(request.getIdLongValue());
        protocolTranslation.getArticleGroupList().clear();
        for (ArticleGroupRequest articleGroupRequest : request.getArticleGroupList()) {
            protocolTranslation.addArticleGroup(ProtocolArticleGroup.builder()
                                                        .name(articleGroupRequest.getName())
                                                        .sort(articleGroupRequest.getSort())
                                                        .translation(protocolTranslation)
                                                        .language(protocolTranslation.getLanguage())
                                                        .articleList(
                                                                articleGroupRequest.getArticleList().stream()
                                                                        .<Article>map(id -> Article.builder().id(Long.valueOf(id)).build())
                                                                        .toList()
                                                        )
                                                        .build());
        }
        protocolTranslationRepository.save(protocolTranslation);
    }
}
