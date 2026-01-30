package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import cn.v7soft.dao.enums.MediaState;
import cn.v7soft.dao.enums.MediaType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

/**
 * 多媒体文件实体类，包含图片、音频、视频文件资源。
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
//@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_multimedia_files",
        indexes = {
                @Index(name = "idx_folder_id", columnList = "folder_id"),
                @Index(name = "idx_id", columnList = "id"),
                @Index(name = "idx_user_id", columnList = "user_id"),
                @Index(name = "idx_company_id", columnList = "company_id"),
        }
)
public class MultimediaFile extends BaseDataRangeEntity {
    /**
     * 文件名，允许重复
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * 宽
     */
    @Column(name = "width", nullable = false, columnDefinition = "INT DEFAULT 0")
    private int width;

    /**
     * 高度
     */
    @Column(name = "height", nullable = false, columnDefinition = "INT DEFAULT 0")
    private int height;

    /**
     * 后缀
     */
    @Column(name = "suffix", nullable = false)
    private String suffix;
    /**
     * 文件大小
     */
    @Column(name = "file_size", nullable = false)
    private long fileSize;

    /**
     * 相对路径
     */
    @Column(name = "relative_path", nullable = false)
    private String relativePath;


    /**
     * 归属文件夹，一个文件夹下允许多个文件
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private Folder folder;

    /**
     * 资源类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false)
    private MediaType mediaType;

    /**
     * 多媒体状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "media_state", nullable = false)
    private MediaState mediaState;

    public boolean isWebp() {
        return "webp".equalsIgnoreCase(suffix);
    }
}
