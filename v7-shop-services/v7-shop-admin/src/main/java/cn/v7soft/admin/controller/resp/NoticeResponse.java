package cn.v7soft.admin.controller.resp;

import cn.v7soft.dao.entities.primary.Notice;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@Schema(description = "通知响应")
public class NoticeResponse {

    @Schema(title = "通知ID")
    private Long id;

    @Schema(title = "标题")
    private String title;

    @Schema(title = "内容")
    private String content;

    @Schema(title = "通知类型")
    private String type;

    @Schema(title = "是否已读")
    private Boolean isRead;

    @Schema(title = "创建时间")
    private LocalDateTime createTime;

    /**
     * 兼容前端 VabNotice 组件的 notice 字段
     */
    @Schema(title = "通知文本（兼容前端）")
    private String notice;

    public static NoticeResponse convertEntity(Notice entity) {
        return NoticeResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .content(entity.getContent())
                .type(entity.getType())
                .isRead(entity.getIsRead())
                .createTime(entity.getCreateTime())
                .notice(entity.getTitle() + (entity.getContent() != null ? "：" + entity.getContent() : ""))
                .build();
    }
}
