package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.ProtocolArticleGroup;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ProtocolArticleGroupRepository extends BaseRepository<ProtocolArticleGroup> {

    @Query("from ProtocolArticleGroup where website.id = :websiteId and language.code = :languageCode order by sort desc, id asc ")
    List<ProtocolArticleGroup> loadWebsiteProtocolArticles(@Param("websiteId") Long websiteId, @Param("languageCode") String languageCode);

    @Query("from ProtocolArticleGroup where country.id = :countryId and language.code = :languageCode order by sort desc, id asc ")
    List<ProtocolArticleGroup> loadCountryProtocolArticles(@Param("countryId") Long countryId, @Param("languageCode") String languageCode);

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO t_protocol_group_articles (protocol_group_id, article_id) VALUES (:protocolId, :articleId)", nativeQuery = true)
    void bindArticleToProtocolGroup(@Param("protocolId") Long protocolId, @Param("articleId")Long articleId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM t_protocol_group_articles WHERE protocol_group_id = :protocolId AND article_id = :articleId", nativeQuery = true)
    void unbindArticleFromProtocolGroup(@Param("protocolId") Long protocolId, @Param("articleId") Long articleId);


    @Modifying
    @Transactional
    @Query(value = "DELETE FROM t_protocol_group_articles WHERE article_id = :articleId", nativeQuery = true)
    void unbindArticles(@Param("articleId") Long articleId);


    @Modifying
    @Transactional
    @Query(value = "DELETE FROM t_protocol_group_articles WHERE protocol_group_id = :protocolId ", nativeQuery = true)
    void unbindArticlesInGroup(@Param("protocolId") Long protocolId);
}
