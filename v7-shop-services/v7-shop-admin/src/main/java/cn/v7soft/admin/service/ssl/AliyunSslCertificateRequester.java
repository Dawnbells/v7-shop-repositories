package cn.v7soft.admin.service.ssl;

import org.jetbrains.annotations.NotNull;
import com.aliyun.alidns20150109.Client;
import com.aliyun.alidns20150109.models.AddDomainRecordResponse;
import com.aliyun.alidns20150109.models.DeleteDomainRecordResponse;
import com.aliyun.alidns20150109.models.DescribeDomainRecordsResponse;
import com.aliyun.alidns20150109.models.DescribeDomainRecordsResponseBody;
import com.aliyun.alidns20150109.models.DescribeDomainRecordsResponseBody.DescribeDomainRecordsResponseBodyDomainRecordsRecord;
import com.aliyun.alidns20150109.models.UpdateDomainRecordResponse;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.v7soft.dao.entities.primary.CloudPlatformAccount;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Builder
public class AliyunSslCertificateRequester extends BaseSslCertificateRequester {

    @Override
    protected String name() {
        return "aliyun";
    }

    @NotNull
    @Override
    protected String getIniContent(CloudPlatformAccount cloudPlatformAccount) {
        return "dns_aliyun_access_key = " + cloudPlatformAccount.getAccessKey() + "\n" +
               "dns_aliyun_access_key_secret = " + cloudPlatformAccount.getAccessKeySecret();
    }

    @Override
    public boolean analyzeDomain(TopLevelDomain topLevelDomain, String subName, String cnameRecord) {
        try {
            Client client = this.createClient(topLevelDomain.getCloudPlatformAccount());
            DescribeDomainRecordsResponse describeDomainRecordsResponse = describeDomainRecords(client, topLevelDomain.getName());
            log.debug("describeDomainRecordsResponse = " + JSONUtil.toJsonStr(describeDomainRecordsResponse));
            DescribeDomainRecordsResponseBody body = describeDomainRecordsResponse.getBody();
            DescribeDomainRecordsResponseBodyDomainRecordsRecord wildcardRecord = null; // 通配匹配
            DescribeDomainRecordsResponseBodyDomainRecordsRecord exactRecord = null; // 精确匹配
            if (body.getTotalCount() > 0) {
                for (DescribeDomainRecordsResponseBodyDomainRecordsRecord record : body.getDomainRecords().getRecord()) {
                    if ("*".equals(record.getRR())) {
                        wildcardRecord = record;
                    } else if (subName.equalsIgnoreCase(record.getRR())) {
                        exactRecord = record;
                    }
                }
                if (wildcardRecord != null && "CNAME".equalsIgnoreCase(wildcardRecord.getType()) && cnameRecord.equals(wildcardRecord.getValue())) {
                    // 通配匹配
                    if (exactRecord == null) {
                        return true;
                    }
                    // 删除精确匹配。
                    DeleteDomainRecordResponse deleteDomainRecordResponse = deleteDomainRecordWithOptions(client, exactRecord);
                    return deleteDomainRecordResponse.getBody() != null && StrUtil.isNotBlank(deleteDomainRecordResponse.getBody().getRecordId());
                }
                if (exactRecord != null && "CNAME".equalsIgnoreCase(exactRecord.getType()) && cnameRecord.equals(exactRecord.getValue())) {
                    // 精确匹配已经解析正确，无需操作
                    return true;
                }
                if (exactRecord != null) {
                    // 精确匹配和通配都不匹配，更新精确匹配
                    UpdateDomainRecordResponse updateDomainRecordResponse = updateDomainRecordWithOptions(client, exactRecord, cnameRecord);
                    return updateDomainRecordResponse.getBody() != null && StrUtil.isNotBlank(updateDomainRecordResponse.getBody().getRecordId());
                }
            }
            // 不存在精确匹配，新增记录，如果已存在通配，则新增精确匹配，如果不存在通配，新增通配记录
            AddDomainRecordResponse addDomainRecordResponse = addDomainRecordWithOptions(client, topLevelDomain.getName(), subName, cnameRecord, wildcardRecord != null);
            return addDomainRecordResponse.getBody() != null && StrUtil.isNotBlank(addDomainRecordResponse.getBody().getRecordId());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private AddDomainRecordResponse addDomainRecordWithOptions(Client client, String domainName, String subName, String cnameRecord, boolean hasWildcard) throws Exception {
        log.debug("添加域名记录(" + (hasWildcard ? subName : "*") + "." + domainName + "): " + cnameRecord);
        com.aliyun.alidns20150109.models.AddDomainRecordRequest addDomainRecordRequest = new com.aliyun.alidns20150109.models.AddDomainRecordRequest()
                .setDomainName(domainName)
                .setRR(hasWildcard ? subName : "*") // 已经有通配记录添加精确记录，否则添加通配记录
                .setType("CNAME")
                .setValue(cnameRecord);
        com.aliyun.teautil.models.RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();
        return client.addDomainRecordWithOptions(addDomainRecordRequest, runtime);
    }

    private UpdateDomainRecordResponse updateDomainRecordWithOptions(Client client, DescribeDomainRecordsResponseBodyDomainRecordsRecord exactRecord, String cnameRecord) throws Exception {
        log.debug("更新域名解析记录(" + exactRecord.getRR() + "." + exactRecord.getDomainName() + "): " + exactRecord.getValue() + " -> " + cnameRecord);
        com.aliyun.alidns20150109.models.UpdateDomainRecordRequest updateDomainRecordRequest = new com.aliyun.alidns20150109.models.UpdateDomainRecordRequest()
                .setRecordId(exactRecord.getRecordId())
                .setRR(exactRecord.getRR())
                .setType("CNAME")
                .setValue(cnameRecord);
        com.aliyun.teautil.models.RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();
        // 复制代码运行请自行打印 API 的返回值
        return client.updateDomainRecordWithOptions(updateDomainRecordRequest, runtime);
    }

    private DeleteDomainRecordResponse deleteDomainRecordWithOptions(Client client, DescribeDomainRecordsResponseBodyDomainRecordsRecord exactRecord) throws Exception {
        log.debug("删除域名解析记录(" + exactRecord.getRR() + "." + exactRecord.getDomainName() + ")");
        com.aliyun.alidns20150109.models.DeleteDomainRecordRequest deleteDomainRecordRequest = new com.aliyun.alidns20150109.models.DeleteDomainRecordRequest()
                .setRecordId(exactRecord.getRecordId());
        com.aliyun.teautil.models.RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();
        return client.deleteDomainRecordWithOptions(deleteDomainRecordRequest, runtime);
    }

    private DescribeDomainRecordsResponse describeDomainRecords(Client client, String domainName) throws Exception {
        log.debug("查询域名解析记录列表: " + domainName);
        com.aliyun.alidns20150109.models.DescribeDomainRecordsRequest describeDomainRecordsRequest = new com.aliyun.alidns20150109.models.DescribeDomainRecordsRequest()
                .setDomainName(domainName);
        com.aliyun.teautil.models.RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();
        return client.describeDomainRecordsWithOptions(describeDomainRecordsRequest, runtime);
    }

    private Client createClient(CloudPlatformAccount cloudPlatformAccount) throws Exception {
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
                .setAccessKeyId(cloudPlatformAccount.getAccessKey())
                .setAccessKeySecret(cloudPlatformAccount.getAccessKeySecret());
        config.endpoint = cloudPlatformAccount.getEndpoint();
        return new com.aliyun.alidns20150109.Client(config);
    }
}
