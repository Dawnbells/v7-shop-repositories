package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.Announcement;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AnnouncementRepository extends BaseRepository<Announcement> {
    @Query("""
            from Announcement
            where website.id = :websiteId
            """)
    List<Announcement> findByWebsiteId(@Param("websiteId") Long websiteId);

    @Query("""
            from Announcement
            where country.id = :countryId
            """)
    List<Announcement> findByCountryId(@Param("countryId") Long countryId);
}
