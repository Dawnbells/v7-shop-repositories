package cn.v7soft.admin.task.provider;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.admin.task.AiAccountTranslateSubTaskType;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.enums.TranslationContentType;

/**
 * Provider 间共享的纯工具方法集合。
 * <p>
 * 抽出原本在 GeminiOfficialProvider / GeminiOfficialBatchProvider / TurboFlowBridgeProvider
 * 各自重复定义的 mapContentType / safeInt / readImageBytes / toMimeType。
 * 不引入抽象基类以保留 Provider 之间的实现自由度。
 */
public final class TranslateProviderSupport {

    private TranslateProviderSupport() {
    }

    public static TranslationContentType mapContentType(AiAccountTranslateSubTaskType type) {
        return switch (type) {
            case TEXT -> TranslationContentType.TEXT;
            case HTML -> TranslationContentType.HTML;
            case IMAGE -> TranslationContentType.IMAGE;
        };
    }

    public static int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    /**
     * 读取多媒体文件的原图字节。统一通过 IMultimediaFileService.download(id, 0) 读全图。
     */
    public static byte[] readImageBytes(IMultimediaFileService multimediaFileService, MultimediaFile file)
            throws Exception {
        try (InputStream in = multimediaFileService.download(String.valueOf(file.getId()), 0);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toByteArray();
        }
    }

    /**
     * 根据图片扩展名返回 MIME。仅识别白名单格式，未知扩展名兜底为 image/png。
     * 此前 TurboFlowBridgeProvider 直接拼 "image/${suffix}"，遇到非主流扩展名会得到无效 MIME；
     * 现统一为白名单实现，行为更安全。
     */
    public static String toMimeType(String suffix) {
        if (suffix == null || suffix.isBlank()) {
            return "image/png";
        }
        return switch (suffix.toLowerCase()) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            default -> "image/png";
        };
    }
}
