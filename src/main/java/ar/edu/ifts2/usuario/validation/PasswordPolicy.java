package ar.edu.ifts2.usuario.validation;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    private PasswordPolicy() {
    }

    public static boolean isValidNewPassword(String password) {
        return password != null && !password.isBlank() && password.length() >= 12
                && isWithinBcryptLimit(password);
    }

    public static boolean isWithinBcryptLimit(String password) {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
