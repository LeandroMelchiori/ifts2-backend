package ar.edu.ifts2.auth.service;

import ar.edu.ifts2.auth.dto.ChangeOwnEmailRequest;
import ar.edu.ifts2.auth.dto.ChangeOwnPasswordRequest;
import ar.edu.ifts2.auth.dto.LoginRequest;
import ar.edu.ifts2.auth.dto.LoginResponse;
import ar.edu.ifts2.auth.dto.MeResponse;
import ar.edu.ifts2.security.JwtService;
import ar.edu.ifts2.shared.error.BusinessConflictException;
import ar.edu.ifts2.shared.error.ResourceNotFoundException;
import ar.edu.ifts2.usuario.entity.Usuario;
import ar.edu.ifts2.usuario.repository.UsuarioRepository;
import ar.edu.ifts2.usuario.validation.PasswordPolicy;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public MeResponse me(UUID userId) {
        return MeResponse.from(requireActive(userId));
    }

    @Transactional
    public void changeOwnPassword(UUID userId, ChangeOwnPasswordRequest request) {
        Usuario usuario = usuarioRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        if (!usuario.isActivo()) {
            throw invalidCredentials();
        }
        assertCurrentPassword(request.currentPassword(), usuario.getPasswordHash());
        usuario.cambiarPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    @Transactional
    public MeResponse changeOwnEmail(UUID userId, ChangeOwnEmailRequest request) {
        Usuario usuario = usuarioRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        if (!usuario.isActivo()) {
            throw invalidCredentials();
        }
        assertCurrentPassword(request.currentPassword(), usuario.getPasswordHash());
        if (usuarioRepository.existsByEmailAndIdNot(request.email(), userId)) {
            throw new BusinessConflictException("El email ya esta registrado");
        }
        usuario.cambiarEmail(request.email());
        usuarioRepository.flush();
        return MeResponse.from(usuario);
    }

    /** Password actual incorrecta → 409 (no 401) para no invalidar el JWT de la sesion. */
    private void assertCurrentPassword(String currentPassword, String passwordHash) {
        if (!PasswordPolicy.isWithinBcryptLimit(currentPassword)
                || !passwordEncoder.matches(currentPassword, passwordHash)) {
            throw new BusinessConflictException("Password actual incorrecta");
        }
    }

    private Usuario requireActive(UUID userId) {
        Usuario usuario = usuarioRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        if (!usuario.isActivo()) {
            throw invalidCredentials();
        }
        return usuario;
    }

    private BadCredentialsException invalidCredentials() {
        return new BadCredentialsException("Credenciales invalidas");
    }
}
