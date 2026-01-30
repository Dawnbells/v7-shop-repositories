package cn.v7soft.admin.service;

import java.util.List;

import org.springframework.data.domain.Page;

import cn.v7soft.admin.controller.req.EditIpBlacklistRequest;
import cn.v7soft.admin.controller.resp.IpBlacklistResponse;
import cn.v7soft.common.service.IBaseDataRangeService;
import cn.v7soft.dao.entities.primary.IpBlacklist;

public interface IIpBlacklistService extends IBaseDataRangeService<IpBlacklist> {
    /**
     * 从远程cloak服务搜索IP黑名单
     * @param query 搜索关键词（IP地址或指纹的模糊匹配）
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 分页结果
     */
    Page<IpBlacklistResponse> searchFromRemote(String query, int page, int size);

    /**
     * 创建IP黑名单记录到远程服务
     * @param request 创建请求
     * @return 创建的记录
     */
    IpBlacklistResponse createRemote(EditIpBlacklistRequest request);

    /**
     * 更新远程IP黑名单记录
     * @param id 记录ID
     * @param request 更新请求
     * @return 更新后的记录
     */
    IpBlacklistResponse updateRemote(Long id, EditIpBlacklistRequest request);

    /**
     * 删除远程IP黑名单记录
     * @param ids 记录ID列表
     */
    void deleteRemote(List<Long> ids);
}
