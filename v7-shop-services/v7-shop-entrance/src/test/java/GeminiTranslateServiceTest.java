import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import cn.v7soft.admin.service.impl.GeminiTranslateService;
import cn.v7soft.entrance.V7ShopEntranceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = V7ShopEntranceApplication.class)
public class GeminiTranslateServiceTest {

    private final GeminiTranslateService geminiTranslateService;

    @Autowired
    public GeminiTranslateServiceTest(GeminiTranslateService geminiTranslateService) {
        this.geminiTranslateService = geminiTranslateService;
    }

    @Test
    public void testTranslateTexts() {
        List<String> texts = List.of(
                "Wireless Bluetooth Headphones",
                "High Quality Stainless Steel Water Bottle",
                "USB-C Fast Charging Cable 2m"
        );
        List<String> result = geminiTranslateService.translateTexts(texts, "Slovenščina");
        System.out.println("=== translateTexts 结果 ===");
        for (int i = 0; i < texts.size(); i++) {
            System.out.println(texts.get(i) + " -> " + result.get(i));
        }
    }

    @Test
    public void testTranslateHtml() {
        String html = """
                <div class="product-detail">
                    <h1>Portable Bluetooth Speaker</h1>
                    <p class="desc">Experience crystal clear sound with our latest portable speaker.
                       Perfect for outdoor adventures and indoor gatherings.</p>
                    <ul>
                        <li>Battery Life: 12 hours</li>
                        <li>Waterproof Rating: IPX7</li>
                        <li>Weight: 350g</li>
                    </ul>
                    <img src="https://example.com/speaker.jpg" alt="speaker" />
                </div>
                """;
        String result = geminiTranslateService.translateHtml(html, "Slovenščina");
        System.out.println("=== translateHtml 结果 ===");
        System.out.println(result);
    }

    @Test
    public void testTranslateImage() throws IOException {
        String inputFile = "E:\\V7Soft\\backup\\20250117\\processed\\1\\1103721578496\\IMAGE\\root\\20241225\\80fb02a17e6c4e2b9c4bf28a7eafa9cd.jpg";
        String outputFile = inputFile.replace(".jpg", ".out.jpg");

        Path inputPath = Path.of(inputFile);
        byte[] imageBytes = Files.readAllBytes(inputPath);
        System.out.println("读取图片: " + inputFile + ", 大小: " + imageBytes.length + " bytes");

        byte[] result = geminiTranslateService.translateImage(imageBytes, "image/jpeg", "Simplified Chinese");
        if (result != null) {
            Files.write(Path.of(outputFile), result);
            System.out.println("=== translateImage 结果: 翻译后图片已保存, 大小=" + result.length + " bytes, 路径=" + outputFile + " ===");
        } else {
            System.out.println("=== translateImage 结果: 图片无需翻译 ===");
        }
    }
}
