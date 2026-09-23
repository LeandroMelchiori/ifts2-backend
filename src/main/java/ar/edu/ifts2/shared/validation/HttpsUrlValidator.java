package ar.edu.ifts2.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;

public class HttpsUrlValidator implements ConstraintValidator<HttpsUrl, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        try {
            URI uri = URI.create(value);
            int port = uri.getPort();
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && (port == -1 || (port > 0 && port <= 65535));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
