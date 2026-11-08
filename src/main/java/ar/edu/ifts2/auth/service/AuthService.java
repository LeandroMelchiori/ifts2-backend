package ar.edu.ifts2.auth.service;

import ar.edu.ifts2.auth.dto.LoginRequest;
import ar.edu.ifts2.auth.dto.LoginResponse;
import ar.edu.ifts2.auth.dto.ChangeOwnEmailRequest;
import ar.edu.ifts2.auth.dto.ChangeOwnPasswordRequest;
import ar.edu.ifts2.auth.dto.MeResponse;
import ar.edu.ifts2.shared.error.BusinessConflictException;
import ar.edu.ifts2.security.JwtService;
import ar.edu.ifts2.usuario.entity.Usuario;
import ar.edu.ifts2.usuario.repository.UsuarioRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.ifts2.usuario.validation.PasswordPolicy;
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
        if (!PasswordPolicy.isWithinBcryptLimit(request.password())) {
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

    @Transactional(readOnly = true)
    public MeResponse me(UUID id) {
        return MeResponse.from(requireActive(id, false));
    }

    @Transactional(readOnly = true)
    public void verifyPassword(UUID id, String password) {
        if (!matchesCurrentPassword(requireActive(id, false), password)) {
            throw invalidCredentials();
        }
    }

    @Transactional
    public MeResponse changeOwnEmail(UUID id, ChangeOwnEmailRequest request) {
        Usuario usuario = requireActive(id, true);
        assertOwnPassword(usuario, request.currentPassword());
        if (usuarioRepository.existsByEmailAndIdNot(request.email(), id)) {
            throw new BusinessConflictException("El email ya esta registrado");
        }
        usuario.cambiarEmail(request.email());
        usuarioRepository.flush();
        return MeResponse.from(usuario);
    }

    @Transactional
    public void changeOwnPassword(UUID id, ChangeOwnPasswordRequest request) {
        Usuario usuario = requireActive(id, true);
        assertOwnPassword(usuario, request.currentPassword());
        usuario.cambiarPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    private Usuario requireActive(UUID id, boolean lock) {
        Usuario usuario = (lock ? usuarioRepository.findByIdForUpdate(id) : usuarioRepository.findById(id))
                .orElseThrow(this::invalidCredentials);
        if (!usuario.isActivo()) { throw invalidCredentials(); }
        return usuario;
    }

    private boolean matchesCurrentPassword(Usuario usuario, String password) {
        return PasswordPolicy.isWithinBcryptLimit(password)
                && passwordEncoder.matches(password, usuario.getPasswordHash());
    }

    private void assertOwnPassword(Usuario usuario, String password) {
        // Mantener el contrato de Mi cuenta del PR: 409 no implica JWT invalido.
        if (!matchesCurrentPassword(usuario, password)) {
            throw new BusinessConflictException("Password actual incorrecta");
        }
    }

    private BadCredentialsException invalidCredentials() {
        return new BadCredentialsException("Credenciales invalidas");
    }
}
