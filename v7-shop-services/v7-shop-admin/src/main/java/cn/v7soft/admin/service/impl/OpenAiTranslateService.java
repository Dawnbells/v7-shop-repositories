package cn.v7soft.admin.service.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import cn.v7soft.admin.service.AiTranslationClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.entities.primary.AiAccount;
import cn.v7soft.dao.enums.AiProvider;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OpenAiTranslateService implements AiTranslationClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final int TIMEOUT_TEXT_MS = 60_000;
    private static final int TIMEOUT_HTML_MS = 120_000;
    private static final int TIMEOUT_IMAGE_MS = 180_000;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public boolean supports(AiProvider provider) {
        return provider == AiProvider.OPENAI;
    }

    @Override
    public String translateTextRaw(AiAccount account, String text, String targetLanguageName,
                                   Consumer<GeminiTranslateService.TokenUsage> usageCallback) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String prompt = """
                You are a professional e-commerce product translator.
                Translate the following text to %s.
                Rules:
                - Keep brand names, model numbers, and units unchanged
                - If the text is already in the target language, keep it as is
                - Output ONLY the translated text, nothing else
                
                Text:
                %s
                """.formatted(targetLanguageName, text);

        long start = System.currentTimeMillis();
        JsonNode response = createResponse(account, prompt, TIMEOUT_TEXT_MS);
        emitTokenUsage(response, start, usageCallback);
        return extractOutputText(response);
    }

    @Override
    public String translateHtmlRaw(AiAccount account, String html, String targetLanguageName,
                                   Consumer<GeminiTranslateService.TokenUsage> usageCallback) {
        if (html == null || html.isBlank()) {
            return html;
        }
        String prompt = """
                You are a professional HTML content translator for e-commerce product pages.
                You MUST follow these rules strictly:
                - Translate ONLY the visible text content to %s
                - Preserve ALL HTML tags, attributes, and structure exactly as they are
                - Do NOT modify any tag names, class names, style attributes, src URLs, or href URLs
                - Do NOT modify <img> tags or their src/alt attributes in any way
                - Do NOT add or remove any HTML tags
                - Keep brand names, product model numbers unchanged
                - Output the complete translated HTML, nothing else
                
                Translate the text content in this HTML to %s:
                
                %s
                """.formatted(targetLanguageName, targetLanguageName, html);

        long start = System.currentTimeMillis();
        JsonNode response = createResponse(account, prompt, TIMEOUT_HTML_MS);
        emitTokenUsage(response, start, usageCallback);
        return extractOutputText(response);
    }

    @Override
    public byte[] translateImageRaw(AiAccount account, byte[] imageBytes, String mimeType, String targetLanguageName,
                                    Consumer<GeminiTranslateService.TokenUsage> usageCallback) {
        String prompt = """
                First, analyze whether the image contains any readable text.
                
                Then classify detected text into two categories:
                
                1. Translatable overlay text:
                   text that is clearly added as part of the design or layout, such as titles, descriptions, feature callouts, promotional text, labels, or other explanatory text placed on top of the image.
                
                2. Non-translatable embedded text:
                   text that is physically part of the photographed product itself or its packaging, such as printed text on the product, bottle, box, bag, label, tag, sticker, manual shown in the photo, engraved text, embossed text, or any text naturally appearing inside the original photographed object.
                
                Rules:
                - If the image contains no translatable overlay text, do not generate an image.
                - If the image contains translatable overlay text, translate only that overlay text into %s.
                - Keep background, product, colors, and layout exactly unchanged.
                - Do not translate or modify product/package text.
                - Preserve original font style, size, alignment, and spacing as much as possible.
                - Output only the final translated image when an image edit is needed.
                """.formatted(targetLanguageName);

        long start = System.currentTimeMillis();
        JsonNode response = createImageResponse(account, prompt, imageBytes, mimeType, TIMEOUT_IMAGE_MS);
        emitTokenUsage(response, start, usageCallback);
        String imageBase64 = extractImageBase64(response);
        if (imageBase64 == null || imageBase64.isBlank()) {
            log.info("[openaiTranslateImage] 未返回图片输出，按无需翻译图片处理");
            return null;
        }
        return Base64.getDecoder().decode(imageBase64);
    }

    private JsonNode createResponse(AiAccount account, String prompt, int timeoutMs) {
        ObjectNode body = OBJECT_MAPPER.createObjectNode();
        body.put("model", account.getModel());
        body.put("input", prompt);
        return postResponses(account, body, timeoutMs);
    }

    private JsonNode createImageResponse(AiAccount account, String prompt, byte[] imageBytes, String mimeType, int timeoutMs) {
        ObjectNode body = OBJECT_MAPPER.createObjectNode();
        body.put("model", account.getModel());

        ArrayNode input = body.putArray("input");
        ObjectNode message = input.addObject();
        message.put("role", "user");
        ArrayNode content = message.putArray("content");
        content.addObject().put("type", "input_text").put("text", prompt);
        content.addObject()
                .put("type", "input_image")
                .put("image_url", "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(imageBytes));

        ArrayNode tools = body.putArray("tools");
        tools.addObject().put("type", "image_generation");

        return postResponses(account, body, timeoutMs);
    }

    private JsonNode postResponses(AiAccount account, ObjectNode body, int timeoutMs) {
        try {
            String requestBody = OBJECT_MAPPER.writeValueAsString(body);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(responsesUrl(account)))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Authorization", "Bearer " + account.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody));
            if (account.getUserAgent() != null && !account.getUserAgent().isBlank()) {
                requestBuilder.header("User-Agent", account.getUserAgent());
            }
            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw ClientResponseEnum.PARAMETER_ILLEGAL.newException(
                        "OpenAI请求失败: HTTP " + response.statusCode() + " " + response.body());
            }
            return OBJECT_MAPPER.readTree(response.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw ClientResponseEnum.PARAMETER_ILLEGAL.newException("OpenAI请求失败: " + e.getMessage());
        }
    }

    private String responsesUrl(AiAccount account) {
        String baseUrl = account.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        }
        baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return baseUrl.endsWith("/responses") ? baseUrl : baseUrl + "/responses";
    }

    @Override
    public String getModel(AiAccount account) {
        return account.getModel();
    }

    private String extractOutputText(JsonNode response) {
        JsonNode outputText = response.path("output_text");
        if (outputText.isTextual()) {
            return outputText.asText();
        }
        String text = findTextByType(response.path("output"), "output_text");
        if (text != null) {
            return text;
        }
        return "";
    }

    private String extractImageBase64(JsonNode response) {
        JsonNode output = response.path("output");
        if (!output.isArray()) {
            return null;
        }
        for (JsonNode item : output) {
            if ("image_generation_call".equals(item.path("type").asText()) && item.path("result").isTextual()) {
                return item.path("result").asText();
            }
        }
        return null;
    }

    private String findTextByType(JsonNode node, String type) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (type.equals(node.path("type").asText()) && node.path("text").isTextual()) {
            return node.path("text").asText();
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                String text = findTextByType(child, type);
                if (text != null) {
                    return text;
                }
            }
        } else if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                String text = findTextByType(fields.next().getValue(), type);
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    private void emitTokenUsage(JsonNode response, long startMs, Consumer<GeminiTranslateService.TokenUsage> callback) {
        if (callback == null) {
            return;
        }
        JsonNode usage = response.path("usage");
        if (usage.isMissingNode()) {
            return;
        }
        int promptTokens = usage.path("input_tokens").asInt(0);
        int completionTokens = usage.path("output_tokens").asInt(0);
        int totalTokens = usage.path("total_tokens").asInt(promptTokens + completionTokens);
        int reasoningTokens = usage.path("output_tokens_details").path("reasoning_tokens").asInt(0);
        callback.accept(GeminiTranslateService.TokenUsage.builder()
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .thinkingTokens(reasoningTokens)
                .totalTokens(totalTokens)
                .elapsedMs(System.currentTimeMillis() - startMs)
                .build());
    }
}
