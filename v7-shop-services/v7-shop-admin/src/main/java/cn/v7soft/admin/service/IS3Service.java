package cn.v7soft.admin.service;

import java.io.InputStream;
import java.time.Duration;

public interface IS3Service {
    /**
     * 上传
     * @param data 资源
     * @param key 名称
     */
    void upload(byte[] data, String key);

    boolean upload(InputStream inputStream, String key, String contentType);

    /**
     * 带整体时限的上传，失败直接抛异常（不吞错），调用方据此决定重试。
     * @param timeout 含 SDK 内部重试在内的整次调用时限
     */
    void upload(byte[] data, String key, String contentType, Duration timeout);

    void uploadExcel(byte[] data, String key);

    /**
     * 下载
     * @param key 名称
     * @return 资源流
     */
    InputStream download(String key);
}
