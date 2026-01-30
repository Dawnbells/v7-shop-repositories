package cn.v7soft.admin.service;

import cn.v7soft.core.service.IBaseService;
import cn.v7soft.dao.entities.primary.FrontServer;

import java.util.List;

public interface IFrontServerService extends IBaseService<FrontServer> {

    /**
     * 根据服务器名称获取服务器列表
     *
     * @param name 服务器名称
     * @return 服务器列表
     */
    FrontServer getFrontServersByName(String name);

    /**
     * 获取当前有效域名解析数量大于指定值的服务器
     *
     * @param minActiveResolutionCount 最小有效解析数量
     * @return 服务器列表
     */
    List<FrontServer> getServersByActiveResolutionCount(int minActiveResolutionCount);

    /**
     * 选择下一个服务器
     * @return
     */
    FrontServer chooseNext();

    /**
     * 提交并更新
     * @param id ID
     */
    void pushAndRefresh(Long id);

    /**
     * 获取所有前端服务器
      * @return 所有前端服务器
     */
    List<FrontServer> listFrontServers();
}
