package cn.v7soft.admin.service;

import cn.v7soft.common.service.IBaseDataRangeService;
import cn.v7soft.dao.entities.primary.PixelAccount;
import cn.v7soft.dao.enums.PixelAccountState;

import java.util.List;

public interface IPixelAccountService extends IBaseDataRangeService<PixelAccount> {

    /**
     * 根据网站ID获取所有像素账号
     * @param websiteId 网站ID
     * @return 像素账号列表
     */
    List<PixelAccount> getPixelAccountsByWebsiteId(Long websiteId);

    /**
     * 根据状态获取像素账号
     * @param state 状态
     * @return 符合条件的像素账号列表
     */
    List<PixelAccount> getPixelAccountsByState(PixelAccountState state);
}
