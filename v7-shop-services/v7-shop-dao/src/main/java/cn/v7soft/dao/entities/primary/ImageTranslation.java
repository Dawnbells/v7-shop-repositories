package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_image_translations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_source_language",
                columnNames = {"source_file_id", "language_id"}),
        indexes = {
                @Index(name = "idx_it_source_file", columnList = "source_file_id"),
                @Index(name = "idx_it_language", columnList = "language_id")
        })
public class ImageTranslation extends BaseDataRangeEntity {

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
