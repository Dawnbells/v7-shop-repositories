package cn.v7soft.admin.service.dns.impl;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import com.aliyun.alidns20150109.Client;
import com.aliyun.alidns20150109.models.AddDomainRecordResponse;
import com.aliyun.alidns20150109.models.DeleteDomainRecordResponse;
import com.aliyun.alidns20150109.models.DescribeDomainRecordsResponse;
import com.aliyun.alidns20150109.models.DescribeDomainRecordsResponseBody;
import com.aliyun.alidns20150109.models.UpdateDomainRecordResponse;
import com.aliyun.domain20180129.models.QueryDomainByDomainNameRequest;
import com.aliyun.domain20180129.models.QueryDomainByDomainNameResponse;

import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.service.dns.IDnsService;
import cn.v7soft.admin.utils.IpUtils;
import cn.v7soft.dao.entities.primary.CloudPlatformAccount;
import cn.v7soft.dao.enums.CloudPlatform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AliyunDnsService implements IDnsService {

    @Override
    public CloudPlatform getPlatform() {
        return CloudPlatform.ALIYUN;
    }

    @Override
    public boolean updateRecord(CloudPlatformAccount cloudPlatformAccount, String domainName, String subName, String recordValue) {
        try{
            String type = IpUtils.isIp(recordValue)? "A": "CNAME";

            Client client = this.createClient(cloudPlatformAccount);
            DescribeDomainRecordsResponse describeDomainRecordsResponse = describeDomainRecords(client, domainName);
            DescribeDomainRecordsResponseBody body = describeDomainRecordsResponse.getBody();
            DescribeDomainRecordsResponseBody.DescribeDomainRecordsResponseBodyDomainRecordsRecord wildcardRecord = null; // 通配匹配
            DescribeDomainRecordsResponseBody.DescribeDomainRecordsResponseBodyDomainRecordsRecord exactRecord = null; // 精确匹配
            if (body.getTotalCount() > 0) {
                for (DescribeDomainRecordsResponseBody.DescribeDomainRecordsResponseBodyDomainRecordsRecord record : body.getDomainRecords().getRecord()) {
                    if ("*".equals(record.getRR())) {
                        wildcardRecord = record;
                    } else if (subName.equalsIgnoreCase(record.getRR())) {
                        exactRecord = record;
                    }
                }
                if (wildcardRecord != null && "A".equalsIgnoreCase(wildcardRecord.getType()) && recordValue.equals(wildcardRecord.getValue())) {
                    // 通配匹配
                    if (exactRecord == null) {
                        return false;
                    }
                    // 删除精确匹配。
                    DeleteDomainRecordResponse deleteDomainRecordResponse = deleteDomainRecordWithOptions(client, exactRecord);
                    if (deleteDomainRecordResponse.getBody() != null && org.apache.commons.lang3.StringUtils.isNotBlank(deleteDomainRecordResponse.getBody().getRecordId())) {
                        return true;
                    }
                }
                if (exactRecord != null) {
                    if (recordValue.equals(exactRecord.getValue())) {
                        // IP 未变化
                        return false;
                    }
                    // 更新解析记录
                    UpdateDomainRecordResponse updateDomainRecordResponse = updateDomainRecordWithOptions(client, exactRecord, type, recordValue);
                    if (updateDomainRecordResponse.getBody() != null && org.apache.commons.lang3.StringUtils.isNotBlank(updateDomainRecordResponse.getBody().getRecordId())) {
                        return true;
                    }
                }
            }
            // 不存在精确匹配，新增记录，如果已存在通配，则新增精确匹配，如果不存在通配，新增通配记录
            AddDomainRecordResponse addDomainRecordResponse = addDomainRecordWithOptions(client, domainName, subName, "A", recordValue,wildcardRecord != null);
            return addDomainRecordResponse.getBody() != null && StrUtil.isNotBlank(addDomainRecordResponse.getBody().getRecordId());
        } catch (Exception e) {
            log.error("更新域名解析失败");
        }
        return false;
    }

    @Override
    public boolean deleteRecord(CloudPlatformAccount cloudPlatformAccount, String domainName, String subName) {
        try {
            Client client = this.createClient(cloudPlatformAccount);
            DescribeDomainRecordsResponse describeDomainRecordsResponse = describeDomainRecords(client, domainName);
            DescribeDomainRecordsResponseBody body = describeDomainRecordsResponse.getBody();
            if (body.getTotalCount() > 0) {
                for (DescribeDomainRecordsResponseBody.DescribeDomainRecordsResponseBodyDomainRecordsRecord record : body.getDomainRecords().getRecord()) {
                    if (subName.equalsIgnoreCase(record.getRR())) {
                        deleteDomainRecordWithOptions(client, record);
                    }
                }
            }
            return true;
        } catch (Exception e) {
            log.error("查询域名解析失败");
        }
        return false;
    }

    @Override
    public String queryRecord(CloudPlatformAccount cloudPlatformAccount, String domainName, String subName) {
        try {
            Client client = this.createClient(cloudPlatformAccount);
            DescribeDomainRecordsResponse describeDomainRecordsResponse = describeDomainRecords(client, domainName);
            log.debug("describeDomainRecordsResponse = " + describeDomainRecordsResponse);
            DescribeDomainRecordsResponseBody body = describeDomainRecordsResponse.getBody();
            DescribeDomainRecordsResponseBody.DescribeDomainRecordsResponseBodyDomainRecordsRecord wildcardRecord = null; // 通配匹配
            DescribeDomainRecordsResponseBody.DescribeDomainRecordsResponseBodyDomainRecordsRecord exactRecord = null; // 精确匹配
            if (body.getTotalCount() > 0) {
                for (DescribeDomainRecordsResponseBody.DescribeDomainRecordsResponseBodyDomainRecordsRecord record : body.getDomainRecords().getRecord()) {
                    if ("*".equals(record.getRR())) {
                        wildcardRecord = record;
                    } else if (subName.equalsIgnoreCase(record.getRR())) {
                        exactRecord = record;
                    }
                }
                if (exactRecord != null) {
                    return exactRecord.getValue();
                }
                return wildcardRecord == null? null: wildcardRecord.getValue();
            }
        } catch (Exception e) {
            log.error("查询域名解析失败");
        }
        return null;
    }

    private AddDomainRecordResponse addDomainRecordWithOptions(Client client, String domainName, String subName, String type, String record, boolean hasWildcard) throws Exception {
        log.debug("添加域名记录(" + (hasWildcard ? subName : "*") + "." + domainName + "): " + record);
        com.aliyun.alidns20150109.models.AddDomainRecordRequest addDomainRecordRequest = new com.aliyun.alidns20150109.models.AddDomainRecordRequest()
                .setDomainName(domainName)
                .setRR(hasWildcard ? subName : "*") // 已经有通配记录添加精确记录，否则添加通配记录
                .setType(type)
                .setValue(record);
        com.aliyun.teautil.models.RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();
        return client.addDomainRecordWithOptions(addDomainRecordRequest, runtime);
    }

    private UpdateDomainRecordResponse updateDomainRecordWithOptions(Client client, DescribeDomainRecordsResponseBody.DescribeDomainRecordsResponseBodyDomainRecordsRecord exactRecord, String type, String ip) throws Exception {
        log.debug("更新域名解析记录(" + exactRecord.getRR() + "." + exactRecord.getDomainName() + "): " + exactRecord.getValue() + " -> " + ip);
        com.aliyun.alidns20150109.models.UpdateDomainRecordRequest updateDomainRecordRequest = new com.aliyun.alidns20150109.models.UpdateDomainRecordRequest()
                .setRecordId(exactRecord.getRecordId())
                .setRR(exactRecord.getRR())
                .setType(type)
                .setValue(ip);
        com.aliyun.teautil.models.RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();
        // 复制代码运行请自行打印 API 的返回值
        return client.updateDomainRecordWithOptions(updateDomainRecordRequest, runtime);
    }

    private DeleteDomainRecordResponse deleteDomainRecordWithOptions(Client client, DescribeDomainRecordsResponseBody.DescribeDomainRecordsResponseBodyDomainRecordsRecord exactRecord) throws Exception {
        log.debug("删除域名解析记录(" + exactRecord.getRR() + "." + exactRecord.getDomainName() + ")");
        com.aliyun.alidns20150109.models.DeleteDomainRecordRequest deleteDomainRecordRequest = new com.aliyun.alidns20150109.models.DeleteDomainRecordRequest()
                .setRecordId(exactRecord.getRecordId());
        com.aliyun.teautil.models.RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();
        return client.deleteDomainRecordWithOptions(deleteDomainRecordRequest, runtime);
    }

    private DescribeDomainRecordsResponse describeDomainRecords(Client client, String domainName) throws Exception {
        log.debug("查询域名解析记录列表: {}", domainName);
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

    private com.aliyun.domain20180129.Client createDomainClient(CloudPlatformAccount cloudPlatformAccount) throws Exception {
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
                .setAccessKeyId(cloudPlatformAccount.getAccessKey())
                .setAccessKeySecret(cloudPlatformAccount.getAccessKeySecret());
        config.endpoint = "domain.aliyuncs.com";
        return new com.aliyun.domain20180129.Client(config);
    }

    @Override
    public LocalDateTime queryDomainExpiryDate(CloudPlatformAccount cloudPlatformAccount, String domainName) {
        try {
            com.aliyun.domain20180129.Client client = createDomainClient(cloudPlatformAccount);
            QueryDomainByDomainNameRequest request = new QueryDomainByDomainNameRequest()
                    .setDomainName(domainName);
            com.aliyun.teautil.models.RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();
            QueryDomainByDomainNameResponse response = client.queryDomainByDomainNameWithOptions(request, runtime);
            if (response.getBody() != null && StrUtil.isNotBlank(response.getBody().getExpirationDate())) {
                // 阿里云返回的时间格式为 "yyyy-MM-dd HH:mm:ss"
                String expirationDate = response.getBody().getExpirationDate();
                return LocalDateTime.parse(expirationDate, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
        } catch (Exception e) {
            log.error("查询域名过期时间失败: {}, 错误: {}", domainName, e.getMessage());
        }
        return null;
    }
}
