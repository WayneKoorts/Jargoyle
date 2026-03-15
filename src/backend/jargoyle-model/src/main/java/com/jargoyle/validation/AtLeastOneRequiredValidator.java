package com.jargoyle.validation;

import java.lang.reflect.Field;
import java.util.Optional;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class AtLeastOneRequiredValidator implements ConstraintValidator<AtLeastOneRequired, Object> {

    private String[] fields;

    @Override
    public void initialize(AtLeastOneRequired constraintAnnotation) {
        fields = constraintAnnotation.value();
    }

    @Override
    public boolean isValid(Object objectToValidate, ConstraintValidatorContext context) {
        for (var fieldName : fields) {
            Field field;
            try {
                // Return valid early if any of the values are present, defined as present
                // for Optional<?> fields and not null for others.
                field = objectToValidate.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                var fieldValue = field.get(objectToValidate);
                if (fieldValue instanceof Optional<?> optionalValue) {
                    if (optionalValue.isPresent()) return true;
                } else {
                    if (fieldValue != null) return true;
                }
            } catch (NoSuchFieldException | IllegalAccessException ex) {
                throw new IllegalStateException(
                    "Invalid @AtLeastOneRequired configuration: field '" + fieldName + "' not found on "
                    + objectToValidate.getClass().getSimpleName(), ex
                );
            }
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
            "At least one of [" + String.join(", ", fields) + "] must be provided"
        ).addConstraintViolation();

        return false;
    }
}
