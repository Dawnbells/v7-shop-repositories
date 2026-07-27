package cn.v7soft.admin.controller.advice;

import cn.v7soft.admin.controller.req.SaveDynamicConfigRequest;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.utils.SaSessionUtil;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Type;

@ControllerAdvice
public class EmailConfigAuthorizationAdvice extends RequestBodyAdviceAdapter {

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return SaveDynamicConfigRequest.class.equals(targetType);
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage,
                                MethodParameter parameter, Type targetType,
                                Class<? extends HttpMessageConverter<?>> converterType) {
        if (!(body instanceof SaveDynamicConfigRequest request)
                || !"email".equals(request.getConfigName())) {
            return body;
        }

        SystemUserDto user = SaSessionUtil.getLoginUser();
        if (user == null) {
            throw new IllegalArgumentException("请先登录");
        }
        if (request.getDepartmentId() == null && !user.isAdmin()) {
            throw new IllegalArgumentException("只有公司管理员可以修改公司邮件配置");
        }
        if (!user.isAdmin() && !request.getDepartmentId().equals(user.getDepartmentId())) {
            throw new IllegalArgumentException("无权修改其他部门的邮件配置");
        }
        return body;
    }
}
