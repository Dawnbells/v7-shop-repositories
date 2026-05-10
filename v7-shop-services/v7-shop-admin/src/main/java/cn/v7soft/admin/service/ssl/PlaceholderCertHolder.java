package cn.v7soft.admin.service.ssl;

import cn.hutool.core.io.FileUtil;
import cn.v7soft.common.utils.SslCertificateUtil;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.PrivateKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Slf4j
@Component
public class PlaceholderCertHolder {
    private static final String FULLCHAIN_FILE = "fullchain.pem";
    private static final String PRIVKEY_FILE = "privkey.pem";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Getter
    private String fullchain;
    @Getter
    private String privkey;

    @PostConstruct
    public void init() {
        try {
            KeyPair keyPair = generateKeyPair();
            X509Certificate certificate = generateCertificate(keyPair);
            fullchain = toPem(certificate);
            privkey = toPkcs8Pem(keyPair.getPrivate());
            SslCertificateUtil.valid(fullchain, privkey);
            log.info("placeholder ssl certificate initialized");
        } catch (Exception e) {
            throw new IllegalStateException("failed to initialize placeholder ssl certificate", e);
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
            java.nio.file.Files.createDirectories(dir);
            atomicWrite(dir.resolve(FULLCHAIN_FILE), fullchainContent);
            atomicWrite(dir.resolve(PRIVKEY_FILE), privkeyContent);
        } catch (IOException e) {
            throw new IllegalStateException("failed to write ssl certificate files to " + targetDir, e);
        }
    }

    private void atomicWrite(Path target, String content) throws IOException {
        Path temp = java.nio.file.Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            java.nio.file.Files.writeString(temp, content, StandardCharsets.UTF_8);
            java.nio.file.Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            java.nio.file.Files.deleteIfExists(temp);
            throw e;
        }
    }

    private KeyPair generateKeyPair() throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"), SECURE_RANDOM);
        return generator.generateKeyPair();
    }

    private X509Certificate generateCertificate(KeyPair keyPair) throws Exception {
        Instant now = Instant.now();
        Date notBefore = Date.from(now.minus(1, ChronoUnit.DAYS));
        Date notAfter = Date.from(now.plus(36500, ChronoUnit.DAYS));
        X500Name subject = new X500Name("CN=placeholder.invalid");
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject,
                new BigInteger(160, SECURE_RANDOM),
                notBefore,
                notAfter,
                subject,
                keyPair.getPublic());
        builder.addExtension(
                Extension.subjectAlternativeName,
                false,
                new GeneralNames(new GeneralName(GeneralName.dNSName, "placeholder.invalid")));
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.getPrivate());
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(builder.build(signer));
    }

    private String toPem(Object object) throws IOException {
        StringWriter stringWriter = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
            pemWriter.writeObject(object);
        }
        return stringWriter.toString();
    }

    private String toPkcs8Pem(PrivateKey privateKey) throws IOException {
        StringWriter stringWriter = new StringWriter();
        try (PemWriter pemWriter = new PemWriter(stringWriter)) {
            pemWriter.writeObject(new PemObject("PRIVATE KEY", privateKey.getEncoded()));
        }
        return stringWriter.toString();
    }
}
