package cn.v7soft.admin.service.impl;

import java.util.function.Consumer;

import cn.v7soft.admin.service.IArticleService;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.core.controller.request.DeleteRequest;
import cn.v7soft.dao.entities.primary.Article;
import cn.v7soft.dao.repositories.primary.ArticleRepository;
import cn.v7soft.dao.repositories.primary.ProtocolArticleGroupRepository;

import org.springframework.stereotype.Service;

@Service
public class ArticleService extends BaseDataRangeService<Article, ArticleRepository> implements IArticleService {
    private final ProtocolArticleGroupRepository protocolArticleGroupRepository;
    public ArticleService(ArticleRepository repository, ProtocolArticleGroupRepository protocolArticleGroupRepository) {
        super(repository);
        this.protocolArticleGroupRepository = protocolArticleGroupRepository;
    }

    @Override
    protected void checkKeyConstraint(Article data) {
        // 添加相关业务逻辑，如检查是否有重复的文章标题等
    }

    @Override
    public void cleanupBeforeDelete(DeleteRequest request) {
        request.getIdList().forEach(protocolArticleGroupRepository::unbindArticles);
    }
}
