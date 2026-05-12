package cn.v7soft.admin.service;

import java.awt.image.BufferedImage;

public interface IImageProcessingService {
    /**
     * 将 BufferedImage 转换为 WebP 并返回字节数组
     *
     * @param image BufferedImage
     * @return WebP
     */
    byte[] convertToWebP(BufferedImage image);

    /**
     * 缩放图片并返回 BufferedImage
     *
     * @param inputImage 图片
     * @param width      宽度
     * @return 缩放的图片
     */
    BufferedImage resizeImage(BufferedImage inputImage, int width);
}
