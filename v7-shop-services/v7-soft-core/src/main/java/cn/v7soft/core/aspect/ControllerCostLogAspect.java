package cn.v7soft.core.aspect;

import java.lang.reflect.Method;
import java.util.Objects;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import cn.hutool.extra.servlet.JakartaServletUtil;
import cn.v7soft.core.utils.HttpRequestHelper;

@Aspect
@Component
public class ControllerCostLogAspect {

    private static final Logger logger = LoggerFactory.getLogger(ControllerCostLogAspect.class);

    // 切点：匹配所有 Controller 的公开方法（也可以限制包名）
    @Pointcut("execution(public * cn.v7soft..controller..*(..))")
    public void controllerMethods() {
    }

    @Around("controllerMethods()")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.nanoTime();

        Object result = joinPoint.proceed(); // 执行方法// 关键修改：获取目标的真实类名，而不是声明方法的类名
        String className = joinPoint.getTarget().getClass().getSimpleName();

        long duration = (System.nanoTime() - start) / 1_000_000;

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        logger.info("[接口耗时][{}] {}.{}() 执行耗时: {} ms",
                    HttpRequestHelper.getRemoteIp(),
                    className,
                    method.getName(),
                    duration);

        return result;
    }
}
