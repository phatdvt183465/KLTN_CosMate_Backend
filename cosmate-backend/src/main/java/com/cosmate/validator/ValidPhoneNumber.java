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
@Constraint(validatedBy = ValidPhoneNumberValidator.class)
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
public @interface ValidPhoneNumber {
    String message() default "INVALID_PHONE";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    String regex() default RequestValidation.PHONE_REGEX;
}
