package ar.edu.ifts2.support;

import ar.edu.ifts2.panelrol.entity.SeccionPanel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import java.util.ArrayList;
import java.util.UUID;

/** Permisos explicitos para fixtures de contenido; los tests de seguridad usan JWT y DB reales. */
public final class PanelRoleFixtures {
    private PanelRoleFixtures() { }

    public static void grantAll(JdbcTemplate jdbc, UUID usuarioId) {
        UUID roleId = UUID.randomUUID();
        jdbc.update("insert into panel_role(id,name,usuario_id,created_at,updated_at) values (?, 'Editor de pruebas', ?, now(), now())", roleId, usuarioId);
        for (var section : SeccionPanel.values()) {
            jdbc.update("insert into panel_role_permission(role_id,section,can_view,can_manage) values (?,?,true,true)", roleId, section.id());
        }
    }

    public static SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor panelEditor() {
        var authorities = new ArrayList<SimpleGrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("ROLE_EDITOR"));
        for (var section : SeccionPanel.values()) {
            authorities.add(new SimpleGrantedAuthority(section.viewAuthority()));
            authorities.add(new SimpleGrantedAuthority(section.manageAuthority()));
        }
        return SecurityMockMvcRequestPostProcessors.user("editor")
                .authorities(authorities.toArray(SimpleGrantedAuthority[]::new));
    }
}
