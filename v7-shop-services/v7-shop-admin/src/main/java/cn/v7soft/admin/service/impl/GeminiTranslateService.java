package cn.v7soft.admin.service.impl;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GeminiTranslateService {

    private static final String MODEL = "gemini-3.1-flash-image-preview";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Client client;

    public GeminiTranslateService(@Value("${gemini.api-key}") String apiKey) {
        this.client = Client.builder().apiKey(apiKey).build();
    }

    /**
     * 批量翻译短文本（title/summary/spec name/value 等）。
     * 将多段文本打包为一次 API 请求，返回翻译后的文本列表（与输入等长、顺序对应）。
     */
    public List<String> translateTexts(List<String> texts, String targetLanguageName) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        String numberedTexts = IntStream.range(0, texts.size())
                .mapToObj(i -> (i + 1) + ". " + texts.get(i))
                .collect(Collectors.joining("\n"));

        String prompt = """
                You are a professional e-commerce product translator.
                Translate the following numbered texts to %s.
                Rules:
                - Keep the same numbering format
                - Translate each line independently
                - Do NOT add, remove, or reorder any lines
                - Keep brand names, model numbers, and units unchanged
                - If a text is already in the target language, keep it as is
                - Output ONLY a JSON array of strings, in the same order as input
                - The array must have exactly %d elements

                Texts:
                %s
                """.formatted(targetLanguageName, texts.size(), numberedTexts);

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(Schema.builder()
                        .type(Type.Known.ARRAY)
                        .items(Schema.builder().type(Type.Known.STRING).build())
                        .build())
                .temperature(0.1f)
                .build();

        GenerateContentResponse response = client.models.generateContent(MODEL, prompt, config);
        String json = response.text();
        try {
            List<String> result = OBJECT_MAPPER.readValue(json, new TypeReference<List<String>>() {});
            if (result.size() != texts.size()) {
                log.warn("翻译结果数量不匹配: expected={}, actual={}", texts.size(), result.size());
                throw new RuntimeException("翻译结果数量不匹配");
            }
            return result;
        } catch (Exception e) {
            log.error("解析翻译结果失败, json={}", json, e);
            throw new RuntimeException("解析翻译结果失败", e);
        }
    }

    /**
     * 翻译 HTML 富文本。
     * 保留 HTML 标签结构不变，仅翻译可见文本内容。
     */
    public String translateHtml(String html, String targetLanguageName) {
        if (html == null || html.isBlank()) {
            return html;
        }

        Content systemInstruction = Content.fromParts(Part.fromText("""
                You are a professional HTML content translator for e-commerce product pages.
                You MUST follow these rules strictly:
                - Translate ONLY the visible text content to %s
                - Preserve ALL HTML tags, attributes, and structure exactly as they are
                - Do NOT modify any tag names, class names, style attributes, src URLs, or href URLs
                - Do NOT modify <img> tags or their src/alt attributes in any way
                - Do NOT add or remove any HTML tags
                - Keep brand names, product model numbers unchanged
                - Output the complete translated HTML, nothing else
                """.formatted(targetLanguageName)));

        GenerateContentConfig config = GenerateContentConfig.builder()
                .systemInstruction(systemInstruction)
                .temperature(0.1f)
                .build();

        String prompt = "Translate the text content in this HTML to "
                + targetLanguageName + ":\n\n" + html;

        GenerateContentResponse response = client.models.generateContent(MODEL, prompt, config);
        return response.text();
    }

    /**
     * 翻译图片中的叠加设计文字。
     * 单次调用完成分析+翻译：无需翻译时返回 null，需要翻译时返回新图片字节数组。
     */
    public byte[] translateImage(byte[] imageBytes, String mimeType, String targetLanguageName) {
        Content content = Content.fromParts(
                Part.fromBytes(imageBytes, mimeType),
                Part.fromText("""
                        First, analyze whether this image contains any readable text.
                        Then classify detected text into two categories:

                        1. Translatable overlay text:
                           Text clearly added as part of the design or layout, such as
                           titles, descriptions, feature callouts, promotional text,
                           labels, or other explanatory text placed on top of the image.

                        2. Non-translatable embedded text:
                           Text that is physically part of the photographed product
                           itself or its packaging, such as printed text on the product,
                           bottle, box, bag, label, tag, sticker, manual shown in the
                           photo, engraved text, embossed text, or any text naturally
                           appearing inside the original photographed object.

                        Rules:

                        - If the image contains NO text at all:
                          Return ONLY the text: "NO_TRANSLATION_NEEDED"
                          Do NOT generate or modify any image.

                        - If the image contains ONLY non-translatable embedded text:
                          Return ONLY the text: "NO_TRANSLATION_NEEDED"
                          Do NOT generate or modify any image.

                        - If the image contains translatable overlay text:
                          Translate ONLY the translatable overlay text into %s.

                          This is a text-only edit task.

                          Requirements:
                          - Keep background, product, colors, and layout exactly unchanged
                          - Do NOT translate or modify any text that is part of the actual
                            product or packaging shown in the photo
                          - Only replace translatable overlay text; leave embedded
                            product/package text untouched
                          - Do NOT redraw or recreate the image
                          - Preserve original font style, size, alignment, and spacing
                            as closely as possible
                          - Ensure translated text fits naturally within original text areas
                          - Use concise, natural %s suitable for e-commerce
                          - Final output should look identical to the original image
                            except for translated overlay text

                        - If uncertain whether some text is overlay or embedded,
                          leave it unchanged.
                        """.formatted(targetLanguageName, targetLanguageName))
        );

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseModalities(List.of("TEXT", "IMAGE"))
                .temperature(0.2f)
                .build();

        GenerateContentResponse response = client.models.generateContent(MODEL, content, config);

        for (Part part : Objects.requireNonNull(response.parts())) {
            if (part.inlineData().isPresent()) {
                var blob = part.inlineData().get();
                if (blob.data().isPresent()) {
                    return blob.data().get();
                }
            }
        }
        log.info("图片无需翻译, 模型响应: {}", response.text());
        return null;
    }
}
