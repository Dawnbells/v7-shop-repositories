package cn.v7soft.admin.service.ssl;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.NotNull;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.v7soft.common.utils.SslCertificateUtil;
import cn.v7soft.dao.entities.primary.CloudPlatformAccount;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import kotlin.text.Charsets;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class BaseSslCertificateRequester implements ISslCertificateRequester {
    protected final static String INI_PATH = "/ssl/config/config.ini";

    @Override
    public SslResult handleRequestSslCertificate(TopLevelDomain domain, String sslServer) {
        final String logFilePath = "/var/log/letsencrypt/letsencrypt.log";
        try {
            FileUtil.del(logFilePath);
            CloudPlatformAccount cloudPlatformAccount = domain.getCloudPlatformAccount();
            // 创建配置文件
            FileUtil.touch(INI_PATH);
            final String iniContent = getIniContent(cloudPlatformAccount);
            FileUtil.writeUtf8String(iniContent, INI_PATH);
            // 更改权限
            RuntimeUtil.exec("chmod 600 " + INI_PATH);
            // 申请证书命令
            String command = "certbot certonly " +
                             makeCertbotCommand() +
                             " -d *." + domain.getName() +
                             " -d " + domain.getName() +
                             " --key-type ecdsa --register-unsafely-without-email" +
                             " --agree-tos   --non-interactive --force-renewal";
            if (StrUtil.isNotBlank(sslServer)) {
                command += " --server " + sslServer;
            }
            log.debug(command);
            // 执行命令
            Process process = Runtime.getRuntime().exec(command);

            Future<String> stdOut = CompletableFuture.supplyAsync(() -> IoUtil.read(process.getInputStream(), Charsets.UTF_8));
            Future<String> stdErr = CompletableFuture.supplyAsync(() -> IoUtil.read(process.getErrorStream(), Charsets.UTF_8));

            boolean isCompleted = process.waitFor(1, TimeUnit.MINUTES);

            String result = stdOut.get();
            String errorMsg = stdErr.get();


            boolean contains = result.contains("Successfully received certificate.");
            String errLog = "";
            if (contains) {
                // 分别提取证书路径和私钥路径
                String certPattern = "Certificate is saved at: (\\S+\\.pem)";
                String keyPattern = "Key is saved at:         (\\S+\\.pem)";
                String expiredPattern = "This certificate expires on (\\S+)\\.";

                String certPath = ReUtil.get(certPattern, result, 1);
                String keyPath = ReUtil.get(keyPattern, result, 1);
                String expired = ReUtil.get(expiredPattern, result, 1);
                log.debug("certPath: " + certPath);
                log.debug("keyPath: " + keyPath);
                log.debug("expired date: " + expired);

                final String targetDir = CERT_CONFIG_DIR + domain.getCompanyId() + "/" + domain.getName() + "/";

                String fullChain = FileUtil.readString(certPath, StandardCharsets.UTF_8);
                String privateKey = FileUtil.readString(keyPath, StandardCharsets.UTF_8);

                SslCertificateUtil.valid(domain, fullChain, privateKey);

                writePemPair(targetDir, fullChain, privateKey);
            } else {
                // 使用正则表达式提取 JSON
                String jsonPattern = "\\{\\s*\"type\":\\s*\"[^\"]+\",\\s*\"detail\":\\s*\"[^\"]+\",\\s*\"status\":\\s*\\d+\\s*}";
                errLog = ReUtil.get(jsonPattern, FileUtil.readString(logFilePath, StandardCharsets.UTF_8), 0);
            }

            return SslResult.builder()
                    .isError(false)
                    .isCompleted(isCompleted)
                    .isSuccess(contains)
                    .result(result)
                    .errorMsg(errorMsg)
                    .errLog(errLog)
                    .build();
        } catch (Exception e) {
            e.printStackTrace();
            return SslResult.builder()
                    .isError(true)
                    .isCompleted(false)
                    .isSuccess(false)
                    .result("")
                    .errorMsg(e.getCause() == null ? e.getMessage() : e.getCause().getMessage())
                    .build();
        }
    }

    protected String makeCertbotCommand() {
        return "--authenticator=dns-" + name() +
               " --dns-" + name() + "-credentials=" + INI_PATH +
               " --dns-" + name() + "-propagation-seconds 80";
    }

    protected abstract String name();

    @NotNull
    protected abstract String getIniContent(CloudPlatformAccount cloudPlatformAccount);

    private void writePemPair(String targetDir, String fullChain, String privateKey) throws Exception {
        Path dir = Path.of(targetDir);
        Files.createDirectories(dir);
        atomicWrite(dir.resolve("fullchain.pem"), fullChain);
        atomicWrite(dir.resolve("privkey.pem"), privateKey);
    }

    private void atomicWrite(Path target, String content) throws Exception {
        Path temp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            Files.deleteIfExists(temp);
            throw e;
        }
    }

    @Override
    public boolean analyzeDomain(TopLevelDomain topLevelDomain, String subName, String cnameRecord) {
        return false;
    }
}
