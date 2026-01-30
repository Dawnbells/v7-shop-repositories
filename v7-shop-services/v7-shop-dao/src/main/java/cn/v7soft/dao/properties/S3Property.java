package cn.v7soft.dao.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "application.s3")
public class S3Property {
    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String region;
    private String bucketName;
}
