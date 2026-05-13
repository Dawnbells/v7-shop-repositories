package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.Currency;
import cn.v7soft.dao.entities.primary.Website;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebsiteRepository extends BaseRepository<Website> {
    @Query("""
            from Website
            where
            name = :name
            and
            (:id is null or id <> :id)
            and
            (:userId is null or owner.id = :userId)
            and
            status = 'VALID'
            """)
    Website findBySameName(@Param("name") String name, @Param("id") Long id, @Param("userId") Long userId);

    /**
     * 根据ID获取商城名称
     * @param id 商城ID
     * @return 商城名称
     */
    @Query("select name from Website where id=:id")
    String getNameById(@Param("id") Long id);

    @Query("select w.currency from Website w where w.id=:id")
    Currency getCurrencyById(@Param("id") Long id);

    @Query("select w.owner.id from Website w where w.id=:id")
    Long getOwnerIdById(@Param("id") Long id);

    @Query("select w.owner.department.id from Website w where w.id=:id")
    Long getOwnerDepartmentIdById(@Param("id") Long id);
}
