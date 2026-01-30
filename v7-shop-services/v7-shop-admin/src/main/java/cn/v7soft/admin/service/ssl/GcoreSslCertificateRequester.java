package cn.v7soft.admin.service.ssl;

import org.jetbrains.annotations.NotNull;

import cn.v7soft.dao.entities.primary.CloudPlatformAccount;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Builder
public class GcoreSslCertificateRequester extends BaseSslCertificateRequester {
    @Override
    protected String name() {
        return "gcore";
    }

    @NotNull
    @Override
    protected String getIniContent(CloudPlatformAccount cloudPlatformAccount) {
        return "dns_gcore_apitoken = " + cloudPlatformAccount.getGcoreApiToken();
    }

    @Override
    public boolean analyzeDomain(TopLevelDomain topLevelDomain, String subName, String cnameRecord) {
        return false;
    }
}
