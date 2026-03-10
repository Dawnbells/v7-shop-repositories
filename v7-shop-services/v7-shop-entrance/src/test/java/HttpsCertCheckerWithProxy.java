import javax.net.ssl.*;
import java.net.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class HttpsCertCheckerWithProxy {

    private static final int THREAD_POOL_SIZE = 50;
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 5000;

    // 本地 HTTP 代理
    private static final String PROXY_HOST = "127.0.0.1";
    private static final int PROXY_PORT = 8800;


    private static final SSLSocketFactory sslSocketFactory;

    static {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, null, null);
            sslSocketFactory = context.getSocketFactory();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {

        List<String> pageUrls = Html404ImageExtractor.pageUrls;

        List<String> expiredDomains = findExpiredDomains(pageUrls);

        System.out.println("证书过期域名列表：");
        expiredDomains.forEach(System.out::println);
    }

    public static List<String> findExpiredDomains(List<String> pageUrls) throws InterruptedException {

        Set<String> domains = pageUrls.stream()
                .map(HttpsCertCheckerWithProxy::extractHost)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        List<Future<String>> futures = new ArrayList<>();

        for (String domain : domains) {
            futures.add(executor.submit(() -> {
                if (isCertificateExpired(domain)) {
                    return domain;
                }
                return null;
            }));
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.MINUTES);

        List<String> expired = new ArrayList<>();
        for (Future<String> f : futures) {
            try {
                String result = f.get();
                if (result != null) expired.add(result);
            } catch (Exception ignored) {}
        }

        return expired;
    }

    public static String extractHost(String url) {
        try {
            return new URL(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isCertificateExpired(String domain) {
        try {
            Proxy proxy = new Proxy(Proxy.Type.HTTP,
                                    new InetSocketAddress(PROXY_HOST, PROXY_PORT));

            Socket tunnel = new Socket(proxy);
            tunnel.connect(new InetSocketAddress(domain, 443), CONNECT_TIMEOUT);
            tunnel.setSoTimeout(READ_TIMEOUT);

            SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(
                    tunnel, domain, 443, true);

            // 强制 SNI（避免部分 CDN 证书错误）
            SSLParameters sslParameters = sslSocket.getSSLParameters();
            sslParameters.setServerNames(
                    Collections.singletonList(new SNIHostName(domain)));
            sslSocket.setSSLParameters(sslParameters);

            sslSocket.startHandshake();

            SSLSession session = sslSocket.getSession();
            Certificate[] certs = session.getPeerCertificates();

            X509Certificate cert = (X509Certificate) certs[0];
            Date expireDate = cert.getNotAfter();

            sslSocket.close();

            return expireDate.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
}