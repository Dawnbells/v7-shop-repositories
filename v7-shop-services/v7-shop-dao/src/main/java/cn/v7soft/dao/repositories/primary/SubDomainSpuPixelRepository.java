package cn.v7soft.dao.repositories.primary;

import cn.v7soft.dao.entities.primary.SubDomainSpuPixel;
import cn.v7soft.dao.entities.primary.SubDomainSpuPixelId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 子域名SPU像素关联Repository
 */
public interface SubDomainSpuPixelRepository extends JpaRepository<SubDomainSpuPixel, SubDomainSpuPixelId> {

    /**
     * 查询子域名和SPU绑定的所有像素
     */
    @Query("""
            SELECT p FROM SubDomainSpuPixel p
            LEFT JOIN FETCH p.pixelAccount
            WHERE p.subDomainId = :subDomainId AND p.spuId = :spuId
            """)
    List<SubDomainSpuPixel> findBySubDomainIdAndSpuId(
            @Param("subDomainId") Long subDomainId,
            @Param("spuId") Long spuId);

    /**
     * 检查是否已存在绑定关系
     */
    boolean existsBySubDomainIdAndSpuIdAndPixelId(Long subDomainId, Long spuId, Long pixelId);

    /**
     * 删除指定的绑定关系
     */
    void deleteBySubDomainIdAndSpuIdAndPixelId(Long subDomainId, Long spuId, Long pixelId);
}

