package cn.v7soft.admin.service;

import java.io.InputStream;
import java.util.List;

import cn.v7soft.admin.controller.resp.MultimediaFileResponse;
import cn.v7soft.common.service.IBaseDataRangeService;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import jakarta.servlet.http.HttpServletRequest;

public interface IMultimediaFileService extends IBaseDataRangeService<MultimediaFile> {
    /**
     * 下载资源
     *
     * @param id    ID
     * @param width 宽
     * @return 下载资源
     */
    InputStream download(String id, int width);

    /**
     * 上传图片
     *
     * @param folderId 文件夹ID
     */
    List<MultimediaFileResponse> uploadFiles(HttpServletRequest httpServletRequest, Long folderId);

    /**
     * 删除文件夹内所有的文件
     * @param folderId 文件夹ID
     * @return 删除行数
     */
    int deleteAllInFolder(Long folderId);
}
