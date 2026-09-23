package ar.edu.ifts2.security;

import ar.edu.ifts2.usuario.repository.UsuarioRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Component
public class UsuarioJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final UsuarioRepository repository;

    public UsuarioJwtAuthenticationConverter(UsuarioRepository repository) { this.repository = repository; }

    @Override
    @Transactional(readOnly = true)
    public AbstractAuthenticationToken convert(Jwt jwt) {
        var usuario = repository.findById(UUID.fromString(jwt.getSubject()))
                .orElseThrow(() -> new InvalidBearerTokenException("Cuenta sin acceso"));
        if (!usuario.isActivo() || !usuario.getRol().name().equals(jwt.getClaimAsString("role"))) {
            throw new InvalidBearerTokenException("Cuenta sin acceso");
        }
        return new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name())));
    }
}
