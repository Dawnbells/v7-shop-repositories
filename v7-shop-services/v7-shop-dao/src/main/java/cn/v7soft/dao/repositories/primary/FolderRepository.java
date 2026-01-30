package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.Folder;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FolderRepository extends BaseRepository<Folder> {
    /**
     * 根据文件夹名称查询文件夹
     */
    @Query("FROM Folder WHERE name = :name AND status = 'VALID'")
    Folder findByName(@Param("name") String name);

    @Query("from Folder where parent is null ")
    List<Folder> findAllTopFolder();
}
