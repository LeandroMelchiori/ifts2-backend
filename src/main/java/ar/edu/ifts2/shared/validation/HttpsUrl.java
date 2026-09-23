package ar.edu.ifts2.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = HttpsUrlValidator.class)
public @interface HttpsUrl {
    String message() default "Debe ser una URL HTTPS absoluta sin credenciales";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
