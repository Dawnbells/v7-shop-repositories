package cn.v7soft.core.controller.validator.annotation;


import cn.v7soft.core.controller.validator.ListPatternValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = ListPatternValidator.class)
@Target({ ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ListPattern {
    String message() default "List contains invalid elements";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    String regexp();
    boolean nullable() default false;
}
