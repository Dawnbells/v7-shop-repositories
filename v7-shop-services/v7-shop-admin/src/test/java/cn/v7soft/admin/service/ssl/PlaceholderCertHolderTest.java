package cn.v7soft.admin.service.ssl;

import cn.v7soft.common.utils.SslCertificateUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceholderCertHolderTest {

    @Test
    void shouldGenerateValidPlaceholderCertificateInMemory() {
        PlaceholderCertHolder holder = new PlaceholderCertHolder();

        assertDoesNotThrow(holder::init);

        assertNotNull(holder.getFullchain());
        assertNotNull(holder.getPrivkey());
        assertTrue(holder.getFullchain().contains("BEGIN CERTIFICATE"));
        assertTrue(holder.getPrivkey().contains("BEGIN PRIVATE KEY"));
        assertDoesNotThrow(() -> SslCertificateUtil.valid(holder.getFullchain(), holder.getPrivkey()));
    }
}
