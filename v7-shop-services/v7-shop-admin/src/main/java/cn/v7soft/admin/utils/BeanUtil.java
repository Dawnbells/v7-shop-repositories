package cn.v7soft.admin.utils;

import java.lang.reflect.Field;
import java.util.List;

public class BeanUtil {
    public static <T> T trimStringFields(T obj) {
        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (field.getType() == String.class) {
                field.setAccessible(true);
                try {
                    String val = (String) field.get(obj);
                    if (val != null) {
                        field.set(obj, val.trim());
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        return obj;
    }
    public static <T> List<T> trimStringFields(List<T> list) {
        for (T obj : list) {
            trimStringFields(obj);
        }
        return list;
    }

}
