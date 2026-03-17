package com.cosmate.base.spec;

import com.cosmate.base.crud.dto.FilterOperator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FilterField {

    String entityField() default "";

    FilterOperator operator() default FilterOperator.EQUAL;

    boolean ignoreCase() default true;
}
