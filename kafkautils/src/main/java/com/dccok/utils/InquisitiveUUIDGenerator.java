package com.dccok.utils;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.EventType;
import org.hibernate.id.factory.spi.CustomIdGeneratorCreationContext;
import org.hibernate.id.uuid.UuidGenerator;

import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

/**
 * Based on https://stackoverflow.com/questions/76166062/custom-uuid-generator-in-hibernate
 */
@Slf4j
public class InquisitiveUUIDGenerator  extends UuidGenerator {

    public InquisitiveUUIDGenerator(
            InquisitiveIdGenerator config,
            Member idMember,
            CustomIdGeneratorCreationContext creationContext
    ) {
        super(getUuidGeneratorAnnotation(config.style()), idMember, creationContext);
    }

    @Override
    public Object generate(SharedSessionContractImplementor session, Object owner, Object currentValue, EventType eventType) {

        Object id = null;
        if (owner != null) {
            try {
                Method method = owner.getClass().getMethod("getId");
                id = method.invoke(owner);
            } catch (Exception e) {
                log.error(String.format("Id generation failed for %s",owner.getClass().getName()), e);
            }
        }
        return (id != null ? id : super.generate(session, owner, currentValue, eventType));
    }

    private static org.hibernate.annotations.UuidGenerator getUuidGeneratorAnnotation(org.hibernate.annotations.UuidGenerator.Style style) {
        return new org.hibernate.annotations.UuidGenerator() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return org.hibernate.annotations.UuidGenerator.class;
            }

            @Override
            public Style style() {
                return style;
            }
        };
    }

}