package ar.edu.ifts2.auth.service;

import ar.edu.ifts2.auth.dto.LoginRequest;
import ar.edu.ifts2.auth.dto.LoginResponse;
import ar.edu.ifts2.security.JwtService;
import ar.edu.ifts2.usuario.entity.Usuario;
import ar.edu.ifts2.usuario.repository.UsuarioRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class AuthService {
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final String dummyPasswordHash;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        // BCrypt limita por bytes, no por caracteres; nunca aceptar truncamiento.
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw invalidCredentials();
        }
        Usuario usuario = usuarioRepository.findByEmail(request.email()).orElse(null);
        // Conservar el costo de BCrypt aunque no exista el email consultado.
        String hash = usuario == null ? dummyPasswordHash : usuario.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (!passwordMatches || usuario == null || !usuario.isActivo()) {
            throw invalidCredentials();
        }
        return jwtService.issue(usuario.getId(), usuario.getRol());
    }

    private BadCredentialsException invalidCredentials() {
        return new BadCredentialsException("Credenciales invalidas");
    }
}
