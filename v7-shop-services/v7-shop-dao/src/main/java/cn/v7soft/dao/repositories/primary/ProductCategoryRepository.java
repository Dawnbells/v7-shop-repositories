package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.ProductCategory;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductCategoryRepository extends BaseRepository<ProductCategory> {
    /**
     * 根据分类名称和排除指定ID查询商品分类
     */
    @Query("FROM ProductCategory WHERE name = :name AND (:id IS NULL OR id <> :id) AND status = 'VALID'")
    ProductCategory findBySameName(@Param("name") String name, @Param("id") Long id);
}
