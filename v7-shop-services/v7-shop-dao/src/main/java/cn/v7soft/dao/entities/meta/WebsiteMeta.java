package cn.v7soft.dao.entities.meta;

import cn.v7soft.dao.entities.primary.MultimediaFile;
import jakarta.persistence.*;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebsiteMeta {
    /**
     * 底部版权信息
     */
    @Column(name = "footer_copyright_info")
    private String footerCopyrightInfo;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "logo_file_id")
    private MultimediaFile logoFile;
}
