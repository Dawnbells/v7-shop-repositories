package cn.v7soft.common.utils;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.io.FileUtil;
import cn.v7soft.common.dto.SSLCertificateInfo;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.List;

@Slf4j
public class SslCertificateUtil {
    public static final String PLACEHOLDER_DNS_NAME = "placeholder.invalid";

    static {
        // 注册 BouncyCastle 提供程序
        Security.addProvider(new BouncyCastleProvider());
    }

    public static LocalDateTime getExpiryDate(TopLevelDomain domain) {
        X509Certificate certificate = loadCertificate(domain);
        if (certificate == null) {
            return null;
        }
        return LocalDateTimeUtil.of(certificate.getNotAfter());
    }

    /**
     * 返回真实证书的有效期。占位证书或证书缺失时返回 null。
     * 用于巡检和申请失败路径，避免占位证书的 100 年有效期污染业务数据。
     */
    public static LocalDateTime getRealExpiryDate(TopLevelDomain domain) {
        X509Certificate certificate = loadCertificate(domain);
        if (certificate == null || isPlaceholder(certificate)) {
            return null;
        }
        return LocalDateTimeUtil.of(certificate.getNotAfter());
    }

    /**
     * 判断磁盘上的证书文件是否为占位证书（CN/SAN 命中 placeholder.invalid）。
     */
    public static boolean isPlaceholderCertificate(TopLevelDomain domain) {
        X509Certificate certificate = loadCertificate(domain);
        return certificate != null && isPlaceholder(certificate);
    }

    private static X509Certificate loadCertificate(TopLevelDomain domain) {
        String path = "/www/certs/" + domain.getCompanyId() + "/" + domain.getName() + "/fullchain.pem";
        try (FileInputStream fis = new FileInputStream(path)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(fis);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isPlaceholder(X509Certificate certificate) {
        try {
            String subjectDn = certificate.getSubjectX500Principal().getName();
            if (subjectDn != null && subjectDn.contains("CN=" + PLACEHOLDER_DNS_NAME)) {
                return true;
            }
            Collection<List<?>> altNames = certificate.getSubjectAlternativeNames();
            if (altNames != null) {
                for (List<?> item : altNames) {
                    if (item.size() >= 2 && Integer.valueOf(2).equals(item.get(0))
                            && PLACEHOLDER_DNS_NAME.equals(item.get(1))) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("isPlaceholder check failed", e);
        }
        return false;
    }

    private static void write(String path, String content) {
        File file = new File(path);
        FileOutputStream fos = null;
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            fos = new FileOutputStream(path);
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                }
            }
        }
    }


    public static SSLCertificateInfo valid(String fullChain, String privateKey) {
        SSLCertificateInfo sslCertificateInfo = parseCertificate(fullChain);
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(sslCertificateInfo, "公钥不正确");
        boolean validPEMPrivateKey = isValidPEMPrivateKey(privateKey);
        ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(validPEMPrivateKey, "私钥不正确");
        assert sslCertificateInfo != null;
        String certExtentInfo = sslCertificateInfo.getCertExtentInfo();
        ClientResponseEnum.PARAMETER_ILLEGAL.notBlank(certExtentInfo, "公钥解析失败");
        return sslCertificateInfo;
    }

    public static void valid(TopLevelDomain domain) {
        String fullchainPath = "/www/certs/" + domain.getCompanyId() + "/" + domain.getName() + "/fullchain.pem";
        String privkeyPath = "/www/certs/" + domain.getCompanyId() + "/" + domain.getName() + "/privkey.pem";
        valid(FileUtil.readString(fullchainPath, StandardCharsets.UTF_8), FileUtil.readString(privkeyPath, StandardCharsets.UTF_8));
    }
    public static void valid(TopLevelDomain domain, String fullChain, String privateKey) {
        SSLCertificateInfo sslCertificateInfo = valid(fullChain, privateKey);
        String domainName = domain.getName();
        String certExtentInfo = sslCertificateInfo.getCertExtentInfo();
        ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(
                certExtentInfo.contains(domainName + ",") ||
                        certExtentInfo.contains("," + domainName) ||
                        certExtentInfo.contains("*." + domainName + ",") ||
                        certExtentInfo.contains("," + "*." + domainName), "证书不匹配");

        ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(sslCertificateInfo.getCertValidDays() > 0, "证书不在有效期内");
    }

    public static void writeFullChain(TopLevelDomain domain, String fullChain) {
        try {
            String path = "/www/certs/" + domain.getCompanyId() + "/" + domain.getName() + "/fullchain.pem";
            write(path, fullChain);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void writePrivateKey(TopLevelDomain domain, String privateKey) {
        try {
            String path = "/www/certs/" + domain.getCompanyId() + "/" + domain.getName() + "/privkey.pem";
            write(path, privateKey);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 验证 PEM 格式的私钥
     *
     * @param privkey 私钥
     * @return 是否是有效的私钥
     */
    public static boolean isValidPEMPrivateKey(String privkey) {
        return isValidECDSAPrivateKey(privkey) || isValidRSAPrivateKey(privkey);
    }


    public static SSLCertificateInfo parseCertificate(String fullchain) {
        try {
            // 读取证书
            InputStream certInputStream = new ByteArrayInputStream(fullchain.getBytes(StandardCharsets.UTF_8));
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            X509Certificate certificate = (X509Certificate) certificateFactory.generateCertificate(certInputStream);

            // 获取证书的公用名称（Common Name）
            String commonName = certificate.getSubjectX500Principal().getName();
            if (commonName.contains("CN")) {
                commonName = commonName.split("=")[1];
            }

            // 获取证书签发机构的信息
            String issuer = certificate.getIssuerX500Principal().getName();
            String[] issuerParts = parseIssuer(issuer);

            // 获取证书信息
            String certSerialNumber = certificate.getSerialNumber().toString();
            String certNotValidBefore = formatDate(certificate.getNotBefore());
            String certNotValidAfter = formatDate(certificate.getNotAfter());
            int certValidDays = (int) ((certificate.getNotAfter().getTime() - new Date().getTime()) / (1000 * 60 * 60 * 24));

            // 获取公钥信息
            PublicKey publicKey = certificate.getPublicKey();
            String publicKeyAlgorithm = publicKey.getAlgorithm();
            String publicKeyString = Base64.getEncoder().encodeToString(publicKey.getEncoded());

            // 获取证书的签名算法
            String signWith = certificate.getSigAlgName();

            // 获取证书的哈希值（SHA-1 和 SHA-256）
            String certHashSha1 = getCertHash(certificate, "SHA-1");
            String certHashSha256 = getCertHash(certificate, "SHA-256");

            // 获取证书签名（Base64 编码）
            String cerSignature = Base64.getEncoder().encodeToString(certificate.getSignature());

            // 获取适用的域名（使用扩展信息）
            String certExtentInfo = getSubjectAlternativeNames(certificate);

            // 解析并填充实体类
            return SSLCertificateInfo.builder()
                    .commonName(commonName)
                    .iCommonName(issuerParts[0])
                    .iCountryName(issuerParts[1])
                    .certCaNot(issuerParts[3])
                    .certSerialNumber(certSerialNumber)
                    .certNotValidBefore(certNotValidBefore)
                    .certNotValidAfter(certNotValidAfter)
                    .certValidDays(certValidDays)
                    .publicKey(publicKeyAlgorithm)
                    .signWith(signWith)
                    .certHashSha1(certHashSha1)
                    .certHashSha256(certHashSha256)
                    .cerSignature(cerSignature)
                    .certExtentInfo(certExtentInfo)
                    .publicKeyString(publicKeyString)
                    .build();
        } catch (Throwable e) {
            return null;
        }
    }

    private static String[] parseIssuer(String issuer) {
        // 解析 issuer 中的各个字段
        String[] parts = issuer.split(",");
        String commonName = "";
        String countryName = "";
        String organizationalUnitName = "";
        String caNot = "";

        for (String part : parts) {
            if (part.contains("CN=")) {
                commonName = part.split("=")[1];
            } else if (part.contains("C=")) {
                countryName = part.split("=")[1];
            } else if (part.contains("OU=")) {
                organizationalUnitName = part.split("=")[1];
            } else if (part.contains("O=")) {
                caNot = part.split("=")[1];
            }
        }
        return new String[]{commonName, countryName, organizationalUnitName, caNot};
    }

    private static String formatDate(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(date);
    }

    private static String getCertHash(X509Certificate certificate, String algorithm) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        byte[] certBytes = certificate.getEncoded();
        byte[] hashBytes = digest.digest(certBytes);
        return bytesToHex(hashBytes);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte aByte : bytes) {
            String hex = Integer.toHexString(0xff & aByte);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString().toUpperCase();
    }

    private static String getSubjectAlternativeNames(X509Certificate certificate) {
        // 获取证书扩展信息中的域名
        try {
            Collection<List<?>> altNames = certificate.getSubjectAlternativeNames();
            if (altNames != null) {
                StringBuilder names = new StringBuilder();
                for (List<?> item : altNames) {
                    if (item.get(0).equals(2)) { // DNS
                        names.append(item.get(1)).append(", ");
                    }
                }
                if (names.length() > 0) {
                    names.setLength(names.length() - 2); // 去掉最后的 ", "
                }
                return names.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * 验证 ECDSA 格式的私钥
     *
     * @param privkey 私钥
     * @return 是否是有效的 ECDSA 私钥
     */
    private static boolean isValidECDSAPrivateKey(String privkey) {
        try (InputStream is = new ByteArrayInputStream(privkey.getBytes(StandardCharsets.UTF_8));
             PEMParser pemParser = new PEMParser(new java.io.InputStreamReader(is))) {

            // 解析 PEM 文件
            Object object = pemParser.readObject();
            if (object instanceof PEMKeyPair keyPair) {
                ECPrivateKey privateKey = (ECPrivateKey) keyPair.getPrivateKeyInfo();

                // 验证私钥是否有效
                Signature signature = Signature.getInstance("SHA256withECDSA", "BC");
                signature.initSign(privateKey);
                signature.update("test".getBytes());
                byte[] signedData = signature.sign();

                return signedData != null && signedData.length > 0;
            } else if (object instanceof PrivateKeyInfo privateKeyInfo) {
                JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
                PrivateKey privateKey = converter.getPrivateKey(privateKeyInfo);

                // 验证私钥是否有效
                Signature signature = Signature.getInstance("SHA256withECDSA", "BC");
                signature.initSign(privateKey);
                signature.update("test".getBytes());
                byte[] signedData = signature.sign();

                return signedData != null && signedData.length > 0;
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 验证 PEM 格式的私钥
     *
     * @param privkey 私钥
     * @return 是否是有效的私钥
     */
    private static boolean isValidRSAPrivateKey(String privkey) {
        try {
            // 读取文件内容
            InputStream is = new ByteArrayInputStream(privkey.getBytes(StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder pemContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                pemContent.append(line.trim());
            }

            // 检查私钥格式是否正确
            if (!pemContent.toString().startsWith("-----BEGIN PRIVATE KEY-----") ||
                    !pemContent.toString().endsWith("-----END PRIVATE KEY-----")) {
                return false;
            }

            // 去除PEM头尾
            String key = pemContent.toString().replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");

            // 尝试将其解析为私钥
            byte[] keyBytes = java.util.Base64.getDecoder().decode(key);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");  // 使用RSA算法
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            PrivateKey privateKey = keyFactory.generatePrivate(keySpec);

            // 验证私钥是否有效，通过进行简单操作，例如私钥加密或解密
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update("test".getBytes());
            byte[] signedData = signature.sign();

            return signedData != null && signedData.length > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
