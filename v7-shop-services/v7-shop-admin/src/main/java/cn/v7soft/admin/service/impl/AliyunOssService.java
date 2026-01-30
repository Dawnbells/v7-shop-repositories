package cn.v7soft.admin.service.impl;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;
import com.aliyun.green20220302.Client;
import com.aliyun.green20220302.models.TextModerationRequest;
import com.aliyun.green20220302.models.TextModerationResponse;
import com.aliyun.green20220302.models.TextModerationResponseBody;
import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;

import cn.hutool.core.lang.Singleton;
import cn.hutool.core.lang.func.Func0;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.v7soft.admin.service.IAliyunOssService;
import cn.v7soft.admin.service.dto.TextModerationData;
import cn.v7soft.core.enums.ServiceResponseEnum;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.enums.MediaState;
import cn.v7soft.dao.enums.MediaType;
import cn.v7soft.dao.properties.AliyunOssProperty;
import cn.v7soft.dao.properties.MultimediaFileProperty;
import cn.v7soft.dao.tenant.TenantContext;
import jakarta.annotation.PreDestroy;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@AllArgsConstructor
public class AliyunOssService implements IAliyunOssService {

    private final MultimediaFileProperty multimediaFileProperty;
    private final AliyunOssProperty aliyunOssProperty;

    @Override
    public InputStream download(MultimediaFile multimediaFile, Integer width) {
        String tenantId = TenantContext.getCurrentTenantStr();
        OSS ossClient = getOssClient(tenantId);
        String bucketName = getOssBucketName(tenantId, multimediaFile.getId());

        String objectName = multimediaFileProperty.getOriginalName(multimediaFile);
        String process = null;
        if (multimediaFile.getMediaState() == MediaState.ERROR || multimediaFile.getMediaState() == MediaState.UPLOADED) {
            // 同步失败或者还没同步
            return getOssInputStream(ossClient, bucketName, objectName, false, process);
        }

        objectName = multimediaFileProperty.getProcessedName(multimediaFile, true);
        if (multimediaFile.getMediaState() == MediaState.ENCRYPTED) {
            // 加密数据
            return getOssInputStream(ossClient, bucketName, objectName, true, process);
        }

        objectName = multimediaFileProperty.getProcessedName(multimediaFile, false);
        if (width != null && multimediaFile.getMediaType() == MediaType.IMAGE) {
            // 图片进行缩放和webp格式转换
            process = "image/resize,m_lfit,w_" + width + ",limit_0";
            if (!multimediaFile.isWebp()) {
                process += "/format,webp";
            }
        }
        return getOssInputStream(ossClient, bucketName, objectName, false, process);
    }

    @Override
    public boolean uploadMultimediaFile(InputStream inputStream, String fileName) {
        try {
            String bucketName = getOssBucketName(TenantContext.getCurrentTenantStr(), 0L);
            // 创建PutObjectRequest对象。
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, fileName, inputStream);
            OSS ossClient = getOssClient(TenantContext.getCurrentTenantStr());
            PutObjectResult putObjectResult = ossClient.putObject(putObjectRequest);
            return putObjectResult != null;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public TextModerationData detectText(String tenantId, String text) {
        if (StrUtil.isBlank(text)) {
            return TextModerationData.builder().build();
        }
        // 创建RuntimeObject实例并设置运行参数
        RuntimeOptions runtime = new RuntimeOptions();

        // 检测参数构造。
        Map<String, String> serviceParameters = new HashMap<>();
        // 待检测文本
        serviceParameters.put("content", text);

        TextModerationRequest request = new TextModerationRequest();
        // 文本检测service：内容安全控制台文本增强版规则配置的serviceCode，示例：baselineCheck
        // 支持service请参考：https://help.aliyun.com/document_detail/467826.html?0#p-23b-o19-gff
        request.setService("comment_multilingual_pro");
        request.setServiceParameters(JSONUtil.toJsonStr(serviceParameters));

        TextModerationResponse response;
        try {
            response = getAliyunGreenClient(tenantId).textModerationWithOptions(request, runtime);
            log.debug("check text moderation response = " + JSONUtil.toJsonStr(response));
            if (response == null || response.statusCode != 200) {
                return TextModerationData.builder().build();
            }
            TextModerationResponseBody moderationResponseBody = response.getBody();
            if (moderationResponseBody == null || moderationResponseBody.code != 200 || moderationResponseBody.getData() == null) {
                return TextModerationData.builder().build();
            }
            TextModerationResponseBody.TextModerationResponseBodyData data = moderationResponseBody.getData();
            return TextModerationData.builder().detected(true).labels(data.labels).reason(data.reason).build();
        } catch (Exception e) {
            log.error("check text moderation error >> ", e);
        }
        return TextModerationData.builder().build();
    }

    /**
     * 释放OSS
     */
    @PreDestroy
    public void onShutdown() {
        getOssClient("1").shutdown();
    }

    private String getOssBucketName(String tenantId, Long multimediaId) {
        return aliyunOssProperty.getBucketName();
    }

    private com.aliyun.green20220302.Client getAliyunGreenClient(String tenantId) {
        return Singleton.get("green_client_" + tenantId, (Func0<Client>) () -> {
            String accessKeyId = aliyunOssProperty.getAccessKeyId();
            String accessKeySecret = aliyunOssProperty.getAccessKeySecret();
            String endpoint = "green-cip.ap-southeast-1.aliyuncs.com";
            Config config = new Config();
            config.setAccessKeyId(accessKeyId);
            config.setAccessKeySecret(accessKeySecret);
            config.setEndpoint(endpoint);
            return new com.aliyun.green20220302.Client(config);
        });
    }

    private OSS getOssClient(String tenantId) {
        return Singleton.get("oss_client_" + tenantId, () -> {
            ServiceResponseEnum.ERR_OSS_CONFIG.notNull(aliyunOssProperty, "请检查阿里云OSS配置: " + tenantId);
            String accessKeyId = aliyunOssProperty.getAccessKeyId();
            String accessKeySecret = aliyunOssProperty.getAccessKeySecret();
            DefaultCredentialProvider credentialsProvider = CredentialsProviderFactory.newDefaultCredentialProvider(accessKeyId, accessKeySecret);

            // 创建OSSClient实例。
            ClientBuilderConfiguration clientBuilderConfiguration = new ClientBuilderConfiguration();
            clientBuilderConfiguration.setSignatureVersion(SignVersion.V2);
            return OSSClientBuilder.create()
                    .endpoint(aliyunOssProperty.getEndpoint())
                    .credentialsProvider(credentialsProvider)
                    .clientConfiguration(clientBuilderConfiguration)
                    .region(aliyunOssProperty.getRegion())
                    .build();
        });
    }

    /**
     * 生成或获取 AES 加密密钥
     */
    private SecretKey getSecretKey() throws Exception {
        // 使用静态密钥，也可以改为动态生成
        String key = "cVozVEJtMEk0c1BlOFhMVnhGdnNvQT09"; // 16字节密钥
        return new SecretKeySpec(key.getBytes(), "AES");
    }

    private InputStream getOssInputStream(OSS ossClient, String bucketName, String objectName, boolean isEncrypted, String process) {
        try {
            GetObjectRequest request = new GetObjectRequest(bucketName, objectName);

            if (StrUtil.isNotBlank(process)) {
                request.setProcess(process);
            }

            OSSObject object = ossClient.getObject(request);
            InputStream inputStream = object.getObjectContent();

            if (!isEncrypted) {
                // 未加密
                return inputStream;
            }

            Cipher cipher = Cipher.getInstance("AES");
            SecretKey secretKey = getSecretKey(); // 获取或生成加密密钥
            cipher.init(Cipher.DECRYPT_MODE, secretKey);

            // 包装输入流
            return new CipherInputStream(inputStream, cipher);
        } catch (Exception e) {
            log.error("get oss input stream error: {}", e.getMessage());
        }
        return null;
    }
}
