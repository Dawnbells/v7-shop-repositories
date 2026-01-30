package cn.v7soft.admin.service.impl;

import java.io.InputStream;
import java.net.URI;

import org.springframework.stereotype.Service;

import cn.v7soft.admin.service.IS3Service;
import cn.v7soft.core.enums.ServiceResponseEnum;
import cn.v7soft.dao.properties.S3Property;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
public class S3Service implements IS3Service {

    private final S3Property s3Property;

    private final S3Client s3Client;

    public S3Service(S3Property s3Property) {
        this.s3Property = s3Property;
        String accessKeyId = s3Property.getAccessKeyId();
        String secretAccessKey = s3Property.getAccessKeySecret();
        String region = s3Property.getRegion();
        String endpoint = s3Property.getEndpoint();
        AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);

        // 创建 S3 客户端，使用 Backblaze B2 的 Endpoint
        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
                .build();
    }

    // 上传文件到 B2
    @Override
    public void upload(byte[] imageData, String key) {
        String bucketName = s3Property.getBucketName();
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType("image/webp")
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(imageData));
        } catch (S3Exception e) {
            e.printStackTrace();
            ServiceResponseEnum.ERR_S3_CONFIG.throwException();
        }
    }

    @Override
    public boolean upload(InputStream inputStream, String key, String contentType) {
        String bucketName = s3Property.getBucketName();
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, inputStream.available()));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void uploadExcel(byte[] data, String key) {
        String bucketName = s3Property.getBucketName();
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(data));
        } catch (S3Exception e) {
            e.printStackTrace();
            ServiceResponseEnum.ERR_S3_CONFIG.throwException();
        }
    }

    @Override
    public InputStream download(String key) {
        String bucketName = s3Property.getBucketName();
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        return s3Client.getObject(getObjectRequest);
    }
}
