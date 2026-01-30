package cn.v7soft.dao.repositories.primary;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.SubDomain;
import cn.v7soft.dao.enums.CloakStrategy;
import cn.v7soft.dao.enums.DomainType;
import jakarta.transaction.Transactional;

public interface SubDomainRepository extends BaseRepository<SubDomain> {

    @Query("""
            from SubDomain
            where
            fullName = :name
            and
            (:id is null or id <> :id)
            and
            (:userId is null or parentDomain.owner.id = :userId)
            and
            status = 'VALID'
            """)
    SubDomain findBySameName(@Param("name") String name, @Param("id") Long id, @Param("userId") Long userId);

    @Query("""
            from SubDomain
            where
            fullName = :domain
            """)
    SubDomain findByFullName(@Param("domain") String domain);

    @Query("""
            SELECT
            count(*)
            FROM SubDomain
            where
            frontServer.id = :frontServerId
            and
            parentDomain.id = :topLevelDomainId
            """)
    int countTopLevelDomainInSameServer(@Param("topLevelDomainId") Long topLevelDomainId, @Param("frontServerId") Long frontServerId);

    @Query("""
            from SubDomain s where s.website.id = :websiteId
            """)
    List<SubDomain> findAllByWebsite(@Param("websiteId") Long websiteId);

    @Query("""
            select s.parentDomain.cloakStrategy from SubDomain s where s.id = :subdomainId
            """)
    CloakStrategy getCloakStrategy(Long subdomainId);

    Optional<SubDomain> findByFullNameAndType(String fullName, DomainType domainType);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SubDomain set theme = null where theme.id in :themeIds")
    void clearDomainThemes(@Param("themeIds") List<Long> themeIds);
}
