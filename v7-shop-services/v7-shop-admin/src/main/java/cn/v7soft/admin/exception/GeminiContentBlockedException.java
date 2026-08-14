package cn.v7soft.admin.exception;

/**
 * Gemini 返回了不可用的生成结果：prompt 被内容策略拦截（promptFeedback.blockReason 存在），
 * 或候选的终止原因不是正常的 STOP（政策阻断、输出被截断、结构性异常）。
 * <p>
 * 这类结果重试同样的输入必然得到同样的结果，所以调用方应当保留原文/原图并记录 reason，
 * 而不是把 null 或半截译文写进产品、也不是进重试队列。
 * <p>
 * reason 直接取 Gemini 的枚举名（PROHIBITED_CONTENT / SAFETY / MAX_TOKENS 等），
 * 未取到具体枚举时用 EMPTY_RESPONSE。
 */
public class GeminiContentBlockedException extends RuntimeException {

    private final String reason;

    public GeminiContentBlockedException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
