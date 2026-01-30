package cn.v7soft.admin.service;

import cn.v7soft.admin.controller.req.CheckSpuTicketRequest;
import cn.v7soft.admin.controller.req.GenerateSharedUrlRequest;
import cn.v7soft.admin.controller.req.ShareSpuRequest;
import cn.v7soft.admin.controller.resp.SharedSpuResponse;
import cn.v7soft.common.service.IBaseDataRangeService;
import cn.v7soft.dao.entities.primary.Spu;

import java.util.List;

public interface ISpuService extends IBaseDataRangeService<Spu> {
    /**
     * 绑定SPU到网站
     *
     * @param spuId spuId
     */
    void bindSpuToWebsite(Long spuId);

    /**
     * 解除绑定
     *
     * @param spuIds spuId
     */
    void unbindSpuToWebsite(List<Long> spuIds);

    /**
     * 获取用户下一个spu code
     *
     * @return spu code
     */
    Integer getNextSpuUserCode();

    /**
     * 切换共享状态
     *
     * @param id     ID
     * @param isOpen 是否共享
     */
    void switchOpen(Long id, boolean isOpen);

    /**
     * 分享SPU
     *
     * @param request 请求
     * @return 返回分享结果
     */
    SharedSpuResponse shareSpu(ShareSpuRequest request);

    /**
     * 检查SPU预览ticket有效性
     *
     * @param request 请求
     * @return 是否有效
     */
    boolean checkSpuTicket(CheckSpuTicketRequest request);

    void deleteAllSpuRelatedData(Long id);

    String generateSharedUrl(GenerateSharedUrlRequest request);
}