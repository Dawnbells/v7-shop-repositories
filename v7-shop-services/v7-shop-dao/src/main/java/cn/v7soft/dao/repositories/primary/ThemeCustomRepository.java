package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.ThemeCustom;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ThemeCustomRepository extends BaseRepository<ThemeCustom> {
    @Query("from ThemeCustom where name = :name and (:id is null or id <> :id) and (:userId is null or owner.id = :userId) and status = 'VALID'")
    ThemeCustom findBySameName(@Param("name") String name, @Param("id") Long id, @Param("userId") Long userId);
}

