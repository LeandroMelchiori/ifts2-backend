package ar.edu.ifts2.usuario.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({FIELD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = ValidPassword.Validator.class)
public @interface ValidPassword {
    String message() default "La password debe tener al menos 12 caracteres y hasta 72 bytes UTF-8";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidPassword, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return PasswordPolicy.isValidNewPassword(value);
        }
    }
}
