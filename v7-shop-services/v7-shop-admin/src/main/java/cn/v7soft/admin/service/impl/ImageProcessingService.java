package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.service.IImageProcessingService;
import cn.v7soft.core.enums.ServiceResponseEnum;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class ImageProcessingService  implements IImageProcessingService {

    // 缩放图片并返回 BufferedImage
    @Override
    public BufferedImage resizeImage(BufferedImage inputImage, int width)  {
        try {
            return Thumbnails.of(inputImage)
                    .width(width)
                    .asBufferedImage();
        } catch (IOException e) {
            ServiceResponseEnum.ERR_THUMBNAILS.throwException();
        }
        return null;
    }

    // 将 BufferedImage 转换为 WebP 并返回字节数组
    @Override
    public byte[] convertToWebP(BufferedImage image)  {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        // 获取 WebP 格式的 ImageWriter
        ImageWriter writer = ImageIO.getImageWritersByMIMEType("image/webp").next();

        try (MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(byteArrayOutputStream)) {
            writer.setOutput(output);
            writer.write(image);
        } catch (IOException e) {
            ServiceResponseEnum.ERR_CONVERT_WEBP.throwException();
        }

        return byteArrayOutputStream.toByteArray();
    }
}
