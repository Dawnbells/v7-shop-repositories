package cn.v7soft.core.result;

import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.enums.IResponseEnum;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommonResult<T> {
    /**
     * 返回码
     */
    private String code;
    /**
     * 消息
     */
    private String msg;
    /**
     * 返回数据，可空
     */
    @Nullable
    private T data;

    public CommonResult(IResponseEnum responseEnum) {
        this(responseEnum, null);
    }

    public CommonResult(IResponseEnum responseEnum, @Nullable T data) {
        this.code = responseEnum.getCode();
        this.msg = responseEnum.getMessage();
        this.data = data;
    }

    public CommonResult(String code, String message) {
        this.code = code;
        this.msg = message;
    }

    public static <T> CommonResult<T> success() {
        return success((T) null);
    }

    public static <T> CommonResult<ListWrapper<T>> success(@Nullable List<T> data) {
        return new CommonResult<>(ClientResponseEnum.SUCCESS, new ListWrapper<T>(data));
    }

    public static <T> CommonResult<PageWrapper<T>> success(@NotNull Page<T> data) {
        return new CommonResult<>(ClientResponseEnum.SUCCESS, new PageWrapper<>(data));
    }

    public static <T> CommonResult<T> success(@Nullable T data) {
        return new CommonResult<T>(ClientResponseEnum.SUCCESS, data);
    }

    public static <T> CommonResult<T> failure(String code, String message) {
        return new CommonResult<>(code, message);
    }
}
