package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.ThemeTemplate;
import cn.v7soft.dao.enums.ShareType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ThemeTemplateRepository extends BaseRepository<ThemeTemplate> {

    /**
     * 检查同名模板
     */
    @Query("from ThemeTemplate where name = :name and (:id is null or id <> :id) and owner.id = :userId and status = 'VALID'")
    ThemeTemplate findBySameName(@Param("name") String name, @Param("id") Long id, @Param("userId") Long userId);

}
