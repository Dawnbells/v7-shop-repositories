package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;


@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@SQLRestriction("status <> 'DELETED'")
@Table(
        name = "t_s3_configuration",
        indexes = {
                @Index(name = "idx_company_id", columnList = "company_id")
        }
)
public class S3Configuration extends BaseTenantEntity {
    @Column(name = "access_key_id", nullable = false, length = 100)
    private String accessKeyId;

    @Column(name = "secret_access_key", nullable = false, length = 100)
    private String secretAccessKey;

    @Column(name = "bucket_name", nullable = false, length = 100)
    private String bucketName;

    @Column(name = "region", nullable = false, length = 50)
    private String region;

    @Column(name = "endpoint_url", length = 512)
    private String endpointUrl;
}
