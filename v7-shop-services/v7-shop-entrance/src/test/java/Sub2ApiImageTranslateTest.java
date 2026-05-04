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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class Sub2ApiImageTranslateTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String API_KEY = "proxypal-local";
    private static final String BASE_URL = "http://localhost:8317/v1";
    private static final String IMAGE_PATH = "C:\\Users\\83850\\Pictures\\商品\\d038beb2a7224402b424a49ddf4f5164.webp";
    private static final String OUTPUT_PATH = "C:\\Users\\83850\\Pictures\\商品\\d038beb2a7224402b424a49ddf4f5164.out.png";

    private static final String MODEL = "";
    private static final String IMAGE_FIELD = "image[]";
    private static final String TARGET_LANGUAGE = "Simplified Chinese";
    private static final String USER_AGENT =
            "codex-tui/0.125.0 (Windows 10.0.26100; x86_64) WarpTerminal (codex-tui; 0.125.0)";
    private static final String PROXY_HOST = "127.0.0.1";
    private static final int PROXY_PORT = 8800;

    @Test
    public void translateImageToChinese() throws Exception {
        String apiKey = API_KEY;
        String baseUrl = BASE_URL;
        Path imagePath = Path.of(IMAGE_PATH);
        Path outputPath = outputPath(imagePath);

        String mimeType = Files.probeContentType(imagePath);
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = "image/png";
        }

        String boundary = "----v7shop-" + UUID.randomUUID();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(normalizeBaseUrl(baseUrl) + "/images/edits"))
                .timeout(Duration.ofMinutes(5))
                .header("Authorization", "Bearer " + apiKey)
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(multipartBody(boundary, Map.of(
                        "model", MODEL,
                        "prompt", buildPrompt(TARGET_LANGUAGE)
                ), IMAGE_FIELD, imagePath, mimeType))
                .build();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .proxy(ProxySelector.of(new InetSocketAddress(PROXY_HOST, PROXY_PORT)))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Sub2API image edit failed, status="
                    + response.statusCode() + ", body=" + response.body());
        }

        JsonNode root = OBJECT_MAPPER.readTree(response.body());
        JsonNode data = root.path("data");
        if (data.isArray() && !data.isEmpty()) {
            JsonNode first = data.get(0);
            String b64Json = first.path("b64_json").asText(null);
            if (b64Json != null && !b64Json.isBlank()) {
                byte[] imageBytes = Base64.getDecoder().decode(b64Json);
                Files.write(outputPath, imageBytes);
                System.out.println("Sub2API translated image saved: " + outputPath.toAbsolutePath()
                        + ", size=" + imageBytes.length + " bytes");
                return;
            }

            String url = first.path("url").asText(null);
            if (url != null && !url.isBlank()) {
                System.out.println("Sub2API returned image url: " + url);
                return;
            }
        }

        String outputText = root.path("output_text").asText(null);
        if (outputText == null || outputText.isBlank()) {
            outputText = root.path("text").asText(null);
        }
        if (outputText != null && !outputText.isBlank()) {
            System.out.println("Sub2API returned text: " + outputText);
            return;
        }

        throw new IllegalStateException("Sub2API response does not contain image data: " + response.body());
    }

    private static HttpRequest.BodyPublisher multipartBody(String boundary,
                                                           Map<String, String> textParts,
                                                           String imageField,
                                                           Path imagePath,
                                                           String mimeType) throws IOException {
        List<byte[]> bytes = new ArrayList<>();
        for (Map.Entry<String, String> entry : textParts.entrySet()) {
            bytes.add(("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"" + entry.getKey() + "\"\r\n\r\n"
                    + entry.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        bytes.add(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + imageField + "\"; filename=\""
                + imagePath.getFileName() + "\"\r\n"
                + "Content-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        bytes.add(Files.readAllBytes(imagePath));
        bytes.add(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return HttpRequest.BodyPublishers.ofByteArrays(bytes);
    }

    private static String buildPrompt(String targetLanguageName) {
        return """
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
                                  Translate ONLY the translatable overlay text into %s.
                                
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
                                  - Use concise, natural %s suitable for e-commerce.
                                
                                  Output rules (VERY IMPORTANT):
                                  - Output ONLY the final translated image.
                                  - Do NOT output any text, explanation, markdown, or JSON.
                                  - Do NOT describe the translation.
                                  - Do NOT list extracted text.
                                  - The response must contain only the image.
                                
                                - If uncertain whether some text is overlay text or embedded product/package text, 
                                leave it unchanged.
                """.formatted(targetLanguageName, targetLanguageName);
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.endsWith("/v1") ? normalized : normalized + "/v1";
    }

    private static Path outputPath(Path imagePath) {
        if (OUTPUT_PATH != null && !OUTPUT_PATH.isBlank()) {
            return Path.of(OUTPUT_PATH);
        }
        String fileName = imagePath.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        String outputFileName = dotIndex > 0
                ? fileName.substring(0, dotIndex) + ".zh" + fileName.substring(dotIndex)
                : fileName + ".zh.png";
        Path parent = imagePath.getParent();
        return parent == null ? Path.of(outputFileName) : parent.resolve(outputFileName);
    }
}
