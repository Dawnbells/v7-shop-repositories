package cn.v7soft.admin.service.ssl;

import cn.v7soft.admin.utils.NginxCertificateReferences;
import cn.v7soft.admin.utils.NginxWorkspace;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DomainResourceCleanerTest {
    @TempDir Path root;
    private Path nginx;
    private Path certs;
    private Path certDir;
    private TopLevelDomain domain;
    private DomainResourceCleaner cleaner;

    @BeforeEach
    void setup() throws IOException {
        nginx = Files.createDirectories(root.resolve("nginx"));
        certs = Files.createDirectories(root.resolve("certs"));
        certDir = Files.createDirectories(certs.resolve("1/example.com"));
        Files.writeString(certDir.resolve("fullchain.pem"), "certificate-before-delete");
        Files.writeString(certDir.resolve("privkey.pem"), "key-before-delete");
        domain = TopLevelDomain.builder().name("example.com").companyId(1L).build();
        cleaner = new DomainResourceCleaner(nginx, certs);
    }

    private Path config(String server, String name) throws IOException {
        Path path = nginx.resolve(server).resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, "server { ssl_certificate \"" + certDir.resolve("fullchain.pem").toString().replace('\\', '/')
                + "\"; ssl_certificate_key \"" + certDir.resolve("privkey.pem").toString().replace('\\', '/') + "\"; }");
        return path;
    }

    @Test
    void deletesHistoricalServersAndPublishesOnlyAfterAllFilesAreRemoved() throws IOException {
        Path a = config("front-a", "example.com.conf");
        Path b = config("renamed-or-deleted-server", "example.com.conf");
        AtomicInteger pushes = new AtomicInteger();
        cleaner.deleteDomain(domain, () -> {
            assertFalse(Files.exists(a));
            assertFalse(Files.exists(b));
            assertFalse(Files.exists(certDir));
            pushes.incrementAndGet();
        });
        assertEquals(1, pushes.get());
    }

    @Test
    void configDeletionFailureKeepsCertificatesAndRestoresOtherConfigs() throws IOException {
        Path a = config("front-a", "example.com.conf");
        Path b = config("front-b", "example.com.conf");
        cleaner = new DomainResourceCleaner(nginx, certs) {
            @Override protected void deleteFile(Path path) throws IOException {
                if (path.equals(b)) throw new IOException("simulated permission failure");
                super.deleteFile(path);
            }
        };
        assertThrows(IllegalStateException.class, () -> cleaner.deleteDomain(domain, () -> fail("must not publish")));
        assertTrue(Files.exists(a));
        assertTrue(Files.exists(b));
        assertEquals("key-before-delete", Files.readString(certDir.resolve("privkey.pem")));
    }

    @Test
    void customFilenameStillReferencingCertificateBlocksDeletion() throws IOException {
        Path generated = config("front-a", "example.com.conf");
        config("historical", "custom-shared.conf");
        assertThrows(IllegalStateException.class, () -> cleaner.deleteDomain(domain, () -> fail("must not publish")));
        assertTrue(Files.exists(generated));
        NginxCertificateReferences.validate(nginx);
    }

    @Test
    void certificateDeletionFailureRestoresFilesBeforeConfigs() throws IOException {
        Path config = config("front-a", "example.com.conf");
        cleaner = new DomainResourceCleaner(nginx, certs) {
            @Override protected void deleteFile(Path path) throws IOException {
                if (path.equals(certDir.resolve("fullchain.pem"))) throw new IOException("simulated cert failure");
                super.deleteFile(path);
            }
        };
        assertThrows(IllegalStateException.class, () -> cleaner.deleteDomain(domain, () -> fail("must not publish")));
        assertTrue(Files.exists(config));
        assertEquals("key-before-delete", Files.readString(certDir.resolve("privkey.pem")));
        NginxCertificateReferences.validate(nginx);
    }

    @Test
    void pushFailureRestoresCompleteWorkspace() throws IOException {
        Path config = config("front-a", "example.com.conf");
        assertThrows(IllegalStateException.class, () -> cleaner.deleteDomain(domain,
                () -> { throw new IllegalStateException("push failed"); }));
        assertTrue(Files.exists(config));
        assertEquals("certificate-before-delete", Files.readString(certDir.resolve("fullchain.pem")));
        assertEquals("key-before-delete", Files.readString(certDir.resolve("privkey.pem")));
        NginxCertificateReferences.validate(nginx);
    }

    @Test
    void orphanCleanupUsesFreshDomainsAndPreservesNewDomain() throws IOException {
        Path config = config("front-a", "example.com.conf");
        AtomicInteger queries = new AtomicInteger();
        cleaner.cleanOrphans(() -> { queries.incrementAndGet(); return List.of(domain); }, () -> fail("no change"));
        assertEquals(1, queries.get());
        assertTrue(Files.exists(config));
        assertTrue(Files.exists(certDir));
    }

    @Test
    void orphanCleanupPublishesAllServersTogether() throws IOException {
        config("front-a", "example.com.conf");
        config("removed-server", "example.com.conf");
        AtomicInteger pushes = new AtomicInteger();
        cleaner.cleanOrphans(List::of, () -> {
            assertFalse(Files.exists(certDir));
            assertFalse(Files.exists(nginx.resolve("removed-server/example.com.conf")));
            pushes.incrementAndGet();
        });
        assertEquals(1, pushes.get());
    }

    @Test
    void orphanCleanupCannotRemoveReferencedCertificatesOnConfigFailure() throws IOException {
        config("front-a", "example.com.conf");
        config("historical", "shared.conf");
        assertThrows(IllegalStateException.class, () -> cleaner.cleanOrphans(List::of, () -> fail("must not publish")));
        NginxCertificateReferences.validate(nginx);
    }

    @Test
    void cleanupWaitsForWriterBeforeQueryingCurrentDomains() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicInteger queries = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        Future<?> cleanup;
        try {
            try (var ignored = NginxWorkspace.lock()) {
                cleanup = executor.submit(() -> {
                    started.countDown();
                    cleaner.cleanOrphans(() -> { queries.incrementAndGet(); return List.of(domain); }, () -> fail("no change"));
                });
                assertTrue(started.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> cleanup.get(150, TimeUnit.MILLISECONDS));
                assertEquals(0, queries.get());
            }
            cleanup.get(5, TimeUnit.SECONDS);
            assertEquals(1, queries.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void orphanCleanupPreservesCustomSnippetsAndNonCompanyDirectories() throws IOException {
        Path custom = nginx.resolve("front-a/ssl.options.conf");
        Files.createDirectories(custom.getParent());
        Files.writeString(custom, "ssl_protocols TLSv1.2 TLSv1.3;");
        Path metadata = Files.createDirectories(certs.resolve("metadata/backup.example"));
        cleaner.cleanOrphans(() -> List.of(domain), () -> fail("no generated orphan resources"));
        assertTrue(Files.exists(custom));
        assertTrue(Files.exists(metadata));
    }

    @Test
    void publisherValidationRejectsMissingCertificate() throws IOException {
        config("front-a", "example.com.conf");
        Files.delete(certDir.resolve("privkey.pem"));
        assertThrows(IOException.class, () -> NginxCertificateReferences.validate(nginx));
    }
}
