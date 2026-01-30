package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.Article;

public interface ArticleRepository extends BaseRepository<Article> {
    // 可以在此添加自定义查询方法，如按标题或文章类型查询
}
