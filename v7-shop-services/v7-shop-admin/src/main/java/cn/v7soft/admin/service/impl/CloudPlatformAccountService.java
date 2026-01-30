package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.service.ssl.AliyunSslCertificateRequester;
import cn.v7soft.admin.service.ssl.GcoreSslCertificateRequester;
import cn.v7soft.admin.service.ssl.ISslCertificateRequester;
import cn.v7soft.admin.service.ssl.UnsupportedSslCertificateRequester;
import cn.v7soft.admin.service.ICloudPlatformAccountService;
import cn.v7soft.common.controller.req.attributes.AccessDataRangeAttribute;
import cn.v7soft.common.enums.AccessDataRangeLevel;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.entities.primary.CloudPlatformAccount;
import cn.v7soft.dao.enums.CloudPlatform;
import cn.v7soft.dao.repositories.primary.CloudPlatformAccountRepository;
import org.springframework.stereotype.Service;

@Service
public class CloudPlatformAccountService extends BaseDataRangeService<CloudPlatformAccount, CloudPlatformAccountRepository> implements ICloudPlatformAccountService {
    public CloudPlatformAccountService(CloudPlatformAccountRepository repository) {
        super(repository);
    }

    @Override
    protected void checkKeyConstraint(CloudPlatformAccount entity) {
        CloudPlatformAccount existingAccount = repository.findBySameName(entity.getName(), entity.getId());
        ClientResponseEnum.PARAMETER_ILLEGAL.isNull(existingAccount, "角色名不允许重复");
    }

    @Override
    public ISslCertificateRequester getCertificateRequester(CloudPlatformAccount cloudPlatformAccount) {
        if (Boolean.TRUE.equals(cloudPlatformAccount.getDnsGcore())) {
            return GcoreSslCertificateRequester.builder().build();
        }
        if (cloudPlatformAccount.getCloudPlatform() == CloudPlatform.ALIYUN) {
            return AliyunSslCertificateRequester.builder().build();
        }
        return UnsupportedSslCertificateRequester.builder().build();
    }

    @Override
    public QueryAttribute getAccessDataRangeQueryAttribute() {
        return new AccessDataRangeAttribute(AccessDataRangeLevel.COMPANY);
    }
}
