package cn.v7soft.dao.repositories.primary;

import cn.v7soft.dao.entities.primary.SubDomainSpuLandingPage;
import cn.v7soft.dao.entities.primary.SubDomainSpuLandingPageId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 子域名SPU落地页关联Repository
 */
public interface SubDomainSpuLandingPageRepository extends JpaRepository<SubDomainSpuLandingPage, SubDomainSpuLandingPageId> {

    /**
     * 查询子域名和SPU的所有个性化落地页配置
     */
    List<SubDomainSpuLandingPage> findBySubDomainIdAndSpuId(Long subDomainId, Long spuId);
}
