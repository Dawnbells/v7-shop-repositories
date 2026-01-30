package cn.v7soft.dao.entities.meta;


import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

/**
 * 路由元数据的嵌入类，包括路由标题、图标、是否可关闭标签和是否隐藏等信息。
 */
@Embeddable
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Meta {
    /**
     * 路由标题。
     */
    @Column(nullable = false)
    private String title;

    /**
     * 路由图标。
     */
    private String icon;

    /**
     * 标签是否不可关闭，默认为false。
     */
    @Column(name = "no_closable")
    @Builder.Default
    private Boolean noClosable = false;

    /**
     * 路由是否隐藏，默认为false。
     */
    @Builder.Default
    private Boolean hidden = false;
}