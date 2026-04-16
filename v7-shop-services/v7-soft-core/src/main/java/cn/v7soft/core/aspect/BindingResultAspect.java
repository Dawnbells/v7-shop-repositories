package cn.v7soft.core.aspect;

import cn.v7soft.core.enums.ClientResponseEnum;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;

@Aspect
@Component
public class BindingResultAspect implements Ordered {
    @Pointcut("execution(* cn.v7soft.*.controller.*Controller.*(..)) ")
    public void bindingResultPoint() {
        // controller 参数校验切面切入点
    }

    @Around("bindingResultPoint() && args(..,bindingResult)")
    public Object doBindingResult(ProceedingJoinPoint pjp, BindingResult bindingResult) throws Throwable {
        // 参数校验不通过
        if (bindingResult.hasFieldErrors()) {
            ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(
                    bindingResult.hasFieldErrors(), bindingResult.getFieldErrors().get(0).getDefaultMessage());
        }
        return pjp.proceed();
    }

    @Override
    public int getOrder() {
        return 10;
    }
}
