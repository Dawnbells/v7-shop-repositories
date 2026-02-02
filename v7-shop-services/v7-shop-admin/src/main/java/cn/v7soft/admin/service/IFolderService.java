package cn.v7soft.admin.service;

import cn.v7soft.admin.controller.req.EditFolderRequest;
import cn.v7soft.common.service.IBaseDataRangeService;
import cn.v7soft.core.controller.request.DeleteRequest;
import cn.v7soft.dao.entities.primary.Folder;

import java.util.List;

public interface IFolderService extends IBaseDataRangeService<Folder> {
    /**
     * 获取所有顶层文件夹
     * @param query 可选的文件夹名称搜索关键字
     */
    List<Folder> treeAllTopFolders(String query);

    /**
     * 创建文件夹
     * @param request 创建文件夹请求
     */
    void mkdir(EditFolderRequest request);

    /**
     * 重命名
     * @param request 重命名文件夹请求
     */
    void rename(EditFolderRequest request);

    /**
     * 删除文件夹
     * @param request 删除参数
     */
    void delete(DeleteRequest request);
}
