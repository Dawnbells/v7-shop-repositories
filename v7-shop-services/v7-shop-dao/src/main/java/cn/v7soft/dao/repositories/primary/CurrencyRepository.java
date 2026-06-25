package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.Currency;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CurrencyRepository extends BaseRepository<Currency> {
    @Query("from Currency where name=:name and (:id is null or id<>:id) and status='VALID'")
    Currency findBySameName(@Param("name") String name, @Param("id") Long id);

    /**
     * 根据语言推荐关联的货币
     *
     * @param languageId 语言ID
     * @return 关联的货币
     */
    @Query("""
            SELECT DISTINCT c.currency FROM Country c JOIN c.languages l WHERE l.id = :languageId
            """)
    Optional<Currency> getRecommendByLanguage(Long languageId);

    @Query("""
            FROM Currency where code = :code
            """)
    Optional<Currency> findByCode(@Param("code") String currencyCode);

    @Query("FROM Currency c WHERE c.status = 'VALID' ORDER BY c.code ASC")
    List<Currency> findAllValid();
}
