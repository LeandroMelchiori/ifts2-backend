package ar.edu.ifts2.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigTest {
    private final JwtConfig config = new JwtConfig();

    @Test
    void rejectsWeakSigningKeys() {
        String weakKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> config.jwtSecretKey(new JwtProperties(weakKey, "test", Duration.ofMinutes(15))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT_SECRET debe contener al menos 32 bytes aleatorios");
    }

    @Test
    void rejectsMalformedBase64WithoutEchoingSecret() {
        assertThatThrownBy(() -> config.jwtSecretKey(new JwtProperties("invalid!base64", "test", Duration.ofMinutes(15))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT_SECRET debe estar codificado en Base64")
                .hasNoCause();
    }
}
