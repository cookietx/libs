package com.capturerx.common.core;

import org.hibernate.annotations.IdGeneratorType;
import org.hibernate.annotations.UuidGenerator;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;

@IdGeneratorType( com.capturerx.common.core.InquisitiveUUIDGenerator.class )
@Retention(RetentionPolicy.RUNTIME)
@Target({ FIELD, METHOD })
public @interface InquisitiveIdGenerator {
    UuidGenerator.Style style() default UuidGenerator.Style.TIME;
}
