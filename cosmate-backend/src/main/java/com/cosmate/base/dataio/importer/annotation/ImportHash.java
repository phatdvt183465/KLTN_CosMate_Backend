package com.cosmate.base.dataio.importer.annotation;

import java.lang.annotation.*;

@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ImportHash {

    boolean hashDefault() default true;
}
