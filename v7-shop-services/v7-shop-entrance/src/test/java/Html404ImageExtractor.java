import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Html404ImageExtractor {

    private static final Pattern DATE_PATTERN = Pattern.compile(
            "/(\\d{8})/"
    );
    public static List<String> pageUrls = List.of(
            "https://bslippages.lol/?m=Item&a=show&id=200967"
    );

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public static Set<String> extract404Images(int index, String pageUrl) {
        Set<String> result = new HashSet<>();

        try {
            Document doc = Jsoup.connect(pageUrl)
                    .timeout(8000)
                    .userAgent("Mozilla/5.0")
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)
                    .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 8800)))
                    .get();

            Elements images = doc.select("img[src], img[data-src]");

            for (int i = 0; i < images.size(); i++) {
                Element img = images.get(i);
                String imgUrl = img.hasAttr("src")
                                ? img.absUrl("src")
                                : img.absUrl("data-src");

                if (imgUrl.isEmpty()) {
                    continue;
                }
                if (is404(imgUrl, pageUrl)) {
                    String date = extractDate(imgUrl);
//                    System.out.println(index + "/" + pageUrls.size() + ": Progress: " + i + "/" + images.size() + " >> " + imgUrl + " >> " + date);
                    result.add(date);
                } else {
//                    System.out.println(index + "/" + pageUrls.size() + ": Progress: " + i + "/" + images.size() + " >> " + imgUrl);
                }
                System.out.flush();
            }
        } catch (Exception e) {
            System.err.println(index + "/" + pageUrls.size() + ": PAGE FAILED: " + pageUrl);
            e.printStackTrace();
        }

        return result;
    }

    public static String extractDate(String imageUrl) {
        Matcher matcher = DATE_PATTERN.matcher(imageUrl);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static boolean is404(String imageUrl, String referer) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(imageUrl))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", referer)
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<Void> resp =
                    httpClient.send(request, HttpResponse.BodyHandlers.discarding());

            if (resp.statusCode() == 404 || resp.statusCode() == 410) {
                return true;
            }

            // CDN 常见：403 禁 HEAD → fallback GET
            if (resp.statusCode() == 403) {
                return checkByGet(imageUrl, referer);
            }

            return false;

        } catch (Exception e) {
            // 网络异常 ≠ 404
            return false;
        }
    }

    private static boolean checkByGet(String imageUrl, String referer) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(imageUrl))
                    .GET()
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", referer)
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<Void> resp =
                    httpClient.send(request, HttpResponse.BodyHandlers.discarding());

            return resp.statusCode() == 404 || resp.statusCode() == 410;
        } catch (Exception e) {
            return false;
        }
    }

    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

        for (int i = 0; i < pageUrls.size(); i++) {
            final String pageUrl = pageUrls.get(i);
            final int index = i;
            executor.submit(() -> {
                try {
                    System.out.println("SCAN[" + index + "]: " + pageUrl);

                    Set<String> broken = extract404Images(index, pageUrl);

                    for (String date : broken) {
                        System.out.println(index + "/" + pageUrls.size());
                        System.err.printf(
                                "rsync -a --partial --append-verify --info=progress2,name0 " +
                                "-e \"ssh -i /www/dwd_prod.pem\" " +
                                "--rsync-path=\"sudo rsync\" " +
                                "/www/wwwroot/huiten_eu.rrret.shop/shop/ueditor/php/upload/image/%s/ " +
                                "ubuntu@51.77.134.119:/www/ht/wwwroot/huiten_eu.rrret.shop/shop/ueditor/php/upload/image/%s/%n",
                                date, date
                        );
                    }

                } catch (Exception e) {
                    System.err.println("SCAN FAILED: " + pageUrl);
                    e.printStackTrace();
                }
            });
        }

        // 不再接收新任务
        executor.shutdown();

        // 等待所有任务完成
        try {
            boolean b = executor.awaitTermination(1, TimeUnit.HOURS);
            System.out.printf("b = " + b);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

    }
}
