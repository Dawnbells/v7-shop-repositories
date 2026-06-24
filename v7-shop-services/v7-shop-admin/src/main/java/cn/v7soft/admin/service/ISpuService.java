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

    /**
     * 统计某员工名下可复制的 SPU 数量（VALID + INVALID，自动排除 DELETED）。
     *
     * @param ownerId 员工ID
     * @return 数量
     */
    long countSpuByOwner(Long ownerId);

    /**
     * 查询某员工名下全部可复制 SPU 的 ID 列表。
     *
     * @param ownerId 员工ID
     * @return SPU ID 列表
     */
    List<Long> findSpuIdsByOwner(Long ownerId);

    /**
     * 将单个源 SPU 深拷贝分享给目标员工；若目标员工名下已存在该来源 SPU 的有效副本则跳过。
     * 每次调用独立事务提交，供批量复制逐条调用。
     *
     * @param sourceSpuId  源 SPU ID
     * @param targetUserId 目标员工ID
     * @param targetDeptId 目标员工部门ID（用于分配新的 SPU code）
     * @return true=已复制；false=已存在跳过
     */
    boolean copySpuToTargetIfAbsent(Long sourceSpuId, Long targetUserId, Long targetDeptId);
}