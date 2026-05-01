import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class OpusGptImage2Test {

    private static final String API_KEY = "sk-2f17c20fb0da519b01ced7891cef10e948ac948358cc24d669c29da5d6d056a7";
    private static final String BASE_URL = "http://8.211.56.211:8080";
    private static final String MODEL = "gpt-image-2";
    private static final String USER_AGENT =
            "codex-tui/0.125.0 (Windows 10.0.26100; x86_64) WarpTerminal (codex-tui; 0.125.0)";

    private static final String IMAGE_PATH =
            "C:\\Users\\83850\\Pictures\\商品\\d038beb2a7224402b424a49ddf4f5164.webp";

    private static final String PROMPT = """
            First, analyze whether the image contains any readable text.

            Then classify detected text into two categories:

            1. Translatable overlay text:
               text that is clearly added as part of the design or layout, such as titles,
            descriptions, feature callouts, promotional text, labels, or other explanatory
            text placed on top of the image.

            2. Non-translatable embedded text:
               text that is physically part of the photographed product itself or its packaging,
            such as printed text on the product, bottle, box, bag, label, tag, sticker, manual
            shown in the photo, engraved text, embossed text, or any text naturally appearing
            inside the original photographed object.

            Rules:

            - If the image contains NO text:
              Return exactly this text only:
              No text detected, no translation needed.
              Do NOT generate any image.

            - If the image contains ONLY non-translatable embedded text:
              Return exactly this text only:
              Only product/package text detected, no translation needed.
              Do NOT generate any image.

            - If the image contains translatable overlay text:
              Translate ONLY the translatable overlay text into Simplified Chinese.

              This is a strict text-only edit on the image.

              Requirements:
              - Keep background, product, colors, and layout exactly unchanged.
              - Do NOT translate or modify any text that is part of the actual product or
            packaging shown in the photo.
              - Only replace translatable overlay text; leave embedded product/package text
            untouched.
              - Do not redraw or recreate the image.
              - Preserve original font style, size, alignment, and spacing as much as possible.
              - Ensure translated text fits naturally within original text areas.
              - Use concise, natural Simplified Chinese suitable for e-commerce.

              Output rules (VERY IMPORTANT):
              - Output ONLY the final translated image.
              - Do NOT output any text, explanation, markdown, or JSON.
              - Do NOT describe the translation.
              - Do NOT list extracted text.
              - The response must contain only the image.

            - If uncertain whether some text is overlay text or embedded product/package text,
            leave it unchanged.
            """;

    @Test
    public void testTranslateImageViaSub2Api() throws IOException, InterruptedException {
        Path imagePath = Path.of(IMAGE_PATH);
        if (!Files.exists(imagePath)) {
            System.out.println("图片文件不存在: " + IMAGE_PATH);
            return;
        }

        byte[] imageBytes = Files.readAllBytes(imagePath);
        System.out.println("读取图片: " + IMAGE_PATH + ", 大小: " + imageBytes.length + " bytes");

        String boundary = UUID.randomUUID().toString();
        byte[] body = buildMultipartBody(boundary, imageBytes, imagePath.getFileName().toString());

        String url = BASE_URL.replaceAll("/+$", "") + "/v1/images/edits";
        System.out.println("请求 URL: " + url);
        System.out.println("模型: " + MODEL);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .proxy(ProxySelector.of(new InetSocketAddress("127.0.0.1", 8800)))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .header("Authorization", "Bearer " + API_KEY)
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        long start = System.currentTimeMillis();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("响应状态码: " + response.statusCode() + ", 耗时: " + elapsed + "ms");

        if (response.statusCode() != 200) {
            System.out.println("请求失败，响应体:");
            System.out.println(response.body());
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response.body());
        JsonNode dataArray = root.get("data");

        if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) {
            System.out.println("响应中无图片数据，可能是纯文本回复:");
            System.out.println(response.body());
            return;
        }

        JsonNode firstImage = dataArray.get(0);
        String b64Json = firstImage.has("b64_json") ? firstImage.get("b64_json").asText() : null;

        if (b64Json == null || b64Json.isBlank()) {
            System.out.println("响应中无 b64_json 数据:");
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
            return;
        }

        byte[] resultBytes = Base64.getDecoder().decode(b64Json);
        String outputFormat = "png";
        if (firstImage.has("output_format")) {
            outputFormat = firstImage.get("output_format").asText();
        }
        String outputFile = IMAGE_PATH.replace(".webp", ".translated." + outputFormat);
        Files.write(Path.of(outputFile), resultBytes);

        System.out.println("=== 翻译完成 ===");
        System.out.println("输出图片大小: " + resultBytes.length + " bytes");
        System.out.println("输出路径: " + outputFile);

        if (root.has("usage")) {
            System.out.println("Token 使用: " + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root.get("usage")));
        }
    }

    private byte[] buildMultipartBody(String boundary, byte[] imageBytes, String fileName)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        writeField(out, boundary, "model", MODEL);
        writeField(out, boundary, "prompt", PROMPT);
        writeFileField(out, boundary, "image[]", fileName, imageBytes, detectMimeType(fileName));

        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private void writeField(ByteArrayOutputStream out, String boundary, String name, String value)
            throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
                          .getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private void writeFileField(ByteArrayOutputStream out, String boundary, String fieldName,
                                String fileName, byte[] data, String mimeType) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\""
                   + fileName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private String detectMimeType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }
}
