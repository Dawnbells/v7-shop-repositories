package cn.v7soft.admin.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.entities.primary.Product;
import cn.v7soft.dao.entities.primary.ProductSpecification;
import cn.v7soft.dao.entities.primary.ProductSpecificationAttributes;
import cn.v7soft.dao.enums.InvokeMode;
import cn.v7soft.dao.enums.TranslationContentType;

public final class TokenCostCalculator {

    private static final Pattern IMG_ID_PATTERN = Pattern.compile("/multimedia/([0-9]+)");

    private TokenCostCalculator() {
    }

    private static final BigDecimal MILLION = new BigDecimal("1000000");
    private static final BigDecimal CREDITS_PER_USD = new BigDecimal("1000");

    // Batch 模式价格（每百万 token，USD）
    private static final BigDecimal BATCH_INPUT_PRICE = new BigDecimal("0.25");
    private static final BigDecimal BATCH_TEXT_OUTPUT_PRICE = new BigDecimal("1.50");
    private static final BigDecimal BATCH_IMAGE_OUTPUT_PRICE = new BigDecimal("30.00");

    // Standard = Batch * 2
    private static final BigDecimal STANDARD_MULTIPLIER = new BigDecimal("2");

    // 图片 token 档位：按较大边向上取档
    private static final int[][] IMAGE_TOKEN_TIERS = {
            {512, 747},
            {1024, 1120},
            {2048, 1680},
            {4096, 2520},
            };
    // private static final int MIN_EXTRA_OVERHEAD_TOKENS = 300;
    // private static final int MAX_EXTRA_OVERHEAD_TOKENS = 600;

    /**
     * 根据图片最大边长度（宽/高的较大值）计算档位 token。
     */
    public static int resolveImageTierTokens(int maxDimension) {
        for (int[] tier : IMAGE_TOKEN_TIERS) {
            if (maxDimension <= tier[0]) {
                return tier[1];
            }
        }
        return IMAGE_TOKEN_TIERS[IMAGE_TOKEN_TIERS.length - 1][1];
    }

    /**
     * 计算图片的业务 prompt token = 档位 token + 80。
     */
    public static int imageBusinessPromptTokens(int maxDimension) {
        return 718;
    }

    private static int getExtraOverheadTokens() {
        // return ThreadLocalRandom.current().nextInt(MIN_EXTRA_OVERHEAD_TOKENS, MAX_EXTRA_OVERHEAD_TOKENS + 1);
        return 80;
    }

    /**
     * 计算图片的业务 completion token = 档位 token + 50。
     */
    public static int imageBusinessCompletionTokens(int maxDimension) {
        return resolveImageTierTokens(maxDimension) + getExtraOverheadTokens();
    }

    /**
     * 计算翻译费用。
     *
     * @param contentType      内容类型
     * @param invokeMode       调用模式
     * @param promptTokens     输入 token
     * @param completionTokens 输出 token（candidates）
     * @param thinkingTokens   思考 token（计入输出，按文本输出单价）
     */
    public static BigDecimal calculateCost(TranslationContentType contentType, InvokeMode invokeMode,
                                           int promptTokens, int completionTokens, int thinkingTokens) {
        BigDecimal multiplier = invokeMode == InvokeMode.STANDARD ? STANDARD_MULTIPLIER : BigDecimal.ONE;
        BigDecimal inputPrice = BATCH_INPUT_PRICE.multiply(multiplier);
        BigDecimal textOutputPrice = BATCH_TEXT_OUTPUT_PRICE.multiply(multiplier);
        BigDecimal imageOutputPrice = BATCH_IMAGE_OUTPUT_PRICE.multiply(multiplier);

        BigDecimal inputCost = BigDecimal.valueOf(promptTokens)
                .multiply(inputPrice).divide(MILLION, 6, RoundingMode.HALF_UP);

        BigDecimal outputCost;
        if (contentType == TranslationContentType.IMAGE) {
            // 图片有输出：completion 按图片输出价，thinking 按文本输出价
            outputCost = BigDecimal.valueOf(completionTokens)
                    .multiply(imageOutputPrice).divide(MILLION, 6, RoundingMode.HALF_UP)
                    .add(BigDecimal.valueOf(thinkingTokens)
                                 .multiply(textOutputPrice).divide(MILLION, 6, RoundingMode.HALF_UP));
        } else {
            // 文本/HTML：completion + thinking 都按文本输出价
            outputCost = BigDecimal.valueOf(completionTokens + thinkingTokens)
                    .multiply(textOutputPrice).divide(MILLION, 6, RoundingMode.HALF_UP);
        }

        return inputCost.add(outputCost);
    }

    /**
     * 将 USD 金额转换为 AI Credits（向上取整）。1 Credit = $0.001
     */
    public static int usdToCredits(BigDecimal usd) {
        if (usd == null || usd.signum() <= 0) {
            return 0;
        }
        return usd.multiply(CREDITS_PER_USD).setScale(0, RoundingMode.CEILING).intValue();
    }

    /**
     * 根据产品内容预估所需 AI Credits。
     *
     * @param textEstimateTokens  文本token数
     * @param imageEstimateTokens 图片token数量
     * @param mode                调用模式
     */
    public static int estimateCredits(int textEstimateTokens, int imageEstimateTokens, InvokeMode mode) {
        BigDecimal textCost = calculateCost(TranslationContentType.TEXT, mode,
                                            textEstimateTokens, textEstimateTokens, 0);

        BigDecimal imgCost = calculateCost(TranslationContentType.IMAGE, mode,
                                           imageEstimateTokens, imageEstimateTokens, 0);

        int credits = usdToCredits(textCost.add(imgCost));
        return Math.max(credits, 1);
    }

    /**
     * 预估产品中所有图片token数
     *
     * @param product 产品
     * @return 产品中所有图片的预估Token数
     */
    public static int getProductImageEstimateTokens(Product product) {
        int imageCount = 0;
        if (product.getImageFiles() != null) {
            for (MultimediaFile img : product.getImageFiles()) {
                if (img != null && !"gif".equalsIgnoreCase(img.getSuffix())) {
                    imageCount++;
                }
            }
        }
        for (ProductSpecification spec : product.getSpecificationList()) {
            MultimediaFile specImg = spec.getSpecificationImage();
            if (specImg != null && !"gif".equalsIgnoreCase(specImg.getSuffix())) {
                imageCount++;
            }
            for (ProductSpecificationAttributes attr : spec.getAttributes()) {
                MultimediaFile attrImg = attr.getMultimediaFile();
                if (attrImg != null && !"gif".equalsIgnoreCase(attrImg.getSuffix())) {
                    imageCount++;
                }
            }
        }
        if (product.getIntroduction() != null) {
            Matcher matcher = IMG_ID_PATTERN.matcher(product.getIntroduction());
            while (matcher.find()) {
                imageCount++;
            }
        }
        return imageCount * estimateImageTokens();
    }

    /**
     * 预估产品中所有文本
     *
     * @param product 产品
     * @return 产品中所有文本token数量
     */
    public static int getProductTextEstimateTokens(Product product) {
        int textToken = 0;
        if (product.getTitle() != null) {
            textToken += estimateTextTokens(product.getTitle());
        }
        if (product.getSummary() != null) {
            textToken += estimateTextTokens(product.getSummary());
        }
        if (product.getWaybillProductName() != null) {
            textToken += estimateTextTokens(product.getWaybillProductName());
        }
        for (ProductSpecification spec : product.getSpecificationList()) {
            for (ProductSpecificationAttributes attr : spec.getAttributes()) {
                if (attr.getName() != null) {
                    textToken += estimateTextTokens(attr.getName());
                }
                if (attr.getValue() != null) {
                    textToken += estimateTextTokens(attr.getValue());
                }
            }
        }
        textToken += estimateTextTokens(product.getIntroduction());
        return textToken;
    }

    /**
     * 估算图片token数量
     *
     * @return 图片token数量
     */
    public static int estimateImageTokens() {
        return 718;
    }

    /**
     * 估算文本token数量
     *
     * @param text 文本内容
     * @return 文本token数量
     */
    public static int estimateTextTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        double tokenCount = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // 1. 判断是否为中文字符 (根据 Unicode 范围)
            if (isChinese(c)) {
                // Gemini 对中文分词较细，通常 1 个汉字 ≈ 1.2~1.5 个 token
                tokenCount += 1.5;
            } else if (Character.isWhitespace(c)) {
                // 2. 判断是否为空格或换行
                tokenCount += 0.2;
            } else {
                // 3. 英文与数字, 英文通常 4 个字符 1 个 token
                tokenCount += 0.25;
            }
        }
        // 向上取整，并预留 10% 的安全边际以防溢出
        return (int) Math.ceil(tokenCount * 1.1);
    }

    /**
     * 是否是中文字符
     *
     * @param c 字符
     * @return 是否是中文字符
     */
    private static boolean isChinese(char c) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        return ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
               || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
               || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A;
    }

    public static void main(String[] args) {
        String test = "你好 Gemini! This is a test.";
        System.out.println("估算 Token 数量: " + estimateTextTokens(test));
    }
}
