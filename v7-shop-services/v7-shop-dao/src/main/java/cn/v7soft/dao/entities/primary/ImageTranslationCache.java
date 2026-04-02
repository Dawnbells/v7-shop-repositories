package cn.v7soft.dao.entities.primary;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_image_translation_cache",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_image_hash_language",
                columnNames = {"image_hash", "language_id"}),
        indexes = {
                @Index(name = "idx_itc_source_file", columnList = "source_file_id"),
                @Index(name = "idx_itc_language", columnList = "language_id"),
                @Index(name = "idx_itc_image_hash", columnList = "image_hash")
        })
public class ImageTranslationCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_hash", nullable = false, length = 64)
    private String imageHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_file_id", nullable = false)
    private MultimediaFile sourceFile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "language_id", nullable = false)
    private Language language;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "translated_file_id")
    private MultimediaFile translatedFile;

    @Column(name = "skipped", nullable = false)
    private boolean skipped;
}
