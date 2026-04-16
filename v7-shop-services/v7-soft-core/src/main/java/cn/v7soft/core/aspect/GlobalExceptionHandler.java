package cn.v7soft.core.aspect;


import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.enums.ServiceResponseEnum;
import cn.v7soft.core.exception.BaseException;
import cn.v7soft.core.result.CommonResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    // 全局异常拦截（拦截项目中的NotLoginException异常）
    @ResponseBody
    @ExceptionHandler(NotPermissionException.class)
    public ResponseEntity<?> handlerNotPermissionException(NotPermissionException nle) throws Exception {
        nle.printStackTrace();
        BaseException ex = ClientResponseEnum.NO_PERMISSION.newException(nle.getMessage());
        return ResponseEntity
                .status(ex.getStatus())
                .body(CommonResult.failure(ex.getRealCode(), ex.getMessage()));
    }

    @ResponseBody
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<?> handlerNotLoginException(NotLoginException nle) throws Exception {
        // 打印堆栈，以供调试
        nle.printStackTrace();

        // 判断场景值，定制化异常信息
        String message = "";
        int code = 402;
        if (nle.getType().equals(NotLoginException.NOT_TOKEN)) {
            message = "未能读取到有效 token";
        } else if (nle.getType().equals(NotLoginException.INVALID_TOKEN)) {
            message = "token 无效";
        } else if (nle.getType().equals(NotLoginException.TOKEN_TIMEOUT)) {
            message = "token 已过期";
        } else if (nle.getType().equals(NotLoginException.BE_REPLACED)) {
            message = "token 已被顶下线";
        } else if (nle.getType().equals(NotLoginException.KICK_OUT)) {
            message = "token 已被踢下线";
        } else if (nle.getType().equals(NotLoginException.TOKEN_FREEZE)) {
            message = "token 已被冻结";
        } else if (nle.getType().equals(NotLoginException.NO_PREFIX)) {
            message = "未按照指定前缀提交 token";
        } else {
            message = "当前会话未登录";
        }

        // 返回给前端
        return ResponseEntity
                .status(code)
                .body(CommonResult.failure(String.valueOf(code), message));
    }


    // 处理特定异常
    @ResponseBody
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<?> handleBaseException(BaseException ex) {
        log.error("handleBaseException: " + ex.getMessage());
        // 创建并返回一个适当的响应实体
        return ResponseEntity
                .status(ex.getStatus())
                .body(CommonResult.failure(ex.getRealCode(), ex.getMessage()));
    }

    // 处理特定异常
    @ResponseBody
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        ex.printStackTrace();
        // 创建并返回一个适当的响应实体
        FieldError fieldError = ex.getFieldErrors().get(0);
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(CommonResult.failure(ClientResponseEnum.PARAMETER_ILLEGAL.getCode(), fieldError.getDefaultMessage()));
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<?> handleOtherThrowable(Throwable ex) {
        if (ex instanceof NoResourceFoundException) {
            log.error("NoResourceFoundException: " + ex.getMessage());
            return ResponseEntity
                    .status(ServiceResponseEnum.NOT_FOUND.getStatus())
                    .body(CommonResult.failure(ServiceResponseEnum.NOT_FOUND.getCode(), ServiceResponseEnum.NOT_FOUND.getMessage()));
        }
        log.error("unknown error:", ex);
        return ResponseEntity
                .status(ServiceResponseEnum.UNKNOWN.getStatus())
                .body(CommonResult.failure(ServiceResponseEnum.UNKNOWN.getCode(), ex.getMessage()));
    }

}