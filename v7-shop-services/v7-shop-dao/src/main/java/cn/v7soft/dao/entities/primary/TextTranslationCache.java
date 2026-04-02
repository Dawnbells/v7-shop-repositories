package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.enums.TranslationContentType;
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
@Table(name = "t_text_translation_cache",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_hash_lang_type",
                columnNames = {"content_hash", "language_id", "content_type"}),
        indexes = @Index(name = "idx_ttc_hash_lang", columnList = "content_hash, language_id"))
public class TextTranslationCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "language_id", nullable = false)
    private Language language;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 10)
    private TranslationContentType contentType;

    @Column(name = "source_text", columnDefinition = "LONGTEXT")
    private String sourceText;

    @Column(name = "translated_text", columnDefinition = "LONGTEXT")
    private String translatedText;
}
