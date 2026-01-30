package cn.v7soft.admin.service.ssl;

import cn.v7soft.dao.entities.primary.TopLevelDomain;

public interface ISslCertificateRequester {
    String CERT_CONFIG_DIR = "/www/certs/";
    SslResult handleRequestSslCertificate(TopLevelDomain domain, String sslServer);

    boolean analyzeDomain(TopLevelDomain topLevelDomain, String subName, String cnameRecord);
}
