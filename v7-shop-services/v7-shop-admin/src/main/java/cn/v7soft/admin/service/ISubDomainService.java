package cn.v7soft.admin.service;

import cn.v7soft.admin.controller.req.BindPixelsRequest;
import cn.v7soft.admin.controller.resp.SubDomainSpuDetailResponse;
import cn.v7soft.admin.service.dto.SubDomainDto;
import cn.v7soft.core.service.IBaseService;
import cn.v7soft.dao.entities.primary.Spu;
import cn.v7soft.dao.entities.primary.SubDomain;

import java.util.List;
import java.util.Optional;

public interface ISubDomainService extends IBaseService<SubDomain> {
    /**
     * 远程查询根据关键字所有归属域名
     * @param keyword 关键字
     * @return 批评的域名列表
     */
    List<SubDomain> queryDomainsByKeyword(String keyword);

    /**
     * 远程查询根据关键字所有空闲的中继域名
     * @param keyword 关键字
     * @return 批评的域名列表
     */
    List<SubDomain> queryRelayDomainsByKeyword(String keyword);

    /**
     * 当前商城新增绑定域名
     * @param id 新增请求
     */
    void doCreate(Long id);

    /**
     * 删除当前删除绑定的域名
     * @param ids 删除的ID列表
     */
    void doDeleteAll(List<Long> ids);

    /**
     * 解除所有绑定该删除的绑定
     * @param websiteId 商城ID
     */
    void deleteAllBindInWebsite(Long websiteId);

    Optional<SubDomainDto> findRelayDomainByFullName(String cnameRecord);

    /**
     * 绑定主题
     *
     * @param id SubDomain id
     * @param themeId 主题ID
     */
    void bindTheme(Long id, Long themeId);

    /**
     * 绑定像素账号到二级域名
     * @param request 绑定请求
     */
    void bindPixels(BindPixelsRequest request);

    /**
     * 清楚设置了列表内主题的域名的主题设置
     * @param themeIds 主题ID列表
     */
    void clearDomainThemes(List<Long> themeIds);

    /**
     * 绑定SPU到二级域名
     * @param subDomainId 子域名ID
     * @param spuId SPU ID
     */
    void bindSpu(Long subDomainId, Long spuId);

    /**
     * 解绑SPU与二级域名
     * @param subDomainId 子域名ID
     * @param spuId SPU ID
     */
    void unbindSpu(Long subDomainId, Long spuId);

    /**
     * 获取二级域名绑定的SPU列表
     * @param subDomainId 子域名ID
     * @param keyword 搜索关键字（匹配name、id、code）
     * @return SPU列表
     */
    List<Spu> getBoundSpus(Long subDomainId, String keyword);

    /**
     * 获取子域名绑定的SPU详情
     * @param subDomainId 子域名ID
     * @param spuId SPU ID
     * @return SPU详情响应
     */
    SubDomainSpuDetailResponse getBoundSpuDetail(Long subDomainId, Long spuId);

    /**
     * 绑定像素到子域名SPU
     * @param subDomainId 子域名ID
     * @param spuId SPU ID
     * @param pixelId 像素账号ID
     */
    void bindSpuPixel(Long subDomainId, Long spuId, Long pixelId);

    /**
     * 解绑像素与子域名SPU
     * @param subDomainId 子域名ID
     * @param spuId SPU ID
     * @param pixelId 像素账号ID
     */
    void unbindSpuPixel(Long subDomainId, Long spuId, Long pixelId);

    /**
     * 绑定落地页SPU到子域名SPU（仅支持 CLOAK 类型）
     * @param subDomainId 子域名ID
     * @param spuId SPU ID
     * @param landingSpuId 落地页显示的SPU ID
     * @param landingPageType 落地页类型（仅支持 CLOAK）
     */
    void bindLandingPageSpu(Long subDomainId, Long spuId, Long landingSpuId, cn.v7soft.dao.enums.LandingPageType landingPageType);

    /**
     * 解绑落地页SPU（仅支持 CLOAK 类型）
     * @param subDomainId 子域名ID
     * @param spuId SPU ID
     * @param landingPageType 落地页类型（仅支持 CLOAK）
     */
    void unbindLandingPageSpu(Long subDomainId, Long spuId, cn.v7soft.dao.enums.LandingPageType landingPageType);

    /**
     * 绑定协议到落地页
     * @param request 绑定协议请求
     */
    void bindLandingPageProtocol(cn.v7soft.admin.controller.req.BindLandingPageProtocolRequest request);
}
