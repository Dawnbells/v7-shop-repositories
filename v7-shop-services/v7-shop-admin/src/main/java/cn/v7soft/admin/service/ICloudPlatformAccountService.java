package cn.v7soft.admin.service;

import cn.v7soft.admin.service.ssl.ISslCertificateRequester;
import cn.v7soft.common.service.IBaseDataRangeService;
import cn.v7soft.dao.entities.primary.CloudPlatformAccount;

public interface ICloudPlatformAccountService extends IBaseDataRangeService<CloudPlatformAccount> {
    ISslCertificateRequester getCertificateRequester(CloudPlatformAccount cloudPlatformAccount);
}
