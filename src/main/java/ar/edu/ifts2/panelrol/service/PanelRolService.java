package ar.edu.ifts2.panelrol.service;

import ar.edu.ifts2.auth.service.AuthService;
import ar.edu.ifts2.panelrol.dto.*;
import ar.edu.ifts2.panelrol.entity.*;
import ar.edu.ifts2.panelrol.repository.PanelRolRepository;
import ar.edu.ifts2.shared.error.BusinessConflictException;
import ar.edu.ifts2.shared.error.ResourceNotFoundException;
import ar.edu.ifts2.usuario.entity.Rol;
import ar.edu.ifts2.usuario.entity.Usuario;
import ar.edu.ifts2.usuario.repository.UsuarioRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PanelRolService {
    private final PanelRolRepository roles;
    private final UsuarioRepository usuarios;
    private final AuthService auth;

    public PanelRolService(PanelRolRepository roles, UsuarioRepository usuarios, AuthService auth) {
        this.roles = roles; this.usuarios = usuarios; this.auth = auth;
    }

    public List<PanelRolResponse> listar() { return roles.findAllForAdmin().stream().map(this::response).toList(); }

    public PanelPermissionsResponse permisos(UUID usuarioId) {
        Usuario usuario = usuarios.findById(usuarioId).filter(Usuario::isActivo)
                .orElseThrow(() -> new BadCredentialsException("Cuenta sin acceso"));
        if (usuario.getRol() == Rol.ADMIN) {
            var permissions = new LinkedHashMap<String, PermisoPanelDto>();
            for (var section : SeccionPanel.values()) { permissions.put(section.id(), new PermisoPanelDto(true, true)); }
            return new PanelPermissionsResponse(true, "Admin", permissions);
        }
        var rol = roles.findByUsuarioId(usuarioId).orElse(null);
        return new PanelPermissionsResponse(false, rol == null ? null : rol.getName(),
                responsePermissions(rol == null ? Map.of() : rol.getPermissions()));
    }

    @Transactional
    public PanelRolResponse crear(UUID actor, PanelRolRequest request) {
        confirmarAdmin(actor, request.currentPassword());
        Usuario usuario = editorExistente(request.email());
        if (roles.existsByUsuarioId(usuario.getId())) { throw duplicate(); }
        return response(roles.saveAndFlush(new PanelRol(request.name(), usuario, entityPermissions(request.permissions()))));
    }

    @Transactional
    public PanelRolResponse actualizar(UUID id, UUID actor, PanelRolRequest request) {
        confirmarAdmin(actor, request.currentPassword());
        PanelRol rol = roles.findByIdForUpdate(id).orElseThrow(this::notFound);
        if (rol.getUsuario().getRol() == Rol.ADMIN) { throw adminAssignment(); }
        Usuario usuario = editorExistente(request.email());
        if (roles.existsByUsuarioIdAndIdNot(usuario.getId(), id)) { throw duplicate(); }
        rol.actualizar(request.name(), usuario, entityPermissions(request.permissions()));
        roles.flush();
        return response(rol);
    }

    @Transactional
    public void eliminar(UUID id, UUID actor, String password) {
        confirmarAdmin(actor, password);
        PanelRol rol = roles.findByIdForUpdate(id).orElseThrow(this::notFound);
        if (rol.getUsuario().getRol() == Rol.ADMIN) { throw adminAssignment(); }
        roles.delete(rol);
        roles.flush();
    }

    private void confirmarAdmin(UUID actor, String password) {
        // Mismo orden que UsuarioService: ADMIN activos ordenados, luego usuario/rol.
        // Serializa reasignaciones con promociones/desactivaciones y confirma al actor real.
        boolean adminActivo = usuarios.findActiveByRolForUpdate(Rol.ADMIN).stream()
                .anyMatch(usuario -> usuario.getId().equals(actor));
        if (!adminActivo) { throw new AccessDeniedException("Requiere ADMIN activo"); }
        auth.verifyPassword(actor, password);
    }

    private Usuario editorExistente(String email) {
        Usuario usuario = usuarios.findByEmailForUpdate(email)
                .orElseThrow(() -> new ResourceNotFoundException("Primero debe crear el usuario EDITOR con ese email"));
        if (usuario.getRol() == Rol.ADMIN) { throw adminAssignment(); }
        if (!usuario.isActivo()) { throw new BusinessConflictException("El usuario debe estar activo para asignarle permisos"); }
        return usuario;
    }

    private Map<String, PermisoPanel> entityPermissions(Map<String, PermisoPanelDto> permissions) {
        var result = new LinkedHashMap<String, PermisoPanel>();
        for (var section : SeccionPanel.values()) {
            var permission = permissions.getOrDefault(section.id(), new PermisoPanelDto(false, false));
            result.put(section.id(), new PermisoPanel(permission.view(), permission.manage()));
        }
        return result;
    }

    private Map<String, PermisoPanelDto> responsePermissions(Map<String, PermisoPanel> permissions) {
        var result = new LinkedHashMap<String, PermisoPanelDto>();
        for (var section : SeccionPanel.values()) {
            var permission = permissions.get(section.id());
            result.put(section.id(), new PermisoPanelDto(permission != null && permission.canView(),
                    permission != null && permission.canManage()));
        }
        return result;
    }

    private PanelRolResponse response(PanelRol rol) {
        return new PanelRolResponse(rol.getId(), rol.getName(), rol.getUsuario().getEmail(), responsePermissions(rol.getPermissions()));
    }
    private ResourceNotFoundException notFound() { return new ResourceNotFoundException("Rol del panel no encontrado"); }
    private BusinessConflictException duplicate() { return new BusinessConflictException("El usuario ya tiene un rol del panel"); }
    private BusinessConflictException adminAssignment() { return new BusinessConflictException("ADMIN tiene acceso total y no admite asignaciones de panel"); }
}
