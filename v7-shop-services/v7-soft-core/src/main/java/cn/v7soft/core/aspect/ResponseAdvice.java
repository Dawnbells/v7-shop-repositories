package cn.v7soft.core.aspect;

import cn.hutool.json.JSONUtil;
import cn.v7soft.core.annotation.IgnoreResponsePackage;
import cn.v7soft.core.result.CommonResult;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.List;

@RestControllerAdvice
public class ResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(@NotNull MethodParameter returnType, @NotNull Class<? extends HttpMessageConverter<?>> converterType) {
        if (returnType.hasMethodAnnotation(IgnoreResponsePackage.class)) {
            return false;
        }
        Class<?> declaringClass = returnType.getDeclaringClass();
        return !declaringClass.isAnnotationPresent(IgnoreResponsePackage.class)
                && declaringClass.getPackageName().startsWith("cn.v7soft");
    }

    @Override
    public Object beforeBodyWrite(Object body, @NotNull MethodParameter returnType, @NotNull MediaType selectedContentType,
                                  @NotNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  @NotNull ServerHttpRequest request, @NotNull ServerHttpResponse response) {
        if (body instanceof CommonResult<?>) {
            return body;
        }
        if (body instanceof ResponseEntity) {
            return body;
        }
        if (response instanceof ServletServerHttpResponse) {
            HttpServletResponse servletResponse = ((ServletServerHttpResponse) response).getServletResponse();
            if (servletResponse.getStatus() == 200) {
                if (body instanceof String) {
                    return JSONUtil.toJsonStr(CommonResult.success(body));
                }
                if (body instanceof List) {
                    // 返回列表
                    return CommonResult.success((List<?>) body);
                }
                if (body instanceof Page) {
                    // 返回分页
                    return CommonResult.success((Page<?>) body);
                }
                // 进行包装
                return CommonResult.success(body);
            }
        }
        return body;
    }

}
