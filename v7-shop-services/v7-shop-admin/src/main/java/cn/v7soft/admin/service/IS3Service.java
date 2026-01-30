package cn.v7soft.admin.service;

import java.io.InputStream;

public interface IS3Service {
    /**
     * 上传
     * @param data 资源
     * @param key 名称
     */
    void upload(byte[] data, String key);

    boolean upload(InputStream inputStream, String key, String contentType);

    void uploadExcel(byte[] data, String key);

    /**
     * 下载
     * @param key 名称
     * @return 资源流
     */
    InputStream download(String key);
}
