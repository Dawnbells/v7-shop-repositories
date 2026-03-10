import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
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

//    private static final Pattern DATE_PATTERN = Pattern.compile(
//            "/(\\d{8})/"
//    );

    private static final Pattern DATE_PATTERN = Pattern.compile(
            "/Uploads//(\\d{6})/"
    );

    private static final HashMap<String, String> dateCaches = new HashMap<>();
    public static List<String> excludeUrls = List.of(
            "mribbon.live",
            "rlerygalj.lol",
            "bquotidians.lol"
    );
    public static List<String> pageUrls = List.of(
            "https://wvanv.monster/?m=Item&a=show&id=145171",
            "https://pconvictq.lol/?m=Item&a=show&id=169325",
            "https://lginalori.club/?m=Item&a=show&id=198653",
            "https://wvacularv.monster/?m=Item&a=show&id=184245",
            "https://combinationkl.shop/?m=Item&a=show&id=196111",
            "https://ecorirym.lol/?m=Item&a=show&id=190844",
            "https://chteeneigk.monster/?m=Item&a=show&id=195438",
            "https://nrepelo.lol/?m=Item&a=show&id=196634",
            "https://yretort.monster/?m=Item&a=show&id=178433",
            "https://eresidual.lol/?m=Item&a=show&id=178781",
            "https://npropitiateo.lol/?m=Item&a=show&id=163363",
            "https://rdwond.shop/?m=Item&a=show&id=61370",
            "https://osanguinew.lol/?m=Item&a=show&id=184336",
            "https://gnope.monster/?m=Item&a=show&id=196715",
            "https://gnope.monster/?m=Item&a=show&id=196719",
            "https://gnope.monster/?m=Item&a=show&id=196716",
            "https://ttivateculf.lol/?m=Item&a=show&id=192952",
            "https://dstitutionconl.lol/?m=Item&a=show&id=176439",
            "https://sllenwoob.lol/?m=Item&a=show&id=178127",
            "https://oinemarw.monster/?m=Item&a=show&id=193473",
            "https://nsino.lol/?m=Item&a=show&id=152584",
            "https://oirectindw.lol/?m=Item&a=show&id=193647",
            "https://astwrix.monster/?m=Item&a=show&id=177599",
            "https://oirectindw.lol/?m=Item&a=show&id=198608",
            "https://osanguinew.lol/?m=Item&a=show&id=181626",
            "https://chanicalmeck.shop/?m=Item&a=show&id=194869",
            "https://zlegcd.life/?m=Item&a=show&id=117160",
            "https://zlegcd.life/?m=Item&a=show&id=100020",
            "https://zlegcd.life/?m=Item&a=show&id=58901",
            "https://zlegcd.life/?m=Item&a=show&id=117215",
            "https://zlegcd.life/?m=Item&a=show&id=138669",
            "https://lrock.live/?m=Item&a=show&id=107263",
            "https://cowpd.shop/?m=Item&a=show&id=151484",
            "https://yhydrate.lol/?m=Item&a=show&id=136915",
            "https://cowpd.shop/?m=Item&a=show&id=78801",
            "https://cowpd.shop/?m=Item&a=show&id=78801",
            "https://cowpd.shop/?m=Item&a=show&id=151485",
            "https://cowpd.shop/?m=Item&a=show&id=78626",
            "https://cowpd.shop/?m=Item&a=show&id=151484",
            "https://conditionkl.shop/?m=Item&a=show&id=136717",
            "https://dmaintainuy.monster/?m=Item&a=show&id=100111",
            "https://gvengefuld.monster/?m=Item&a=show&id=154874",
            "https://qsirz.monster/?m=Item&a=show&id=80752",
            "https://dmaintainuy.monster/?m=Item&a=show&id=145592",
            "https://dmaintainuy.monster/?m=Item&a=show&id=137891",
            "https://msecuret.lol/?m=Item&a=show&id=127012",
            "https://zresign.life/?m=Item&a=show&id=93772",
            "https://vrapiddr.lol/?m=Item&a=show&id=107463",
            "https://zresign.life/?m=Item&a=show&id=93775",
            "https://msecuret.lol/?m=Item&a=show&id=96926",
            "https://zresign.life/?m=Item&a=show&id=93773",
            "https://vrapiddr.lol/?m=Item&a=show&id=107462",
            "https://msecuret.lol/?m=Item&a=show&id=102217",
            "https://msecuret.lol/?m=Item&a=show&id=91393",
            "https://msecuret.lol/?m=Item&a=show&id=199973",
            "https://msecuret.lol/?m=Item&a=show&id=146077",
            "https://osteerw.life/?m=Item&a=show&id=95711",
            "https://osteerw.life/?m=Item&a=show&id=161674",
            "https://lexpel.monster/?m=Item&a=show&id=130380",
            "https://zdetachedc.lol/?m=Item&a=show&id=130107",
            "https://gtureculd.lol/?m=Item&a=show&id=179154",
            "https://wupwiodsv.monster/?m=Item&a=show&id=101599",
            "https://vpanoramag.monster/?m=Item&a=show&id=158215",
            "https://vpanoramag.monster/?m=Item&a=show&id=172218",
            "https://vpanoramag.monster/?m=Item&a=show&id=172217",
            "https://wupwiodsv.monster/?m=Item&a=show&id=101599",
            "https://psafetyet.monster/?m=Item&a=show&id=157545",
            "https://psafetyet.monster/?m=Item&a=show&id=105122",
            "https://mresear.life/?m=Item&a=show&id=188906",
            "https://mresear.life/?m=Item&a=show&id=161839",
            "https://mresear.life/?m=Item&a=show&id=157531",
            "https://mresear.life/?m=Item&a=show&id=140121",
            "https://psafetyet.monster/?m=Item&a=show&id=84881",
            "https://mresear.life/?m=Item&a=show&id=199928",
            "https://mribbon.live/?m=Item&a=show&id=78449",
            "https://mribbon.live/?m=Item&a=show&id=151395",
            "https://zobeyfg.lol/?m=Item&a=show&id=151403",
            "https://tapkizingf.lol/?m=Item&a=show&id=115004",
            "https://zobeyfg.lol/?m=Item&a=show&id=151406",
            "https://zobeyfg.lol/?m=Item&a=show&id=85312",
            "https://zobeyfg.lol/?m=Item&a=show&id=194681",
            "https://zobeyfg.lol/?m=Item&a=show&id=174637",
            "https://zobeyfg.lol/?m=Item&a=show&id=151404",
            "https://zobeyfg.lol/?m=Item&a=show&id=77474",
            "https://zobeyfg.lol/?m=Item&a=show&id=166712",
            "https://psplendidh.live/?m=Item&a=show&id=151376",
            "https://psplendidh.live/?m=Item&a=show&id=48533",
            "https://psplendidh.live/?m=Item&a=show&id=69035",
            "https://psplendidh.live/?m=Item&a=show&id=45095",
            "https://oreserve.life/?m=Item&a=show&id=151492",
            "https://oreserve.life/?m=Item&a=show&id=93893",
            "https://halfht.shop/?m=Item&a=show&id=62605",
            "https://halfht.shop/?m=Item&a=show&id=172306",
            "https://spancreasb.monster/?m=Item&a=show&id=166322",
            "https://grocerybr.shop/?m=Item&a=show&id=105040",
            "https://spancreasb.monster/?m=Item&a=show&id=166453",
            "https://grocerybr.shop/?m=Item&a=show&id=152490",
            "https://spancreasb.monster/?m=Item&a=show&id=166298",
            "https://koutlookcv.life/?m=Item&a=show&id=149913",
            "https://affairyr.shop/?m=Item&a=show&id=106882",
            "https://affairyr.shop/?m=Item&a=show&id=158878",
            "https://brottenet.monster/?m=Item&a=show&id=80463",
            "https://koutlookcv.life/?m=Item&a=show&id=85265",
            "https://wgeran.monster/?m=Item&a=show&id=198428",
            "https://zpoundcomc.lol/?m=Item&a=show&id=191542",
            "https://zpoundcomc.lol/?m=Item&a=show&id=192427",
            "https://metysaft.monster/?m=Item&a=show&id=198362",
            "https://sestyhonb.monster/?m=Item&a=show&id=193130",
            "https://nntgiao.monster/?m=Item&a=show&id=185841",
            "https://rrenouncej.lol/?m=Item&a=show&id=165464",
            "https://zpoundcomc.lol/?m=Item&a=show&id=194978",
            "https://fhouseoir.shop/?m=Item&a=show&id=49221",
            "https://ubarbarousu.monster/?m=Item&a=show&id=122614",
            "https://csyringek.monster/?m=Item&a=show&id=152326",
            "https://bquotidians.lol/?m=Item&a=show&id=185291",
            "https://qcomplimentz.lol/?m=Item&a=show&id=120405",
            "https://ftrush.shop/?m=Item&a=show&id=198399",
            "https://zwershoc.club/?m=Item&a=show&id=198574",
            "https://hidyll.monster/?m=Item&a=show&id=147273",
            "https://aicipateant.monster/?m=Item&a=show&id=200620",
            "https://fehidh.lol/?m=Item&a=show&id=201036",
            "https://cicusdelk.monster/?m=Item&a=show&id=187121",
            "https://deritalexpl.monster/?m=Item&a=show&id=200734",
            "https://rsideout.monster/?m=Item&a=show&id=201039",
            "https://fehidh.lol/?m=Item&a=show&id=201038",
            "https://aishjewx.monster/?m=Item&a=show&id=199470",
            "https://nrtexeo.lol/?m=Item&a=show&id=201043",
            "https://berpows.lol/?m=Item&a=show&id=198809",
            "https://kffclir.lol/?m=Item&a=show&id=183410",
            "https://uhasizeempu.lol/?m=Item&a=show&id=180917",
            "https://sementpav.lol/?m=Item&a=show&id=194717",
            "https://zinductc.lol/?m=Item&a=show&id=200684",
            "https://zinductc.lol/?m=Item&a=show&id=200010",
            "https://zinductc.lol/?m=Item&a=show&id=200512",
            "https://zinductc.lol/?m=Item&a=show&id=200011",
            "https://ssuremeab.shop/?m=Item&a=show&id=198554",
            "https://ssuremeab.shop/?m=Item&a=show&id=196280",
            "https://veignforg.monster/?m=Item&a=show&id=200683",
            "https://veignforg.monster/?m=Item&a=show&id=200685",
            "https://veignforg.monster/?m=Item&a=show&id=200039",
            "https://veignforg.monster/?m=Item&a=show&id=197536",
            "https://veignforg.monster/?m=Item&a=show&id=197374",
            "https://uernalintu.monster/?m=Item&a=show&id=195457",
            "https://uernalintu.monster/?m=Item&a=show&id=196653",
            "https://dventionconl.monster/?m=Item&a=show&id=176632",
            "https://rlerygalj.lol/?m=Item&a=show&id=188775",
            "https://zinductc.lol/?m=Item&a=show&id=200016",
            "https://pemilq.lol/?m=Item&a=show&id=194884",
            "https://pemilq.lol/?m=Item&a=show&id=195867",
            "https://pemilq.lol/?m=Item&a=show&id=194328",
            "https://ftliaaush.monster/?m=Item&a=show&id=185259"

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
//                if (is404(imgUrl, pageUrl)) {
                    String date = extractDate(imgUrl);
//                    System.out.println(index + "/" + pageUrls.size() + ": Progress: " + i + "/" + images.size() + " >> " + imgUrl + " >> " + date);
                    if(date != null) {
                        result.add(date);
                    }
//                } else {
//                    System.out.println(index + "/" + pageUrls.size() + ": Progress: " + i + "/" + images.size() + " >> " + imgUrl);
//                }
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
        Set<String> result = new HashSet<>();
        for (int i = 0; i < pageUrls.size(); i++) {
            final String pageUrl = pageUrls.get(i);
            final int index = i;
            executor.submit(() -> {
                try {
                    if (excludeUrls.contains(HttpsCertCheckerWithProxy.extractHost(pageUrl))) {
                        return;
                    }
//                    System.out.println("SCAN[" + index + "]: " + pageUrl);

                    Set<String> broken = extract404Images(index, pageUrl);
                    result.addAll(broken);
                    for (String date : broken) {
//                        System.out.println(index + "/" + pageUrls.size());
//                        System.err.printf(
//                                """
//                                        rsync -avz \\
//                                        -e "ssh -i /home/15880411714/服务器备份/多维度/172.82.18.26/www/dwd_prod.pem" \\
//                                        --rsync-path="mkdir -p /www/dwd/wwwroot/eu.upcmc.shop/shop/ueditor/php/upload/image/%s/ && rsync" \\
//                                        "/home/15880411714/服务器备份/多维度/172.82.18.26/www/wwwroot/eu.upcmc.shop/shop/ueditor/php/upload/image/%s/" \\
//                                        root@188.245.204.231:/www/dwd/wwwroot/eu.upcmc.shop/shop/ueditor/php/upload/image/%s/
//                                        """,
//                                date, date, date
//                        );
//                        System.err.printf(
//                                """
//                                        rsync -avz \\
//                                        -e "ssh -i /home/15880411714/服务器备份/多维度/172.82.18.26/www/dwd_prod.pem" \\
//                                        --rsync-path="mkdir -p /www/ht/wwwroot/huiten_eu.rrret.shop/shop/ueditor/php/upload/image/%s/ && rsync" \\
//                                        "/home/15880411714/服务器备份/辉腾/104.160.20.74/www/wwwroot/huiten_eu.rrret.shop/shop/ueditor/php/upload/image/%s/" \\
//                                        root@188.245.204.231:/www/ht/wwwroot/huiten_eu.rrret.shop/shop/ueditor/php/upload/image/%s/
//                                        """,
//                                date, date, date
//                        );
//                        System.out.println(date);
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
            result.stream().toList().stream().sorted().forEach(System.out::println);
            System.out.printf("b = " + b);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

    }
}
