package cn.v7soft.dao.repositories.primary;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.Product;

public interface ProductRepository extends BaseRepository<Product> {

    @Query("from Product where spu.id in :spuIds")
    List<Product> findAllBySpuIdIn(@Param("spuIds") Collection<Long> spuIds);

    /**
     * 同一SPU下不允许存在相同国家和语言的商品
     */
    @Query("""
            from Product
            where
            spu.id = :spuId
            and
            (:id is null or id <> :id)
            and
            country.id = :countryId
            and
            language.id = :languageId
            and
            status = 'VALID'
            """)
    Product findBySameCountryLanguage(@Param("spuId") Long spuId, @Param("id") Long id,
                                      @Param("countryId") Long countryId,
                                      @Param("languageId") Long languageId);

    @Query("""
                SELECT
                DISTINCT merchandise
                FROM Product
                WHERE
                (:query IS NULL OR merchandise LIKE :query)
                AND
                (:departmentId is NULL OR owner.department.id = :departmentId)
            """)
    List<String> remoteQueryMerchandise(@Param("query") String query, @Param("departmentId") Long departmentId);


    @Query("from Product where botShowSpu.id=:spuId or riskUserShowSpu.id=:spuId or blacklistedUserShowSpu.id=:spuId")
    List<Product> findAllProductRelatedFromSpu(@Param("spuId") Long spuId);
}
