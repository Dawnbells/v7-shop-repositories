package cn.v7soft.dao.enums;

public enum AiProvider {
    TURBOFLOW_GEMINI,
    GEMINI_OFFICIAL_BATCH,
    GEMINI_OFFICIAL_STANDARD;

    public boolean isTurboFlow() {
        return this == TURBOFLOW_GEMINI;
    }

    public boolean isGeminiOfficial() {
        return this == GEMINI_OFFICIAL_BATCH || this == GEMINI_OFFICIAL_STANDARD;
    }
}
