package cn.v7soft.admin.service.ssl;

import cn.hutool.core.io.FileUtil;
import cn.v7soft.common.utils.SslCertificateUtil;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

@Slf4j
@Builder
public class UnsupportedSslCertificateRequester implements ISslCertificateRequester {
    @Override
    public SslResult handleRequestSslCertificate(TopLevelDomain domain, String sslServer) {
        final String targetDir = CERT_CONFIG_DIR + domain.getCompanyId() + "/" + domain.getName() + "/";

        FileUtil.del(targetDir + "fullchain.pem");
        FileUtil.del(targetDir + "privkey.pem");
        FileUtil.writeUtf8String(DEFAULT_FULLCHAIN, targetDir + "fullchain.pem");
        FileUtil.writeUtf8String(DEFAULT_PRIVKEY, targetDir + "privkey.pem");
        return SslResult.builder().build();
    }

    public void checkAndWriteDefault(TopLevelDomain domain) {
        if (domain == null) {
            return;
        }
        final String targetDir = CERT_CONFIG_DIR + domain.getCompanyId() + "/" + domain.getName() + "/";
        try {
            String fullchainPath = targetDir + "fullchain.pem";
            String privkeyPath = targetDir + "privkey.pem";
            String fullchain = FileUtil.readString(fullchainPath, StandardCharsets.UTF_8);
            String privkey = FileUtil.readString(privkeyPath, StandardCharsets.UTF_8);
            SslCertificateUtil.valid(fullchain, privkey);
        } catch (Exception e) {
            log.debug("check and write default error: ", e);
            FileUtil.del(targetDir + "fullchain.pem");
            FileUtil.del(targetDir + "privkey.pem");
            FileUtil.writeUtf8String(DEFAULT_FULLCHAIN, targetDir + "fullchain.pem");
            FileUtil.writeUtf8String(DEFAULT_PRIVKEY, targetDir + "privkey.pem");
        }
    }

    @Override
    public boolean analyzeDomain(TopLevelDomain topLevelDomain, String subName, String cnameRecord) {
        return false;
    }

    private final String DEFAULT_FULLCHAIN = """
            -----BEGIN CERTIFICATE-----
            MIIDiDCCAw2gAwIBAgISA0Yjio5K0NaKWz2ojqgRv1oGMAoGCCqGSM49BAMDMDIx
            CzAJBgNVBAYTAlVTMRYwFAYDVQQKEw1MZXQncyBFbmNyeXB0MQswCQYDVQQDEwJF
            NTAeFw0yNDExMjAwNzAyMTRaFw0yNTAyMTgwNzAyMTNaMBcxFTATBgNVBAMMDCou
            eXBpYnouY2x1YjBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABEYbMYZgyFgmEYb2
            +/AFFCjo6ge3FAtxfZ7HHQHxc626i6tXSbgXTTaZjEWFvQJNMfR4TlMuTeC84AKO
            KKSuwoWjggIcMIICGDAOBgNVHQ8BAf8EBAMCB4AwHQYDVR0lBBYwFAYIKwYBBQUH
            AwEGCCsGAQUFBwMCMAwGA1UdEwEB/wQCMAAwHQYDVR0OBBYEFPGoa89YnrtqJosQ
            UW13G6n6MI85MB8GA1UdIwQYMBaAFJ8rX888IU+dBLftKyzExnCL0tcNMFUGCCsG
            AQUFBwEBBEkwRzAhBggrBgEFBQcwAYYVaHR0cDovL2U1Lm8ubGVuY3Iub3JnMCIG
            CCsGAQUFBzAChhZodHRwOi8vZTUuaS5sZW5jci5vcmcvMCMGA1UdEQQcMBqCDCou
            eXBpYnouY2x1YoIKeXBpYnouY2x1YjATBgNVHSAEDDAKMAgGBmeBDAECATCCAQYG
            CisGAQQB1nkCBAIEgfcEgfQA8gB3AKLjCuRF772tm3447Udnd1PXgluElNcrXhss
            xLlQpEfnAAABk0iWhuYAAAQDAEgwRgIhAMP6lmOSBOhpXCsP4QWfJj/23N41+2XX
            R8R0mkT2o0c0AiEA5APH/rloXAh0F4H3IP5j/dMSQT0Ile7TLyy7vRZPCYgAdwAT
            St8atZhCCXgMb+9MepGkFrcjSc5YV2rfrtqnwqvgIgAAAZNIlpAVAAAEAwBIMEYC
            IQCE91SIHmz0O5J5ayK7MxS2NVLohkXNM8E3JUAY49U+kgIhAKQPk11jn2+zX6/w
            I8kjZsi4hozffsC9HW1ja8KISxxqMAoGCCqGSM49BAMDA2kAMGYCMQCQ5r5sK6YY
            hTur3yrSG0yHY4ojj4l6QoJ3pSD8yovSZ+as+wV4gIbCx/UNwhXFtSwCMQC+3NGl
            ze8ULA0iOvDbyE23hoaolw4t6n9hQ5mvDT5Cc7bAQNJXDCg7XJUkidHW2D0=
            -----END CERTIFICATE-----
            -----BEGIN CERTIFICATE-----
            MIIEVzCCAj+gAwIBAgIRAIOPbGPOsTmMYgZigxXJ/d4wDQYJKoZIhvcNAQELBQAw
            TzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2Vh
            cmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwHhcNMjQwMzEzMDAwMDAw
            WhcNMjcwMzEyMjM1OTU5WjAyMQswCQYDVQQGEwJVUzEWMBQGA1UEChMNTGV0J3Mg
            RW5jcnlwdDELMAkGA1UEAxMCRTUwdjAQBgcqhkjOPQIBBgUrgQQAIgNiAAQNCzqK
            a2GOtu/cX1jnxkJFVKtj9mZhSAouWXW0gQI3ULc/FnncmOyhKJdyIBwsz9V8UiBO
            VHhbhBRrwJCuhezAUUE8Wod/Bk3U/mDR+mwt4X2VEIiiCFQPmRpM5uoKrNijgfgw
            gfUwDgYDVR0PAQH/BAQDAgGGMB0GA1UdJQQWMBQGCCsGAQUFBwMCBggrBgEFBQcD
            ATASBgNVHRMBAf8ECDAGAQH/AgEAMB0GA1UdDgQWBBSfK1/PPCFPnQS37SssxMZw
            i9LXDTAfBgNVHSMEGDAWgBR5tFnme7bl5AFzgAiIyBpY9umbbjAyBggrBgEFBQcB
            AQQmMCQwIgYIKwYBBQUHMAKGFmh0dHA6Ly94MS5pLmxlbmNyLm9yZy8wEwYDVR0g
            BAwwCjAIBgZngQwBAgEwJwYDVR0fBCAwHjAcoBqgGIYWaHR0cDovL3gxLmMubGVu
            Y3Iub3JnLzANBgkqhkiG9w0BAQsFAAOCAgEAH3KdNEVCQdqk0LKyuNImTKdRJY1C
            2uw2SJajuhqkyGPY8C+zzsufZ+mgnhnq1A2KVQOSykOEnUbx1cy637rBAihx97r+
            bcwbZM6sTDIaEriR/PLk6LKs9Be0uoVxgOKDcpG9svD33J+G9Lcfv1K9luDmSTgG
            6XNFIN5vfI5gs/lMPyojEMdIzK9blcl2/1vKxO8WGCcjvsQ1nJ/Pwt8LQZBfOFyV
            XP8ubAp/au3dc4EKWG9MO5zcx1qT9+NXRGdVWxGvmBFRAajciMfXME1ZuGmk3/GO
            koAM7ZkjZmleyokP1LGzmfJcUd9s7eeu1/9/eg5XlXd/55GtYjAM+C4DG5i7eaNq
            cm2F+yxYIPt6cbbtYVNJCGfHWqHEQ4FYStUyFnv8sjyqU8ypgZaNJ9aVcWSICLOI
            E1/Qv/7oKsnZCWJ926wU6RqG1OYPGOi1zuABhLw61cuPVDT28nQS/e6z95cJXq0e
            K1BcaJ6fJZsmbjRgD5p3mvEf5vdQM7MCEvU0tHbsx2I5mHHJoABHb8KVBgWp/lcX
            GWiWaeOyB7RP+OfDtvi2OsapxXiV7vNVs7fMlrRjY1joKaqmmycnBvAq14AEbtyL
            sVfOS66B8apkeFX2NY4XPEYV4ZSCe8VHPrdrERk2wILG3T/EGmSIkCYVUMSnjmJd
            VQD9F6Na/+zmXCc=
            -----END CERTIFICATE-----
            """;
    private final String DEFAULT_PRIVKEY = """
             -----BEGIN PRIVATE KEY-----
             MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgNNSLceepzSMEPjmY
             bvzL9U1N0xjzW70/frF7mIifL9mhRANCAARGGzGGYMhYJhGG9vvwBRQo6OoHtxQL
             cX2exx0B8XOtuourV0m4F002mYxFhb0CTTH0eE5TLk3gvOACjiikrsKF
             -----END PRIVATE KEY-----
            """;
}
