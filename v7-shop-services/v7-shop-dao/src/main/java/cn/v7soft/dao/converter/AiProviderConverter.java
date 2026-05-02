package cn.v7soft.dao.converter;

import cn.v7soft.dao.enums.AiProvider;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class AiProviderConverter implements AttributeConverter<AiProvider, String> {

    @Override
    public String convertToDatabaseColumn(AiProvider attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public AiProvider convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return switch (dbData) {
            case "TURBOFLOW" -> AiProvider.TURBOFLOW_GEMINI;
            case "GEMINI" -> AiProvider.GEMINI_OFFICIAL_STANDARD;
            case "OPENAI" -> null;
            default -> AiProvider.valueOf(dbData);
        };
    }
}
