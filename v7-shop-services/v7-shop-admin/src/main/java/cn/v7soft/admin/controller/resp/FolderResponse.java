package cn.v7soft.admin.controller.resp;

import cn.v7soft.core.controller.response.IdResponse;
import cn.v7soft.dao.entities.primary.Folder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Getter
@Setter
@SuperBuilder
@Schema(description = "文件夹信息响应")
public class FolderResponse extends IdResponse {
    @Schema(title = "文件夹名称", example = "Documents")
    private String name;

    @Schema(title = "是否是敏感路径", example = "false")
    private boolean isSensitive;

    @Schema(title = "子目录", example = "company/documents")
    private List<FolderResponse> children;

    @Schema(title = "父ID", example = "company/documents")
    private Long parentId;

    public static FolderResponse convertEntity(Folder entity) {
        return filling(entity, FolderResponse.builder()
                .name(entity.getName())
                .isSensitive(entity.isSensitive())
                .build());
    }
}
