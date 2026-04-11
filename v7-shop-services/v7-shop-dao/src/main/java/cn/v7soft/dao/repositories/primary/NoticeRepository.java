package cn.v7soft.dao.repositories.primary;

import cn.v7soft.dao.entities.primary.Notice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoticeRepository extends JpaRepository<Notice, Long> {

    @Query("SELECT n FROM Notice n WHERE (n.userId = :userId OR n.userId IS NULL) AND n.isRead = false ORDER BY n.createTime DESC")
    List<Notice> findUnreadByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(n) FROM Notice n WHERE (n.userId = :userId OR n.userId IS NULL) AND n.isRead = false")
    long countUnreadByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE Notice n SET n.isRead = true WHERE n.id = :id")
    void markAsRead(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Notice n SET n.isRead = true WHERE (n.userId = :userId OR n.userId IS NULL) AND n.isRead = false")
    void markAllAsReadByUserId(@Param("userId") Long userId);
}
