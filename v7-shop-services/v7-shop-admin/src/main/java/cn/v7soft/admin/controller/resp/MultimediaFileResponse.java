package cn.v7soft.admin.controller.resp;

import cn.v7soft.admin.utils.MultimediaUtil;
import cn.v7soft.common.controller.resp.DataRangeResponse;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.enums.MediaType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.EntityNotFoundException;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
@SuperBuilder
@Schema(description = "多媒体文件信息响应")
public class MultimediaFileResponse extends DataRangeResponse {

    @Schema(title = "文件名", example = "example.jpg")
    private String name;

    @Schema(title = "后缀", example = "jpg")
    private String suffix;

    @Schema(title = "资源类型", example = "IMAGE")
    private MediaType mediaType;

    @Schema(title = "相对路径", example = "IMAGE")
    private String relativePath;

    @Schema(title = "相对路径", example = "IMAGE")
    private String absolutionPath;

    public static MultimediaFileResponse convertEntity(MultimediaFile entity) {
        if (entity == null) {
            return null;
        }
        try {
            String absolutionPath1 = MultimediaUtil.resolveAbsolutionPath(entity.getId());
            return filling(entity, MultimediaFileResponse.builder()
                    .name(entity.getName())
                    .suffix(entity.getSuffix())
                    .mediaType(entity.getMediaType())
                    .relativePath(entity.getRelativePath())
                    .absolutionPath(absolutionPath1)
                    .build());
        } catch (EntityNotFoundException e) {
//            log.warn("multimedia file not found: {}", e.getMessage());
            return null;
        }
    }
}
