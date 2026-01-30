package cn.v7soft.dao.entities.primary;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.SQLRestriction;

import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_protocol")
public class Protocol extends BaseDataRangeEntity {

    /**
     * 协议名称
     */
    @Column(name = "name")
    private String name;
    /**
     * 默认语言
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "default_language_id", referencedColumnName = "id")
    private Language defaultLanguage;

    /** 多语言版本列表 */
    @Builder.Default
    @OneToMany(mappedBy = "protocol", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ProtocolTranslation> translations = new ArrayList<>();

    public void addTranslation(ProtocolTranslation translation) {
        translations.add(translation);
        translation.setProtocol(this);
    }

    public void removeTranslation(ProtocolTranslation translation) {
        translations.remove(translation);
        translation.setProtocol(null);
    }
}

