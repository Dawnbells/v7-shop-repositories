package cn.v7soft.core.controller.validator;

import cn.v7soft.core.controller.validator.annotation.ListPattern;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.List;
import java.util.regex.Pattern;

public class ListPatternValidator implements ConstraintValidator<ListPattern, List<String>> {
    private boolean nullable;
    private Pattern pattern;

    @Override
    public void initialize(ListPattern constraintAnnotation) {
        this.pattern = Pattern.compile(constraintAnnotation.regexp());
        this.nullable = constraintAnnotation.nullable();
    }

    @Override
    public boolean isValid(List<String> value, ConstraintValidatorContext context) {
        if (value == null) {
            return this.nullable; // consider null as valid, handle it separately if needed
        }
        for (String element : value) {
            if (!pattern.matcher(element).matches()) {
                return false;
            }
        }
        return true;
    }
}
