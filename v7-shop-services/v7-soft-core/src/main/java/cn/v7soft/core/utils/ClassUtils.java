package cn.v7soft.core.utils;

import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClassUtils {
    private final static Logger logger = LoggerFactory.getLogger(ClassUtils.class);
    public static boolean isInstanceOf(Object obj, String className) {
        try {
            Class<?> clazz = Class.forName(className);
            return clazz.isInstance(obj);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static Long invokeGetId(Object obj) {
        try {
            // 获取 getId() 方法（假设无参数）
            Method getIdMethod = obj.getClass().getMethod("getId");
            // 调用并返回结果
            return (Long) getIdMethod.invoke(obj);
        } catch (NoSuchMethodException e) {
            logger.error("对象没有 getId() 方法");
        } catch (Exception e) {
            logger.error("调用 getId() 失败: " + e.getMessage());
        }
        return null; // 或抛出异常
    }
}
