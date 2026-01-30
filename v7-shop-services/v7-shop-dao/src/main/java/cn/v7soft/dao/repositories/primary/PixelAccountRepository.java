package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.PixelAccount;
import cn.v7soft.dao.enums.PixelAccountState;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PixelAccountRepository extends BaseRepository<PixelAccount> {

    /**
     * 根据网站ID查找所有像素账号
     * @param websiteId 网站ID
     * @return 像素账号列表
     */
    @Query("select p from PixelAccount p join p.spuList s where p.website.id = :websiteId and s.id = :spuId")
    List<PixelAccount> findByWebsiteIdAndSpuId(@Param("websiteId") Long websiteId, @Param("spuId") Long spuId);

    /**
     * 根据状态查找像素账号
     * @param state 状态
     * @return 符合条件的像素账号列表
     */
    List<PixelAccount> findByState(@Param("state") PixelAccountState state);
}
