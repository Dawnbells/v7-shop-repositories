package cn.v7soft.core.asserts;

import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;

import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import cn.v7soft.core.enums.IResponseEnum;
import cn.v7soft.core.exception.BaseException;

public interface IAsserts extends IResponseEnum {

    /**
     * 创建具体的异常对象
     *
     * @param args      格式化参数
     * @param message   已格式化消息
     * @param throwable 异常信息
     * @return 具体的异常对象
     * @See com.v7soft.common.exception.BusinessException
     * @see ServiceException
     */
    BaseException newSpecialException(Object[] args, String message, Throwable throwable);

    /**
     * 创建异常
     *
     * @param args 参数
     * @return 异常
     */
    default BaseException newException(Object... args) {
        String msg = MessageFormat.format(this.getMessage(), args);
        return newSpecialException(args, msg, null);
    }

    /**
     * 创建异常
     *
     * @param throwable 异常
     * @param args      参数
     * @return 异常
     */
    default BaseException newException(Throwable throwable, Object... args) {
        String msg = MessageFormat.format(this.getMessage(), args);
        return newSpecialException(args, msg, throwable);
    }

    default void assertTrue(Boolean obj, Object... args) {
        if (Boolean.TRUE.equals(obj)) {
            return;
        }
        if (args.length == 0) {
            throw newException();
        }
        throw newException(args);
    }

    /**
     * 断言对象不能为空
     *
     * @param obj 要判断的对象
     */
    default void notNull(Object obj) {
        if (obj == null) {
            throw newException();
        }
    }

    /**
     * 断言对象不为空
     *
     * @param obj  对象
     * @param args 消息格式化参数
     */
    default void notNull(Object obj, Object... args) {
        if (obj == null) {
            throw newException(args);
        }
    }

    /**
     * 断言对象为空
     *
     * @param obj  对象
     * @param args 消息格式化参数
     */
    default void isNull(Object obj, Object... args) {
        if (obj != null) {
            throw newException(args);
        }
    }

    default <T> void notEmpty(List<T> list, Object... args) {
        if (list == null || list.isEmpty()) {
            throw newException(args);
        }
    }

    /**
     * 断言两个对象内容一致
     *
     * @param obj1 比对对象1
     * @param obj2 比对对象2
     * @param args 消息格式化参数
     */
    default void notEquals(Object obj1, Object obj2, Object... args) {
        if (Objects.equals(obj1, obj2)) {
            throw newException(args);
        }
    }

    /**
     * 断言文本内容不为空
     *
     * @param text 文本内容
     * @param args 消息格式化参数
     */
    default void notBlank(String text, Object... args) {
        if (!StringUtils.hasLength(text)) {
            throw newException(args);
        }
    }

    /**
     * 断言文本内存为空
     *
     * @param text 文本内容
     * @param args 消息格式化参数
     */
    default void isBlank(String text, Object... args) {
        if (!StringUtils.hasLength(text)) {
            throw newException(args);
        }
    }


    default  void isTrue(boolean value, Object... args) {
        assertTrue(value, args);
    }

    /**
     * 断言是正整数
     *
     * @param value number
     * @param args  消息格式化参数
     */
    default void isPositive(long value, Object... args) {
        if (value <= 0) {
            throw newException(args);
        }
    }

    /**
     * 断言是Long
     *
     * @param value number
     * @param args  消息格式化参数
     */
    default void isLong(String value, Object... args) {
        try {
            Long.parseLong(value.trim());
        } catch (Exception e) {
            LoggerFactory.getLogger(IAsserts.class).error("isLong error, value: {}, err = {}", value, e.getMessage());
            throw newException(args);
        }
    }

    /**
     * 断言是正整数和0
     *
     * @param value number
     * @param args  消息格式化参数
     */
    default void isPositiveAndZero(long value, Object... args) {
        if (value < 0) {
            throw newException(args);
        }
    }

    default  <T> void in(T value, List<T> values, Object... args) {
        for (T t : values) {
            if (Objects.equals(value, t)) {
                return;
            }
        }
        throw newException(args);
    }

    /**
     * 直接抛出异常
     *
     * @param args 消息格式化参数
     */
    default void throwException(Object... args) {
        throw newException(args);
    }

    String getRealCode();
}
