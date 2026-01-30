package cn.v7soft.admin.service;

import cn.v7soft.admin.service.dto.TextModerationData;
import cn.v7soft.dao.entities.primary.MultimediaFile;

import java.io.InputStream;

public interface IAliyunOssService {

    InputStream download(MultimediaFile multimediaFile, Integer width);

    boolean uploadMultimediaFile(InputStream inputStream, String fileName);
    /**
     * 审核文本
     *
     * @param text
     * @return
     */
    TextModerationData detectText(String tenantId, String text);
}
