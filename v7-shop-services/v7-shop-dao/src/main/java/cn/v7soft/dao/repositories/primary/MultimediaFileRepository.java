package cn.v7soft.dao.repositories.primary;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.MultimediaFile;

@Repository
public interface MultimediaFileRepository extends BaseRepository<MultimediaFile> {

    @Query("""
            from MultimediaFile where mediaState = 'UPLOADED'
            """)
    List<MultimediaFile> findAllUploadedMultimediaFiles();

    @Query(value = """
            select * from
            `t_multimedia_files` as m where
            m.media_state <> 'UPLOADED'
            and
            m.media_state <> 'ERROR'
            and
            m.deleted_origin = false
            order by m.id
            limit 1
            """, nativeQuery = true)
    Optional<MultimediaFile> findDeletableMultimediaFiles();

    @Query(value = """
            from MultimediaFile  where id > :start order by id asc limit 1
            """)
    Optional<MultimediaFile> findNextMultimedia(@Param("start") long start);

    @Modifying
    @Transactional
    @Query("update MultimediaFile set status = 'DELETED' where folder.id = :folderId")
    int deleteAllInFolder(@Param("folderId") Long folderId);

}
