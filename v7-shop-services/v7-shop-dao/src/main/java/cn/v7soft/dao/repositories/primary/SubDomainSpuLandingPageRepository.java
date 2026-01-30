package cn.v7soft.dao.repositories.primary;

import cn.v7soft.dao.entities.primary.Spu;
import cn.v7soft.dao.entities.primary.SubDomainSpuLandingPage;
import cn.v7soft.dao.entities.primary.SubDomainSpuLandingPageId;
import cn.v7soft.dao.enums.LandingPageType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 子域名SPU落地页配置Repository
 */
public interface SubDomainSpuLandingPageRepository extends JpaRepository<SubDomainSpuLandingPage, SubDomainSpuLandingPageId> {

    /**
     * 查询子域名和SPU的所有落地页配置
     */
    List<SubDomainSpuLandingPage> findBySubDomainIdAndSpuId(Long subDomainId, Long spuId);

    /**
     * 删除子域名和SPU的所有落地页配置
     */
    void deleteBySubDomainIdAndSpuId(Long subDomainId, Long spuId);

    /**
     * 检查是否存在绑定关系
     */
    boolean existsBySubDomainIdAndSpuIdAndLandingPageType(Long subDomainId, Long spuId, LandingPageType landingPageType);

    /**
     * 查询子域名绑定的所有SPU（通过 LAND 类型，带关键字搜索和预加载）
     * @param subDomainId 子域名ID
     * @param keyword 搜索关键字（匹配name、id、code）
     * @param pageable 分页参数
     * @return SPU列表（包含productList和country）
     */
    @Query("""
            SELECT DISTINCT s FROM SubDomainSpuLandingPage lp
            JOIN lp.spu s
            LEFT JOIN FETCH s.productList p
            LEFT JOIN FETCH p.country
            WHERE lp.subDomainId = :subDomainId
            AND lp.landingPageType = 'LAND'
            AND (:keyword IS NULL OR :keyword = ''
                 OR s.name LIKE CONCAT('%', :keyword, '%')
                 OR CAST(s.id AS string) LIKE CONCAT('%', :keyword, '%')
                 OR CAST(s.code AS string) LIKE CONCAT('%', :keyword, '%'))
            """)
    List<Spu> findBoundSpusBySubDomainIdWithKeyword(
            @Param("subDomainId") Long subDomainId,
            @Param("keyword") String keyword,
            Pageable pageable);
}
