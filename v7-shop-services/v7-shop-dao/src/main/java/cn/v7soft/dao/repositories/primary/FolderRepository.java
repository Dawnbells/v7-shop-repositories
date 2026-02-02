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

    /**
     * 检查顶层文件夹中是否存在同名文件夹
     */
    @Query("SELECT COUNT(f) > 0 FROM Folder f WHERE f.name = :name AND f.parent IS NULL AND f.status = 'VALID'")
    boolean existsByNameInTopLevel(@Param("name") String name);

    /**
     * 检查指定父文件夹下是否存在同名文件夹
     */
    @Query("SELECT COUNT(f) > 0 FROM Folder f WHERE f.name = :name AND f.parent.id = :parentId AND f.status = 'VALID'")
    boolean existsByNameInParent(@Param("name") String name, @Param("parentId") Long parentId);
}
