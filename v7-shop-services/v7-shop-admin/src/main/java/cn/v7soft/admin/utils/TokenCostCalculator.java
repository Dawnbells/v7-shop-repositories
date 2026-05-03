package cn.v7soft.admin.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.v7soft.dao.entities.primary.AiAccount;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.entities.primary.Product;
import cn.v7soft.dao.entities.primary.ProductSpecification;
import cn.v7soft.dao.entities.primary.ProductSpecificationAttributes;
import cn.v7soft.dao.enums.AiBillingPriceUnit;
import cn.v7soft.dao.enums.TranslationContentType;

public final class TokenCostCalculator {

    private static final Pattern IMG_ID_PATTERN = Pattern.compile("/multimedia/([0-9]+)");

    private TokenCostCalculator() {
    }

    private static final BigDecimal MILLION = new BigDecimal("1000000");
    private static final BigDecimal THOUSAND = new BigDecimal("1000");
    private static final BigDecimal CREDITS_PER_USD = new BigDecimal("1000");

    private static final int[][] IMAGE_TOKEN_TIERS = {
            {512, 747},
            {1024, 1120},
            {2048, 1680},
            {4096, 2520},
            };

    public static int resolveImageTierTokens(int maxDimension) {
        for (int[] tier : IMAGE_TOKEN_TIERS) {
            if (maxDimension <= tier[0]) {
                return tier[1];
            }
        }
        return IMAGE_TOKEN_TIERS[IMAGE_TOKEN_TIERS.length - 1][1];
    }

    public static int imageBusinessPromptTokens(int maxDimension) {
        return 718;
    }

    private static int getExtraOverheadTokens() {
        return 80;
    }

    public static int imageBusinessCompletionTokens(int maxDimension) {
        return resolveImageTierTokens(maxDimension) + getExtraOverheadTokens();
    }

    /**
     * 将配置的单价按计费单位换算为「金额」：与 token 数量相乘前的系数。
     */
    private static BigDecimal amountForUnits(int quantity, BigDecimal unitPrice, AiBillingPriceUnit unit) {
        if (quantity <= 0 || unitPrice == null) {
            return BigDecimal.ZERO;
        }
        AiBillingPriceUnit u = unit != null ? unit : AiBillingPriceUnit.PER_1M_TOKENS;
        return switch (u) {
            case PER_1M_TOKENS -> BigDecimal.valueOf(quantity).multiply(unitPrice).divide(MILLION, 6, RoundingMode.HALF_UP);
            case PER_1K_TOKENS -> BigDecimal.valueOf(quantity).multiply(unitPrice).divide(THOUSAND, 6, RoundingMode.HALF_UP);
            case PER_IMAGE, PER_VIDEO -> unitPrice;
            case PER_1K_IMAGES -> unitPrice.multiply(BigDecimal.valueOf(quantity)).divide(THOUSAND, 6, RoundingMode.HALF_UP);
            case PER_MINUTE, PER_SECOND ->
                    BigDecimal.valueOf(quantity).multiply(unitPrice).divide(MILLION, 6, RoundingMode.HALF_UP);
        };
    }

    /**
     * 计算翻译费用（USD），按 AiAccount 配置的输入/输出单价；thinking 始终按文本输出单价计费。
     */
    public static BigDecimal calculateCost(TranslationContentType contentType, AiAccount account,
                                           int promptTokens, int completionTokens, int thinkingTokens) {
        if (account == null) {
            return BigDecimal.ZERO;
        }

        if (contentType == TranslationContentType.IMAGE) {
            BigDecimal inputCost = amountForUnits(promptTokens, account.getImageInputPrice(),
                    account.getImageInputPriceUnit());
            BigDecimal completionCost = amountForUnits(completionTokens, account.getImageOutputPrice(),
                    account.getImageOutputPriceUnit());
            BigDecimal thinkingCost = amountForUnits(thinkingTokens, account.getTextOutputPrice(),
                    account.getTextOutputPriceUnit());
            return inputCost.add(completionCost).add(thinkingCost);
        }

        BigDecimal inputCost = amountForUnits(promptTokens, account.getTextInputPrice(),
                account.getTextInputPriceUnit());
        BigDecimal outputCost = amountForUnits(completionTokens + thinkingTokens, account.getTextOutputPrice(),
                account.getTextOutputPriceUnit());
        return inputCost.add(outputCost);
    }

    public static int usdToCredits(BigDecimal usd) {
        if (usd == null || usd.signum() <= 0) {
            return 0;
        }
        return usd.multiply(CREDITS_PER_USD).setScale(0, RoundingMode.CEILING).intValue();
    }

    public static int estimateCredits(int textEstimateTokens, int imageEstimateTokens, AiAccount account) {
        BigDecimal textCost = calculateCost(TranslationContentType.TEXT, account,
                                            textEstimateTokens, textEstimateTokens, 0);

        BigDecimal imgCost = calculateCost(TranslationContentType.IMAGE, account,
                                           imageEstimateTokens, imageEstimateTokens, 0);

        int credits = usdToCredits(textCost.add(imgCost));
        return Math.max(credits, 1);
    }

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

    public static int estimateImageTokens() {
        return 718;
    }

    public static int estimateTextTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        double tokenCount = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isChinese(c)) {
                tokenCount += 1.5;
            } else if (Character.isWhitespace(c)) {
                tokenCount += 0.2;
            } else {
                tokenCount += 0.25;
            }
        }
        return (int) Math.ceil(tokenCount * 1.1);
    }

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
