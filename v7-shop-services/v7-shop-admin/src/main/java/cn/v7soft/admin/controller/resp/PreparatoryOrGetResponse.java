package cn.v7soft.admin.controller.resp;

import cn.v7soft.admin.utils.MultimediaUtil;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.enums.MediaType;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class PreparatoryOrGetResponse {
    /**
     * ID
     */
    private String id;
    /**
     * 文件名，允许重复
     */
    private String name;

    /**
     * 后缀
     */
    private String suffix;
    /**
     * 文件大小
     */
    private long fileSize;

    /**
     * 相对路径
     */
    private String relativePath;

    /**
     * 归属文件夹，一个文件夹下允许多个文件
     */
    private String folderId;

    /**
     * bucketName
     */
    private String bucketName;
    /**
     * 资源类型
     */
    private MediaType mediaType;

    public static PreparatoryOrGetResponse convert(MultimediaFile multimediaFile) {
        String relativePath = MultimediaUtil.makeRelativePath(multimediaFile.getMediaType(), multimediaFile.getName(), multimediaFile.getSuffix(), multimediaFile.getCreateTime());
        return PreparatoryOrGetResponse.builder()
                .id(String.valueOf(multimediaFile.getId()))
                .name(multimediaFile.getName())
                .suffix(multimediaFile.getSuffix())
                .fileSize(multimediaFile.getFileSize())
                .relativePath(relativePath)
                .folderId(multimediaFile.getFolder() == null? null: String.valueOf(multimediaFile.getFolder().getId()))
                .mediaType(multimediaFile.getMediaType())
                .bucketName("v7-shop-dwd")
                .build();
    }
}
