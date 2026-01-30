package cn.v7soft.accountservice.controller.resp;

import cn.v7soft.dao.entities.primary.SystemRouter;
import cn.v7soft.dao.entities.meta.Meta;
import cn.v7soft.dao.enums.RouterPlatform;
import cn.v7soft.dao.enums.SystemRouterType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 权限树请求
 *
 * @Author jiangjt
 * @Date 2023/4/22 0:41
 * @Version 1.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class SystemRouterResponse {

    @Schema(title = "权限ID", example = "id1")
    private Long id;

    @Schema(title = "路径", example = "/managements")
    private String path;

    @Schema(title = "名称", example = "Managements")
    private String name;

    @Schema(title = "组件路径", example = "Layout")
    private String component;

    @Schema(title = "文本内容")
    private Meta meta;

    /**
     * 路由所属平台
     */
    @Column(length = 32)
    @Enumerated(EnumType.STRING)
    private RouterPlatform platform;

    @Schema(title = "路由类型")
    private SystemRouterType type;

    @Schema(title = "上级菜单", example = "1")
    private Long parentId;

    @Schema(title = "子菜单")
    private List<SystemRouterResponse> children;

    public static SystemRouterResponse fromSystemRouter(SystemRouter systemRouter, SystemRouter parent) {
        return SystemRouterResponse.builder()
                .id(systemRouter.getId())
                .path(systemRouter.getPath())
                .name(systemRouter.getName())
                .component(systemRouter.getComponent())
                .meta(systemRouter.getMeta())
                .type(systemRouter.getType())
                .platform(systemRouter.getPlatform())
                .parentId(parent == null? null : parent.getId())
                .children(systemRouter.getChildren().stream().map(s->fromSystemRouter(s, systemRouter)).collect(Collectors.toList()))
                .build();
    }
}
