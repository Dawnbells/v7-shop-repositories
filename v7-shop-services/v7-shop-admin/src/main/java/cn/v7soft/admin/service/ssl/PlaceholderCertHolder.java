package cn.v7soft.admin.service.ssl;

import cn.hutool.core.io.FileUtil;
import cn.v7soft.common.utils.SslCertificateUtil;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class PlaceholderCertHolder {
    private static final String FULLCHAIN_FILE = "fullchain.pem";
    private static final String PRIVKEY_FILE = "privkey.pem";

    @Getter
    private String fullchain;
    @Getter
    private String privkey;

    @PostConstruct
    public void init() {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("placeholder-cert-");
            Path keyPath = tempDir.resolve(PRIVKEY_FILE);
            Path certPath = tempDir.resolve(FULLCHAIN_FILE);

            runOpenSsl(tempDir, "genpkey", "-algorithm", "EC", "-pkeyopt", "ec_paramgen_curve:prime256v1",
                    "-out", keyPath.toString());
            runOpenSsl(tempDir, "req", "-new", "-x509", "-key", keyPath.toString(), "-out", certPath.toString(),
                    "-days", "36500", "-subj", "/CN=placeholder.invalid");

            fullchain = Files.readString(certPath, StandardCharsets.UTF_8);
            privkey = Files.readString(keyPath, StandardCharsets.UTF_8);
            SslCertificateUtil.valid(fullchain, privkey);
            log.info("placeholder ssl certificate initialized");
        } catch (Exception e) {
            throw new IllegalStateException("failed to initialize placeholder ssl certificate", e);
        } finally {
            if (tempDir != null) {
                FileUtil.del(tempDir.toFile());
            }
        }
    }

    public void ensureWritten(TopLevelDomain domain) {
        if (domain == null) {
            return;
        }
        final String targetDir = ISslCertificateRequester.CERT_CONFIG_DIR + domain.getCompanyId() + "/" + domain.getName() + "/";
        String fullchainPath = targetDir + FULLCHAIN_FILE;
        String privkeyPath = targetDir + PRIVKEY_FILE;
        try {
            String currentFullchain = FileUtil.readString(fullchainPath, StandardCharsets.UTF_8);
            String currentPrivkey = FileUtil.readString(privkeyPath, StandardCharsets.UTF_8);
            SslCertificateUtil.valid(currentFullchain, currentPrivkey);
        } catch (Exception e) {
            log.info("writing placeholder ssl certificate for domain {}", domain.getName());
            writePemPair(targetDir, fullchain, privkey);
        }
    }

    public void writePlaceholder(TopLevelDomain domain) {
        if (domain == null) {
            return;
        }
        final String targetDir = ISslCertificateRequester.CERT_CONFIG_DIR + domain.getCompanyId() + "/" + domain.getName() + "/";
        writePemPair(targetDir, fullchain, privkey);
    }

    public void writePemPair(String targetDir, String fullchainContent, String privkeyContent) {
        try {
            Path dir = Path.of(targetDir);
            Files.createDirectories(dir);
            atomicWrite(dir.resolve(FULLCHAIN_FILE), fullchainContent);
            atomicWrite(dir.resolve(PRIVKEY_FILE), privkeyContent);
        } catch (IOException e) {
            throw new IllegalStateException("failed to write ssl certificate files to " + targetDir, e);
        }
    }

    private void atomicWrite(Path target, String content) throws IOException {
        Path temp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
    }

    private void runOpenSsl(Path tempDir, String... args) throws IOException, InterruptedException {
        String[] command = new String[args.length + 1];
        command[0] = "openssl";
        System.arraycopy(args, 0, command, 1, args.length);
        Process process = new ProcessBuilder(command)
                .directory(tempDir.toFile())
                .redirectErrorStream(true)
                .start();
        boolean completed = process.waitFor(30, TimeUnit.SECONDS);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!completed) {
            process.destroyForcibly();
            throw new IllegalStateException("openssl command timed out: " + String.join(" ", command));
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("openssl command failed: " + String.join(" ", command) + ", output: " + output);
        }
    }
}
