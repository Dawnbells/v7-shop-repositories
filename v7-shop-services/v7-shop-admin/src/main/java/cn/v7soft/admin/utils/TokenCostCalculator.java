package cn.v7soft.admin.utils;

import cn.v7soft.dao.enums.InvokeMode;
import cn.v7soft.dao.enums.TranslationContentType;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class TokenCostCalculator {

    private TokenCostCalculator() {}

    private static final BigDecimal MILLION = new BigDecimal("1000000");

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
    private static final int EXTRA_OVERHEAD_TOKENS = 50;

    /**
     * 根据图片最大边长度（宽/高的较大值）计算档位 token。
     */
    public static int resolveImageTierTokens(int maxDimension) {
        for (int[] tier : IMAGE_TOKEN_TIERS) {
            if (maxDimension <= tier[0]) return tier[1];
        }
        return IMAGE_TOKEN_TIERS[IMAGE_TOKEN_TIERS.length - 1][1];
    }

    /**
     * 计算图片的业务 prompt token = 档位 token + 50。
     */
    public static int imageBusinessPromptTokens(int maxDimension) {
        return resolveImageTierTokens(maxDimension) + EXTRA_OVERHEAD_TOKENS;
    }

    /**
     * 计算图片的业务 completion token = 档位 token + 50。
     */
    public static int imageBusinessCompletionTokens(int maxDimension) {
        return resolveImageTierTokens(maxDimension) + EXTRA_OVERHEAD_TOKENS;
    }

    /**
     * 计算翻译费用。
     *
     * @param contentType     内容类型
     * @param invokeMode      调用模式
     * @param promptTokens    输入 token
     * @param completionTokens 输出 token（candidates）
     * @param thinkingTokens  思考 token（计入输出，按文本输出单价）
     * @param hasImageOutput  图片翻译是否有图片输出（仅 contentType=IMAGE 时有意义）
     */
    public static BigDecimal calculateCost(TranslationContentType contentType, InvokeMode invokeMode,
                                           int promptTokens, int completionTokens, int thinkingTokens,
                                           boolean hasImageOutput) {
        BigDecimal multiplier = invokeMode == InvokeMode.STANDARD ? STANDARD_MULTIPLIER : BigDecimal.ONE;
        BigDecimal inputPrice = BATCH_INPUT_PRICE.multiply(multiplier);
        BigDecimal textOutputPrice = BATCH_TEXT_OUTPUT_PRICE.multiply(multiplier);
        BigDecimal imageOutputPrice = BATCH_IMAGE_OUTPUT_PRICE.multiply(multiplier);

        BigDecimal inputCost = BigDecimal.valueOf(promptTokens)
                .multiply(inputPrice).divide(MILLION, 6, RoundingMode.HALF_UP);

        BigDecimal outputCost;
        if (contentType == TranslationContentType.IMAGE && hasImageOutput) {
            // 图片有输出：completion 按图片输出价，thinking 按文本输出价
            outputCost = BigDecimal.valueOf(completionTokens)
                    .multiply(imageOutputPrice).divide(MILLION, 6, RoundingMode.HALF_UP)
                    .add(BigDecimal.valueOf(thinkingTokens)
                            .multiply(textOutputPrice).divide(MILLION, 6, RoundingMode.HALF_UP));
        } else if (contentType == TranslationContentType.IMAGE) {
            // 图片无输出（2b 场景）：completion 仍按图片输出价（业务虚拟 token），thinking=0
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
}
