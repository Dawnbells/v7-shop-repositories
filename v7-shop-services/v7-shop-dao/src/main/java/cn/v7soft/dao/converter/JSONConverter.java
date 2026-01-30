package cn.v7soft.dao.converter;



import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class JSONConverter implements AttributeConverter<JSONObject, String>  {

    @Override
    public String convertToDatabaseColumn(JSONObject attribute) {
        if (attribute == null) {
            return "";
        }
        try {
            return JSONUtil.toJsonStr(attribute);
        } catch (Exception e) {
            throw new RuntimeException("Error converting List<String> to JSON", e);
        }
    }

    @Override
    public JSONObject convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return new JSONObject();
        }
        try {
            return JSONUtil.parseObj(dbData);
        } catch (Exception e) {
            throw new RuntimeException("Error converting JSON to List<String>", e);
        }
    }
}
