package cn.v7soft.dao.entities.primary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 图片上传被内容政策拒绝后的跨语言缓存，仅按图片摘要匹配。 */
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_image_policy_cache",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_image_policy_hash",
                columnNames = "image_hash"),
        indexes = @Index(name = "idx_ipc_source_file", columnList = "source_file_id"))
public class ImagePolicyCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_hash", nullable = false, length = 64)
    private String imageHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_file_id", nullable = false)
    private MultimediaFile sourceFile;

    @Column(name = "api_status", nullable = false, length = 80)
    private String apiStatus;

    @Column(name = "reason", nullable = false, length = 160)
    private String reason;
}
