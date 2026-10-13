package cn.v7soft.admin.service.ssl;

import cn.v7soft.admin.utils.NginxCertificateReferences;
import cn.v7soft.admin.utils.NginxWorkspace;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** One deletion protocol for manual deletion and scheduled orphan cleanup. */
@Component
public class DomainResourceCleaner {
    private final Path nginxRoot;
    private final Path certRoot;

    public DomainResourceCleaner() {
        this(Path.of("/www/nginx"), Path.of(ISslCertificateRequester.CERT_CONFIG_DIR));
    }

    DomainResourceCleaner(Path nginxRoot, Path certRoot) {
        this.nginxRoot = nginxRoot.toAbsolutePath().normalize();
        this.certRoot = certRoot.toAbsolutePath().normalize();
    }

    public void deleteDomain(TopLevelDomain domain, Runnable publish) {
        try (var ignored = NginxWorkspace.lock()) {
            Path certDir = domainCertDir(String.valueOf(domain.getCompanyId()), domain.getName());
            List<Path> configs = NginxCertificateReferences.files(nginxRoot).stream()
                    .filter(p -> p.getFileName().toString().equals(domain.getName() + ".conf")).toList();
            deleteBatch(configs, List.of(certDir), publish);
        } catch (IOException e) {
            throw new IllegalStateException("清理域名资源失败: " + domain.getName(), e);
        }
    }

    public void cleanOrphans(Supplier<List<TopLevelDomain>> loadCurrentDomains, Runnable publish) {
        try (var ignored = NginxWorkspace.lock()) {
            // Query after acquiring the lock, never reuse the snapshot from the start of the daily scan.
            List<TopLevelDomain> domains = loadCurrentDomains.get();
            Set<Path> validCertDirs = new LinkedHashSet<>();
            for (TopLevelDomain domain : domains) {
                validCertDirs.add(domainCertDir(String.valueOf(domain.getCompanyId()), domain.getName()));
            }
            Set<String> validNames = domains.stream().map(TopLevelDomain::getName).collect(Collectors.toSet());
            List<Path> candidates = NginxCertificateReferences.files(nginxRoot).stream()
                    // Only generated per-domain files are eligible; custom snippets are never deleted.
                    .filter(p -> nginxRoot.relativize(p).getNameCount() == 2)
                    .filter(p -> p.getFileName().toString().endsWith(".conf"))
                    .filter(p -> !validNames.contains(p.getFileName().toString().replaceFirst("\\.conf$", "")))
                    .filter(p -> p.getFileName().toString().matches("[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)+\\.conf"))
                    .toList();
            List<Path> configs = new ArrayList<>();
            for (Path candidate : candidates) {
                String name = candidate.getFileName().toString().replaceFirst("\\.conf$", "");
                // Filename alone is insufficient: preserve custom snippets even if they contain dots.
                List<Path> references = NginxCertificateReferences.certificates(candidate);
                if (!references.isEmpty() && references.stream().allMatch(cert ->
                        cert.startsWith(certRoot) && cert.getParent().getFileName().toString().equals(name))) {
                    configs.add(candidate);
                }
            }
            List<Path> certDirs = new ArrayList<>();
            // Fail closed if traversal is incomplete or encounters a symlink.
            NginxCertificateReferences.files(certRoot);
            if (Files.exists(certRoot)) {
                try (var walk = Files.walk(certRoot, 2)) {
                    walk.filter(Files::isDirectory)
                            .filter(p -> certRoot.relativize(p).getNameCount() == 2)
                            .filter(p -> certRoot.relativize(p).getName(0).toString().matches("[0-9]+"))
                            .filter(p -> p.getFileName().toString().matches("[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)+"))
                            .filter(p -> !validCertDirs.contains(p))
                            .forEach(certDirs::add);
                }
            }
            deleteBatch(configs, certDirs, publish);
        } catch (IOException e) {
            throw new IllegalStateException("清理孤立域名资源失败，证书保留或恢复", e);
        }
    }

    private Path domainCertDir(String company, String domain) {
        if (!company.matches("[0-9]+") || domain == null
                || !domain.matches("[a-zA-Z0-9][a-zA-Z0-9.-]*") || domain.contains("..")) {
            throw new IllegalArgumentException("非法域名证书目录");
        }
        return certRoot.resolve(company).resolve(domain).normalize();
    }

    private void deleteBatch(List<Path> configs, List<Path> certDirs, Runnable publish) throws IOException {
        Map<Path, Backup> configBackup = snapshot(configs);
        List<Path> certFiles = new ArrayList<>();
        for (Path dir : certDirs) certFiles.addAll(NginxCertificateReferences.files(dir));
        Map<Path, Backup> certBackup = snapshot(certFiles);
        boolean changed = !configs.isEmpty() || certDirs.stream().anyMatch(Files::exists);
        if (!changed) return;
        try {
            for (Path config : configs) deleteFile(config);
            // Rescan the entire filesystem, including custom filenames and historical servers.
            NginxCertificateReferences.assertUnreferenced(nginxRoot, certDirs);
            for (Path config : configs) {
                if (Files.exists(config, LinkOption.NOFOLLOW_LINKS)) throw new IOException("nginx 配置删除失败: " + config);
            }
            for (Path certDir : certDirs) {
                if (Files.notExists(certDir, LinkOption.NOFOLLOW_LINKS)) continue;
                try (var walk = Files.walk(certDir)) {
                    for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) deleteFile(path);
                }
                if (Files.exists(certDir, LinkOption.NOFOLLOW_LINKS)) throw new IOException("证书目录删除失败: " + certDir);
            }
            publish.run();
        } catch (IOException | RuntimeException failure) {
            // Restore certificates first; never restore a config if certificate recovery failed.
            if (restore(certBackup, failure)) restore(configBackup, failure);
            throw failure;
        }
    }

    protected void deleteFile(Path path) throws IOException {
        Files.delete(path);
    }

    private Map<Path, Backup> snapshot(List<Path> paths) throws IOException {
        Map<Path, Backup> result = new LinkedHashMap<>();
        for (Path path : paths) {
            Set<PosixFilePermission> permissions = Files.getFileStore(path).supportsFileAttributeView("posix")
                    ? Files.getPosixFilePermissions(path) : null;
            result.put(path, new Backup(Files.readAllBytes(path), permissions));
        }
        return result;
    }

    private boolean restore(Map<Path, Backup> backups, Throwable failure) {
        boolean restored = true;
        for (var entry : backups.entrySet()) {
            try {
                Files.createDirectories(entry.getKey().getParent());
                if (!Files.exists(entry.getKey()) && entry.getValue().permissions != null) {
                    Files.createFile(entry.getKey(), java.nio.file.attribute.PosixFilePermissions
                            .asFileAttribute(entry.getValue().permissions));
                }
                Files.write(entry.getKey(), entry.getValue().content);
            } catch (IOException | RuntimeException e) {
                restored = false;
                failure.addSuppressed(e);
            }
        }
        return restored;
    }

    private record Backup(byte[] content, Set<PosixFilePermission> permissions) {}
}
