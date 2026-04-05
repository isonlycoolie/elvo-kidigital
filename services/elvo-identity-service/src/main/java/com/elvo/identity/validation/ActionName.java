package com.elvo.identity.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Documented
@Target({FIELD})
@Retention(RUNTIME)
@Constraint(validatedBy = ActionNameValidator.class)
public @interface ActionName {

    String message() default "Action value format is invalid";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
