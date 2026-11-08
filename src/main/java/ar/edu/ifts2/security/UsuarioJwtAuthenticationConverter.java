package ar.edu.ifts2.security;

import ar.edu.ifts2.usuario.repository.UsuarioRepository;
import ar.edu.ifts2.usuario.entity.Rol;
import ar.edu.ifts2.panelrol.entity.SeccionPanel;
import ar.edu.ifts2.panelrol.repository.PanelRolRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.UUID;

@Component
public class UsuarioJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final UsuarioRepository repository;
    private final PanelRolRepository panelRoles;

    public UsuarioJwtAuthenticationConverter(UsuarioRepository repository, PanelRolRepository panelRoles) {
        this.repository = repository;
        this.panelRoles = panelRoles;
    }

    @Override
    @Transactional(readOnly = true)
    public AbstractAuthenticationToken convert(Jwt jwt) {
        var usuario = repository.findById(UUID.fromString(jwt.getSubject()))
                .orElseThrow(() -> new InvalidBearerTokenException("Cuenta sin acceso"));
        if (!usuario.isActivo() || !usuario.getRol().name().equals(jwt.getClaimAsString("role"))) {
            throw new InvalidBearerTokenException("Cuenta sin acceso");
        }
        var authorities = new ArrayList<GrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name()));
        if (usuario.getRol() == Rol.EDITOR) {
            // Los permisos se leen en cada request; no se confian claims ni caches del cliente.
            panelRoles.findByUsuarioId(usuario.getId()).ifPresent(rol -> {
                var permissions = rol.getPermissions();
                for (var section : SeccionPanel.values()) {
                    var permission = permissions.get(section.id());
                    if (permission != null && permission.canView()) {
                        authorities.add(new SimpleGrantedAuthority(section.viewAuthority()));
                        if (permission.canManage()) { authorities.add(new SimpleGrantedAuthority(section.manageAuthority())); }
                    }
                }
            });
        }
        return new JwtAuthenticationToken(jwt, authorities);
    }
}
