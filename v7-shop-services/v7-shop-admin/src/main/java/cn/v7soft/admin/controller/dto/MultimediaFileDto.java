package cn.v7soft.admin.controller.dto;

import cn.v7soft.dao.dto.IdDto;
import cn.v7soft.dao.entities.primary.Folder;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.enums.MediaType;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Setter
@Getter
@SuperBuilder
public class MultimediaFileDto extends IdDto {
    /**
     * 文件名，允许重复
     */
    private String name;
    /**
     * 资源类型
     */
    private MediaType mediaType;

    private String suffix;

    private String relativePath;
    private boolean sensitive;
    public static MultimediaFileDto convert(MultimediaFile multimediaFile) {
        if (multimediaFile == null) {
            return null;
        }
        Folder folder = multimediaFile.getFolder();
        boolean sensitive = folder != null && folder.isSensitive();
        return builder()
                .id(String.valueOf(multimediaFile.getId()))
                .name(multimediaFile.getName())
                .suffix(multimediaFile.getSuffix())
                .relativePath(multimediaFile.getRelativePath())
                .sensitive(sensitive)
                .mediaType(multimediaFile.getMediaType()).build();
    }
}
