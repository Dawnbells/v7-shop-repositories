package cn.v7soft.admin.service.ssl;

import cn.v7soft.dao.entities.primary.TopLevelDomain;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Builder
public class UnsupportedSslCertificateRequester implements ISslCertificateRequester {
    private final PlaceholderCertHolder placeholderCertHolder;

    @Override
    public SslResult handleRequestSslCertificate(TopLevelDomain domain, String sslServer) {
        checkAndWriteDefault(domain);
        return SslResult.builder()
                .isCompleted(true)
                .isSuccess(false)
                .isError(true)
                .errorMsg("unsupported cloud platform for automatic ssl certificate request")
                .build();
    }

    public void checkAndWriteDefault(TopLevelDomain domain) {
        if (placeholderCertHolder == null) {
            log.warn("placeholder cert holder is unavailable, skip writing default certificate");
            return;
        }
        placeholderCertHolder.ensureWritten(domain);
    }

    @Override
    public boolean analyzeDomain(TopLevelDomain topLevelDomain, String subName, String cnameRecord) {
        return false;
    }
}
