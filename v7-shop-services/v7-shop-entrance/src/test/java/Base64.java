import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ByteUtil;
import cn.hutool.core.util.StrUtil;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;

public class Base64 {
//    public static void main(String[] args) {
//        File file = new File("E:\\Verifone\\Repositories\\SDK\\HeiHeSdk\\sources\\VerifioneCompat\\packer\\compat.dex");
////        File file = new File("E:\\Verifone\\Repositories\\SDK\\HeiHeSdk\\sources\\VerifioneCompat\\app\\build\\intermediates\\dex\\release\\mergeDexRelease\\classes.dex");
//        FileUtil.writeUtf8String(cn.hutool.core.codec.Base64.encode(file),new File("E:\\Verifone\\Repositories\\SDK\\HeiHeSdk\\sources\\VerifioneCompat\\packer\\compat.dex.base64"));
//    }

    public static void main(String[] args) throws Exception {
        String filePath = "E:\\V7Soft\\ssh\\dwd-sync\\certs\\1\\xyzshopee.com\\fullchain.pem";
        String pemContent = FileUtil.readString(filePath, StandardCharsets.UTF_8);

        // 提取证书的 BASE64 编码部分
        String base64Cert = pemContent
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s", "");

        System.out.println(base64Cert);

        // 将 BASE64 内容转换为字节数组
        byte[] certBytes = cn.hutool.core.codec.Base64.decode(base64Cert);

        // 使用 CertificateFactory 生成 X509Certificate 对象
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate certificate = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certBytes));
        // 获取证书的有效期
        Date notAfter = certificate.getNotAfter();
        System.out.println("证书有效期结束：" + notAfter);
    }
}
