package cn.v7soft.dao.properties;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.Folder;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.enums.MediaType;
import cn.v7soft.dao.tenant.TenantContext;
import cn.v7soft.dao.utils.SaSessionUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "application.multimedia")
public class MultimediaFileProperty {
    /**
     * 本地临时存储路径，docker中需要挂载到主机，以免重启后图片丢失
     * 图片上传会先传到本地路径
     * 然后再后台线程上传到s3服务器。提升传输时速度。
     */
    private String localPath;
    /**
     * 最大允许上传的图片大小，单位MB
     */
    private int maxImageSize = 20;
    /**
     * 最大允许上传的视频大小，单位MB
     */
    private int maxVideoSize = 20;
    /**
     * 最大允许上传的音频大小，单位MB
     */
    private int maxAudioSize = 20;
    /**
     * 支持的图片后缀格式
     */
    private List<String> imagesSuffixes = new ArrayList<>();
    /**
     * 支持的视频后缀格式
     */
    private List<String> videoSuffixes = new ArrayList<>();
    /**
     * 支持的音频后缀格式
     */
    private List<String> audioSuffixes = new ArrayList<>();

    /**
     * 默认支持的图片宽,必须按从小到大顺序
     */
    private List<Integer> supportWidths = new ArrayList<>();

    /**
     * 资源相对路径：
     * 公司ID/资源类型/yyyy/MM/dd/文件名UUID.后缀
     */
    public static String makeRelativePath(MediaType mediaType, String tempFileName,
                                          LocalDateTime createTime, String suffix) {
        String year = LocalDateTimeUtil.format(createTime, "yyyy");
        String month = LocalDateTimeUtil.format(createTime, "MM");
        String day = LocalDateTimeUtil.format(createTime, "dd");
        return mediaType.name() + "/" + year + "/" + month + "/" + day + "/" + tempFileName + "." + suffix;
    }


    public String getLocalUploadPath(MediaType mediaType, Folder folder, LocalDateTime createTime) {
        SystemUserDto user = SaSessionUtil.getLoginUser();
        String basePath = TenantContext.getCurrentTenant() + "/" + user.getId() + "/" + mediaType + "/";
        String path = basePath + (folder == null ? "root/" : (folder.getId() + "/")) + DateUtil.format(
                createTime, "yyyyMMdd") + "/";
        return localPath + path;
    }

    public String getCurrentUploadPath(MediaType mediaType, Folder folder,
                                       LocalDateTime createTime) {
        SystemUserDto user = SaSessionUtil.getLoginUser();
        String basePath = TenantContext.getCurrentTenant() + "/" + user.getId() + "/" + mediaType + "/";
        return basePath + (folder == null ? "root/" : (folder.getId() + "/")) + DateUtil.format(
                createTime, "yyyyMMdd") + "/";
    }

    public String getCurrentOriginalUploadPath(MediaType mediaType, Folder folder,
                                               LocalDateTime createTime) {
        return getOriginalPrefix() + getCurrentUploadPath(mediaType, folder, createTime);
    }

    public String getOriginalName(MultimediaFile multimediaFile) {
        return getOriginalPrefix() + multimediaFile.getRelativePath() + "." + multimediaFile.getSuffix();
    }

    public String getProcessedName(MultimediaFile multimediaFile, boolean recommendedSuffix) {
        String suffix = multimediaFile.getSuffix();
        if (recommendedSuffix) {
            if (multimediaFile.getMediaType() == MediaType.IMAGE) {
                suffix = "webp";
            }
        }
        return getProcessedPrefix() + multimediaFile.getRelativePath() + "." + suffix;
    }

    public static String getOriginalPrefix() {
        return "original/";
    }

    public static String getProcessedPrefix() {
        return "processed/";
    }
}
