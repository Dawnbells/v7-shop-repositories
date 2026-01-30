package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.ProductSKU;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductSKURepository extends BaseRepository<ProductSKU> {
    /**
     * 根据 SKU 编码和排除指定 ID 查询商品 SKU
     */
    @Query("""
                SELECT COUNT(s) > 0
                FROM ProductSKU s
                JOIN s.owner o
                JOIN o.department d
                WHERE d.id = (
                    SELECT o2.department.id
                    FROM SystemUser o2
                    WHERE o2.id = :ownerId
                )
                AND s.skuCode = :code
                AND (:id is NULL or s.id != :id)
            """)
    boolean existsByCodeInSameDepartment(@Param("id") Long id, @Param("code") String code, @Param("ownerId") Long ownerId);


    @Query("""
            from ProductSKU s
            JOIN s.owner o
            JOIN o.department d
            WHERE d.id = (
                SELECT o2.department.id
                FROM SystemUser o2
                WHERE o2.id = :ownerId
            )
            AND s.skuCode = :skuCode
            """)
    Optional<ProductSKU> findBySkuCode(@Param("skuCode") String skuCode, @Param("ownerId") Long ownerId);

    @Query("""
            from ProductSKU s
            JOIN s.owner o
            JOIN o.department d
            WHERE d.id = (
                SELECT o2.department.id
                FROM SystemUser o2
                WHERE o2.id = :ownerId
            )
            AND s.skuCode in :skuCodes
            """)
    List<ProductSKU> listBySkuCodes(@Param("skuCodes") List<String> skuCodes, @Param("ownerId") Long ownerId);
}

