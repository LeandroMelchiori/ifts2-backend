package ar.edu.ifts2.usuario.service;

import ar.edu.ifts2.shared.dto.PageResponse;
import ar.edu.ifts2.shared.error.BusinessConflictException;
import ar.edu.ifts2.shared.error.ResourceNotFoundException;
import ar.edu.ifts2.usuario.dto.ActualizarUsuarioRequest;
import ar.edu.ifts2.usuario.dto.CambiarPasswordRequest;
import ar.edu.ifts2.usuario.dto.CrearUsuarioRequest;
import ar.edu.ifts2.usuario.dto.UsuarioResponse;
import ar.edu.ifts2.usuario.entity.Rol;
import ar.edu.ifts2.usuario.entity.Usuario;
import ar.edu.ifts2.usuario.repository.UsuarioRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UsuarioService {
    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public PageResponse<UsuarioResponse> listar(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id")));
        return PageResponse.from(repository.findAll(pageable).map(UsuarioResponse::from));
    }

    public UsuarioResponse obtener(UUID id) {
        return UsuarioResponse.from(repository.findById(id).orElseThrow(this::notFound));
    }

    @Transactional
    public UsuarioResponse crear(CrearUsuarioRequest request) {
        if (repository.existsByEmail(request.email())) {
            throw duplicateEmail();
        }
        Usuario usuario = new Usuario(request.nombre(), request.apellido(), request.email(),
                passwordEncoder.encode(request.password()), request.rol());
        return UsuarioResponse.from(repository.saveAndFlush(usuario));
    }

    @Transactional
    public UsuarioResponse actualizar(UUID id, UUID actorId, ActualizarUsuarioRequest request) {
        // Serializar los cambios de ADMIN antes de leer el usuario; evita perder al ultimo por concurrencia.
        List<Usuario> activeAdmins = repository.findActiveByRolForUpdate(Rol.ADMIN);
        Usuario usuario = repository.findByIdForUpdate(id).orElseThrow(this::notFound);
        boolean removesAdmin = usuario.isActivo() && usuario.getRol() == Rol.ADMIN
                && (!request.activo() || request.rol() != Rol.ADMIN);
        if (removesAdmin && activeAdmins.size() <= 1) {
            throw new BusinessConflictException("No se puede desactivar ni degradar al ultimo ADMIN activo");
        }
        if (id.equals(actorId) && (!request.activo() || request.rol() != Rol.ADMIN)) {
            throw new BusinessConflictException("No puede desactivar ni degradar su propia cuenta");
        }
        if (repository.existsByEmailAndIdNot(request.email(), id)) {
            throw duplicateEmail();
        }
        usuario.actualizar(request.nombre(), request.apellido(), request.email(), request.rol(), request.activo());
        repository.flush();
        return UsuarioResponse.from(usuario);
    }

    @Transactional
    public void cambiarPassword(UUID id, CambiarPasswordRequest request) {
        Usuario usuario = repository.findByIdForUpdate(id).orElseThrow(this::notFound);
        usuario.cambiarPasswordHash(passwordEncoder.encode(request.password()));
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Usuario no encontrado");
    }

    private BusinessConflictException duplicateEmail() {
        return new BusinessConflictException("El email ya esta registrado");
    }
}
