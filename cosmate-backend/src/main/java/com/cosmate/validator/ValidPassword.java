package com.cosmate.validator;

import com.cosmate.validation.RequestValidation;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = ValidPasswordValidator.class)
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
public @interface ValidPassword {
    String message() default "PASSWORD_INVALID";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    String regex() default RequestValidation.PASSWORD_REGEX;
}
