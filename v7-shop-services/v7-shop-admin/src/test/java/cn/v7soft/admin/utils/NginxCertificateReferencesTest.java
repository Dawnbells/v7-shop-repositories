package cn.v7soft.admin.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NginxCertificateReferencesTest {
    @TempDir Path root;

    @Test
    void preservesQuotedHashAndSpacesAndIgnoresCommentedReferences() throws IOException {
        Path cert = root.resolve("with space#cert.pem");
        Files.writeString(cert, "certificate");
        Path conf = root.resolve("site.conf");
        Files.writeString(conf, "# ssl_certificate /missing.pem;\nssl_certificate '"
                + cert.toString().replace('\\', '/') + "'; # trailing comment\n");
        assertEquals(List.of(cert), NginxCertificateReferences.certificates(conf));
    }

    @Test
    void dynamicPathsBlockLegacyDeletionInsteadOfSilentlyMissingReferences() throws IOException {
        Path conf = root.resolve("site.conf");
        Files.writeString(conf, "ssl_certificate $cert_dir/fullchain.pem;");
        assertThrows(IOException.class, () -> NginxCertificateReferences.assertUnreferenced(root, root.resolve("certs")));
    }

    @Test
    void malformedCertificateDirectiveFailsClosed() throws IOException {
        Path conf = root.resolve("site.conf");
        Files.writeString(conf, "ssl_certificate /www/certs/1/example.com/fullchain.pem\n}");
        assertThrows(IOException.class, () -> NginxCertificateReferences.certificates(conf));
    }
}
