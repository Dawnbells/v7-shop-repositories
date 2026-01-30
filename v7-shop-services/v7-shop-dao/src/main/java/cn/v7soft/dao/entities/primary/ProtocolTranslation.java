package cn.v7soft.dao.entities.primary;

import java.util.ArrayList;
import java.util.List;

import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import jakarta.persistence.CascadeType;
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
@Table(name = "t_protocol_translation")
public class ProtocolTranslation extends BaseDataRangeEntity {

    /**
     * 所属协议
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "protocol_id", referencedColumnName = "id")
    private Protocol protocol;

    /**
     * 所属语言
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "language_id", referencedColumnName = "id")
    private Language language;

    /**
     * 该语言下的协议文章分组
     */
    @Builder.Default
    @OneToMany(mappedBy = "translation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ProtocolArticleGroup> articleGroupList = new ArrayList<>();

    public void addArticleGroup(ProtocolArticleGroup group) {
        articleGroupList.add(group);
        group.setTranslation(this);
    }

    public void removeArticleGroup(ProtocolArticleGroup group) {
        articleGroupList.remove(group);
        group.setTranslation(null);
    }
}
