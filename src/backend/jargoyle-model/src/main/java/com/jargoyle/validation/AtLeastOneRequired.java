package com.jargoyle.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AtLeastOneRequiredValidator.class)
public @interface AtLeastOneRequired {
    String message() default "At least one field must be provided";
    String[] value();
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
