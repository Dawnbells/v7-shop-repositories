import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import cn.v7soft.common.dto.SSLCertificateInfo;
import cn.v7soft.common.utils.SslCertificateUtil;

public class CertificateChecker {
    public static void main(String[] args) throws Exception {
        boolean validPEMPrivateKey = SslCertificateUtil.isValidPEMPrivateKey("""
                -----BEGIN PRIVATE KEY-----
                 MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgPfo+890u2v99KU3n
                 lDPg6YueI6kSvg42KqXYLxLmrCqhRANCAAS1ciQJk7zXQM3f2Fa+3ExlByt3gkz2
                 t4Ss0RSiPo8I9wCCU5nqIEuzdPhf/jyubTAydzTEyvgxP2fGVFDo5DU5
                 -----END PRIVATE KEY-----
                """);
        System.out.println("valid privkey: " + validPEMPrivateKey);

        SSLCertificateInfo sslCertificateInfo = SslCertificateUtil.parseCertificate("""
                -----BEGIN CERTIFICATE-----
                MIIDhDCCAwugAwIBAgISBNAJnvGdByYTMlalLiVgwbY1MAoGCCqGSM49BAMDMDIx
                CzAJBgNVBAYTAlVTMRYwFAYDVQQKEw1MZXQncyBFbmNyeXB0MQswCQYDVQQDEwJF
                NjAeFw0yNDExMjcwNTU0NTJaFw0yNTAyMjUwNTU0NTFaMBcxFTATBgNVBAMMDCou
                dGtsZWUuc2hvcDBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABNlk25+Y9LBHKDux
                OapR3iOA36uMZRQdmT0c4WLA2Y38qbkUBhVAE5pjfguQPmj5dk5vuRt5SD6UQMXT
                Lj4S7P+jggIaMIICFjAOBgNVHQ8BAf8EBAMCB4AwHQYDVR0lBBYwFAYIKwYBBQUH
                AwEGCCsGAQUFBwMCMAwGA1UdEwEB/wQCMAAwHQYDVR0OBBYEFB3Oc5cxiizfX/b4
                ZH4659H963HZMB8GA1UdIwQYMBaAFJMnRpgDqVFojpjWxEJI2yO/WJTSMFUGCCsG
                AQUFBwEBBEkwRzAhBggrBgEFBQcwAYYVaHR0cDovL2U2Lm8ubGVuY3Iub3JnMCIG
                CCsGAQUFBzAChhZodHRwOi8vZTYuaS5sZW5jci5vcmcvMCMGA1UdEQQcMBqCDCou
                dGtsZWUuc2hvcIIKdGtsZWUuc2hvcDATBgNVHSAEDDAKMAgGBmeBDAECATCCAQQG
                CisGAQQB1nkCBAIEgfUEgfIA8AB1AM8RVu7VLnyv84db2Wkum+kacWdKsBfsrAHS
                W3fOzDsIAAABk2xlXiUAAAQDAEYwRAIgIzXRe/US/COMjdjSWXE54AbvoBdQJ1XG
                pQKG21lKlKsCICKMfPPd8JYcm/R7hgxl/4rFvWqLNh2PEm0A1akp4oQNAHcAE0rf
                GrWYQgl4DG/vTHqRpBa3I0nOWFdq367ap8Kr4CIAAAGTbGVe9QAABAMASDBGAiEA
                i7x1kUaAtHzqtpFXUQio0fV7tXGp63BVD94Xwclw6HoCIQCrZocQojv550o8XFJv
                dlbAdMVE1HaA92CgHbyuxStQkTAKBggqhkjOPQQDAwNnADBkAjB5jZi+OwQOwk7f
                aVL0tdmzzltIQkyywSjCYx6anQ4InGfE9W15A9hdYcWNkFPxwHQCMA/qIcKiTX9U
                eTp0nN7AmXx5YXJN5o6ysHWh6zZXhfcHIrbYeR7fdQ515NHnXqCddQ==
                -----END CERTIFICATE-----
                -----BEGIN CERTIFICATE-----
                MIIEVzCCAj+gAwIBAgIRALBXPpFzlydw27SHyzpFKzgwDQYJKoZIhvcNAQELBQAw
                TzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2Vh
                cmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwHhcNMjQwMzEzMDAwMDAw
                WhcNMjcwMzEyMjM1OTU5WjAyMQswCQYDVQQGEwJVUzEWMBQGA1UEChMNTGV0J3Mg
                RW5jcnlwdDELMAkGA1UEAxMCRTYwdjAQBgcqhkjOPQIBBgUrgQQAIgNiAATZ8Z5G
                h/ghcWCoJuuj+rnq2h25EqfUJtlRFLFhfHWWvyILOR/VvtEKRqotPEoJhC6+QJVV
                6RlAN2Z17TJOdwRJ+HB7wxjnzvdxEP6sdNgA1O1tHHMWMxCcOrLqbGL0vbijgfgw
                gfUwDgYDVR0PAQH/BAQDAgGGMB0GA1UdJQQWMBQGCCsGAQUFBwMCBggrBgEFBQcD
                ATASBgNVHRMBAf8ECDAGAQH/AgEAMB0GA1UdDgQWBBSTJ0aYA6lRaI6Y1sRCSNsj
                v1iU0jAfBgNVHSMEGDAWgBR5tFnme7bl5AFzgAiIyBpY9umbbjAyBggrBgEFBQcB
                AQQmMCQwIgYIKwYBBQUHMAKGFmh0dHA6Ly94MS5pLmxlbmNyLm9yZy8wEwYDVR0g
                BAwwCjAIBgZngQwBAgEwJwYDVR0fBCAwHjAcoBqgGIYWaHR0cDovL3gxLmMubGVu
                Y3Iub3JnLzANBgkqhkiG9w0BAQsFAAOCAgEAfYt7SiA1sgWGCIpunk46r4AExIRc
                MxkKgUhNlrrv1B21hOaXN/5miE+LOTbrcmU/M9yvC6MVY730GNFoL8IhJ8j8vrOL
                pMY22OP6baS1k9YMrtDTlwJHoGby04ThTUeBDksS9RiuHvicZqBedQdIF65pZuhp
                eDcGBcLiYasQr/EO5gxxtLyTmgsHSOVSBcFOn9lgv7LECPq9i7mfH3mpxgrRKSxH
                pOoZ0KXMcB+hHuvlklHntvcI0mMMQ0mhYj6qtMFStkF1RpCG3IPdIwpVCQqu8GV7
                s8ubknRzs+3C/Bm19RFOoiPpDkwvyNfvmQ14XkyqqKK5oZ8zhD32kFRQkxa8uZSu
                h4aTImFxknu39waBxIRXE4jKxlAmQc4QjFZoq1KmQqQg0J/1JF8RlFvJas1VcjLv
                YlvUB2t6npO6oQjB3l+PNf0DpQH7iUx3Wz5AjQCi6L25FjyE06q6BZ/QlmtYdl/8
                ZYao4SRqPEs/6cAiF+Qf5zg2UkaWtDphl1LKMuTNLotvsX99HP69V2faNyegodQ0
                LyTApr/vT01YPE46vNsDLgK+4cL6TrzC/a4WcmF5SRJ938zrv/duJHLXQIku5v0+
                EwOy59Hdm0PT/Er/84dDV0CSjdR/2XuZM3kpysSKLgD1cKiDA+IRguODCxfO9cyY
                Ig46v9mFmBvyH04=
                -----END CERTIFICATE-----
                                
                """);
        System.out.println(JSONUtil.toJsonPrettyStr(sslCertificateInfo));
//        analyzeFromUrl();
    }

    private static void analyzeFromUrl() {
        // 请求URL
        String url = "https://www.ssleye.com/ssltool/cer_check_hander";

        // 设置请求头
        HttpResponse response = HttpRequest.post(url)
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6")
                .header("Cache-Control", "no-cache")
                .header("Connection", "keep-alive")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Cookie", "Hm_lvt_34a942241ce2f6c20e8a5dc3069deee3=1732890307; Hm_lpvt_34a942241ce2f6c20e8a5dc3069deee3=1732890307; HMACCOUNT=A6E0DAB9251D0F5A; _aihecong_chat_address=%7B%22city%22%3A%22%E7%A6%8F%E5%B7%9E%22%2C%22region%22%3A%22%E7%A6%8F%E5%BB%BA%22%2C%22country%22%3A%22%E4%B8%AD%E5%9B%BD%22%7D; _aihecong_chat_visibility=true")
                .header("Origin", "https://www.ssleye.com")
                .header("Pragma", "no-cache")
                .header("Referer", "https://www.ssleye.com/ssltool/cer_check.html")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-origin")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("sec-ch-ua", "\"Microsoft Edge\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"")
                .header("sec-ch-ua-mobile", "?0")
                .header("sec-ch-ua-platform", "\"Windows\"")
                .form("cer_content", """
                        -----BEGIN CERTIFICATE-----
                        MIIDhDCCAwugAwIBAgISBNAJnvGdByYTMlalLiVgwbY1MAoGCCqGSM49BAMDMDIx
                        CzAJBgNVBAYTAlVTMRYwFAYDVQQKEw1MZXQncyBFbmNyeXB0MQswCQYDVQQDEwJF
                        NjAeFw0yNDExMjcwNTU0NTJaFw0yNTAyMjUwNTU0NTFaMBcxFTATBgNVBAMMDCou
                        dGtsZWUuc2hvcDBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABNlk25+Y9LBHKDux
                        OapR3iOA36uMZRQdmT0c4WLA2Y38qbkUBhVAE5pjfguQPmj5dk5vuRt5SD6UQMXT
                        Lj4S7P+jggIaMIICFjAOBgNVHQ8BAf8EBAMCB4AwHQYDVR0lBBYwFAYIKwYBBQUH
                        AwEGCCsGAQUFBwMCMAwGA1UdEwEB/wQCMAAwHQYDVR0OBBYEFB3Oc5cxiizfX/b4
                        ZH4659H963HZMB8GA1UdIwQYMBaAFJMnRpgDqVFojpjWxEJI2yO/WJTSMFUGCCsG
                        AQUFBwEBBEkwRzAhBggrBgEFBQcwAYYVaHR0cDovL2U2Lm8ubGVuY3Iub3JnMCIG
                        CCsGAQUFBzAChhZodHRwOi8vZTYuaS5sZW5jci5vcmcvMCMGA1UdEQQcMBqCDCou
                        dGtsZWUuc2hvcIIKdGtsZWUuc2hvcDATBgNVHSAEDDAKMAgGBmeBDAECATCCAQQG
                        CisGAQQB1nkCBAIEgfUEgfIA8AB1AM8RVu7VLnyv84db2Wkum+kacWdKsBfsrAHS
                        W3fOzDsIAAABk2xlXiUAAAQDAEYwRAIgIzXRe/US/COMjdjSWXE54AbvoBdQJ1XG
                        pQKG21lKlKsCICKMfPPd8JYcm/R7hgxl/4rFvWqLNh2PEm0A1akp4oQNAHcAE0rf
                        GrWYQgl4DG/vTHqRpBa3I0nOWFdq367ap8Kr4CIAAAGTbGVe9QAABAMASDBGAiEA
                        i7x1kUaAtHzqtpFXUQio0fV7tXGp63BVD94Xwclw6HoCIQCrZocQojv550o8XFJv
                        dlbAdMVE1HaA92CgHbyuxStQkTAKBggqhkjOPQQDAwNnADBkAjB5jZi+OwQOwk7f
                        aVL0tdmzzltIQkyywSjCYx6anQ4InGfE9W15A9hdYcWNkFPxwHQCMA/qIcKiTX9U
                        eTp0nN7AmXx5YXJN5o6ysHWh6zZXhfcHIrbYeR7fdQ515NHnXqCddQ==
                        -----END CERTIFICATE-----
                        -----BEGIN CERTIFICATE-----
                        MIIEVzCCAj+gAwIBAgIRALBXPpFzlydw27SHyzpFKzgwDQYJKoZIhvcNAQELBQAw
                        TzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2Vh
                        cmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwHhcNMjQwMzEzMDAwMDAw
                        WhcNMjcwMzEyMjM1OTU5WjAyMQswCQYDVQQGEwJVUzEWMBQGA1UEChMNTGV0J3Mg
                        RW5jcnlwdDELMAkGA1UEAxMCRTYwdjAQBgcqhkjOPQIBBgUrgQQAIgNiAATZ8Z5G
                        h/ghcWCoJuuj+rnq2h25EqfUJtlRFLFhfHWWvyILOR/VvtEKRqotPEoJhC6+QJVV
                        6RlAN2Z17TJOdwRJ+HB7wxjnzvdxEP6sdNgA1O1tHHMWMxCcOrLqbGL0vbijgfgw
                        gfUwDgYDVR0PAQH/BAQDAgGGMB0GA1UdJQQWMBQGCCsGAQUFBwMCBggrBgEFBQcD
                        ATASBgNVHRMBAf8ECDAGAQH/AgEAMB0GA1UdDgQWBBSTJ0aYA6lRaI6Y1sRCSNsj
                        v1iU0jAfBgNVHSMEGDAWgBR5tFnme7bl5AFzgAiIyBpY9umbbjAyBggrBgEFBQcB
                        AQQmMCQwIgYIKwYBBQUHMAKGFmh0dHA6Ly94MS5pLmxlbmNyLm9yZy8wEwYDVR0g
                        BAwwCjAIBgZngQwBAgEwJwYDVR0fBCAwHjAcoBqgGIYWaHR0cDovL3gxLmMubGVu
                        Y3Iub3JnLzANBgkqhkiG9w0BAQsFAAOCAgEAfYt7SiA1sgWGCIpunk46r4AExIRc
                        MxkKgUhNlrrv1B21hOaXN/5miE+LOTbrcmU/M9yvC6MVY730GNFoL8IhJ8j8vrOL
                        pMY22OP6baS1k9YMrtDTlwJHoGby04ThTUeBDksS9RiuHvicZqBedQdIF65pZuhp
                        eDcGBcLiYasQr/EO5gxxtLyTmgsHSOVSBcFOn9lgv7LECPq9i7mfH3mpxgrRKSxH
                        pOoZ0KXMcB+hHuvlklHntvcI0mMMQ0mhYj6qtMFStkF1RpCG3IPdIwpVCQqu8GV7
                        s8ubknRzs+3C/Bm19RFOoiPpDkwvyNfvmQ14XkyqqKK5oZ8zhD32kFRQkxa8uZSu
                        h4aTImFxknu39waBxIRXE4jKxlAmQc4QjFZoq1KmQqQg0J/1JF8RlFvJas1VcjLv
                        YlvUB2t6npO6oQjB3l+PNf0DpQH7iUx3Wz5AjQCi6L25FjyE06q6BZ/QlmtYdl/8
                        ZYao4SRqPEs/6cAiF+Qf5zg2UkaWtDphl1LKMuTNLotvsX99HP69V2faNyegodQ0
                        LyTApr/vT01YPE46vNsDLgK+4cL6TrzC/a4WcmF5SRJ938zrv/duJHLXQIku5v0+
                        EwOy59Hdm0PT/Er/84dDV0CSjdR/2XuZM3kpysSKLgD1cKiDA+IRguODCxfO9cyY
                        Ig46v9mFmBvyH04=
                        -----END CERTIFICATE-----
                        """)  // 这里替换为实际的证书内容
                .execute();

        // 打印返回的结果
        System.out.println(response.body());
    }
}
