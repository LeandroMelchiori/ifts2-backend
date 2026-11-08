package ar.edu.ifts2.panelrol;

import ar.edu.ifts2.panelrol.entity.SeccionPanel;
import ar.edu.ifts2.security.JwtService;
import ar.edu.ifts2.support.PostgresIntegrationTest;
import ar.edu.ifts2.usuario.entity.Rol;
import ar.edu.ifts2.usuario.entity.Usuario;
import ar.edu.ifts2.usuario.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PanelRolIntegrationTest extends PostgresIntegrationTest {
    private static final String ROLES = "/api/admin/panel-roles";
    private final MockMvc mvc;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final String adminPassword = UUID.randomUUID().toString();
    private final String editorPassword = UUID.randomUUID().toString();
    private Usuario admin, editor, other;
    private String adminToken, editorToken, otherToken;

    @Autowired
    PanelRolIntegrationTest(MockMvc mvc, ObjectMapper mapper, JdbcTemplate jdbc,
                            UsuarioRepository usuarios, PasswordEncoder encoder, JwtService jwt) {
        this.mvc = mvc; this.mapper = mapper; this.jdbc = jdbc;
        this.usuarios = usuarios; this.encoder = encoder; this.jwt = jwt;
    }

    @BeforeAll
    void users() {
        admin = usuarios.saveAndFlush(new Usuario("Admin", "Panel", "admin@panel.test", encoder.encode(adminPassword), Rol.ADMIN));
        String hash = encoder.encode(editorPassword);
        editor = usuarios.saveAndFlush(new Usuario("Editor", "Panel", "editor@panel.test", hash, Rol.EDITOR));
        other = usuarios.saveAndFlush(new Usuario("Otro", "Panel", "other@panel.test", hash, Rol.EDITOR));
        adminToken = jwt.issue(admin.getId(), Rol.ADMIN).accessToken();
        editorToken = jwt.issue(editor.getId(), Rol.EDITOR).accessToken();
        otherToken = jwt.issue(other.getId(), Rol.EDITOR).accessToken();
    }

    @BeforeEach
    void reset() {
        jdbc.update("delete from panel_role");
        jdbc.update("update usuarios set email=?, rol='EDITOR', activo=true where id=?", editor.getEmail(), editor.getId());
        jdbc.update("update usuarios set email=?, rol='EDITOR', activo=true where id=?", other.getEmail(), other.getId());
    }

    @Test
    void adminHasFullAccessWithoutAssignment() throws Exception {
        var result = call(get("/api/auth/me/panel-permissions"), adminToken)
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullAccess").value(true))
                .andExpect(jsonPath("$.roleName").value("Admin"))
                .andExpect(header().string("Cache-Control", "no-store"));
        var permissions = json(result).path("permissions");
        assertThat(permissions.size()).isEqualTo(4);
        permissions.forEach(p -> { assertThat(p.path("view").asBoolean()).isTrue(); assertThat(p.path("manage").asBoolean()).isTrue(); });
        call(get(ROLES), adminToken).andExpect(status().isOk()).andExpect(content().json("[]"));
        assertThat(jdbc.queryForObject("select count(*) from panel_role", Integer.class)).isZero();
    }

    @Test
    void noAssignmentAllowsAccountButDeniesEveryAdminSection() throws Exception {
        call(get("/api/auth/me"), editorToken).andExpect(status().isOk());
        var permissions = json(call(get("/api/auth/me/panel-permissions"), editorToken).andExpect(status().isOk()));
        assertThat(permissions.path("fullAccess").asBoolean()).isFalse();
        assertThat(permissions.path("roleName").isNull()).isTrue();
        assertThat(permissions.path("permissions").size()).isEqualTo(4);
        permissions.path("permissions").forEach(p -> assertThat(p.toString()).isEqualTo("{\"view\":false,\"manage\":false}"));
        for (String path : new String[]{"noticias", "eventos", "galeria", "destacados", "documentos", "institucion",
                "carreras", "autoridades", "enlaces", "meta/posts", "usuarios", "panel-roles", "test", "storage/usage", "future"}) {
            call(get("/api/admin/" + path), editorToken).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/noticias")).andExpect(status().isOk());
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void editorCannotManageAssignmentsEvenWithAllSectionPermissions() throws Exception {
        ar.edu.ifts2.support.PanelRoleFixtures.grantAll(jdbc, editor.getId());
        String id = jdbc.queryForObject("select id from panel_role", UUID.class).toString();
        for (var req : new MockHttpServletRequestBuilder[]{get(ROLES), post(ROLES), put(ROLES + "/" + id), delete(ROLES + "/" + id)}) {
            call(req.contentType(MediaType.APPLICATION_JSON).content(body(editor.getEmail(), Map.of())), editorToken)
                    .andExpect(status().isForbidden());
        }
        call(get("/api/admin/storage/test/url"), editorToken).andExpect(status().isForbidden());
        call(delete("/api/admin/storage/test"), editorToken).andExpect(status().isForbidden());
        call(post("/api/admin/storage/test"), editorToken).andExpect(status().isForbidden());
        call(get("/api/admin/future"), editorToken).andExpect(status().isForbidden());
    }

    @Test
    void allAccountAndAssignmentEndpointsRequireJwt() throws Exception {
        for (String path : new String[]{"/api/auth/me", "/api/auth/me/panel-permissions", ROLES}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/verify-password").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(ROLES).contentType(MediaType.APPLICATION_JSON).content(body(editor.getEmail(), Map.of())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crudNormalizesEmailAndDefaultsMissingSectionsToFalse() throws Exception {
        String id = create(" EDITOR@PANEL.TEST ", Map.of("contenido", permission(true, false)));
        var listed = json(call(get(ROLES), adminToken).andExpect(status().isOk())).get(0);
        assertThat(listed.path("email").asText()).isEqualTo(editor.getEmail());
        assertThat(listed.path("permissions").size()).isEqualTo(4);
        assertThat(listed.at("/permissions/contenido/view").asBoolean()).isTrue();
        assertThat(listed.at("/permissions/instagram/manage").asBoolean()).isFalse();
        assertThat(listed.has("currentPassword")).isFalse();
        call(put(ROLES + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(body(editor.getEmail(), Map.of("informacion", permission(true, true)))), adminToken)
                .andExpect(status().isOk()).andExpect(jsonPath("$.permissions.contenido.view").value(false))
                .andExpect(jsonPath("$.permissions.informacion.manage").value(true));
        call(delete(ROLES + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("currentPassword", adminPassword))), adminToken)
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from panel_role_permission", Integer.class)).isZero();
        assertThat(usuarios.findById(editor.getId()).orElseThrow().isActivo()).isTrue();
        call(get("/api/admin/documentos"), editorToken).andExpect(status().isForbidden());
        call(get("/api/auth/me"), editorToken).andExpect(status().isOk());
    }

    @Test
    void roleMutationsRequireActingAdminsPasswordEveryTime() throws Exception {
        call(post(ROLES).contentType(MediaType.APPLICATION_JSON)
                .content(body(editor.getEmail(), Map.of()).replace(adminPassword, editorPassword)), adminToken)
                .andExpect(status().isUnauthorized());
        String id = create(editor.getEmail(), Map.of());
        call(put(ROLES + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(body(editor.getEmail(), Map.of()).replace(adminPassword, editorPassword)), adminToken)
                .andExpect(status().isUnauthorized());
        call(delete(ROLES + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("currentPassword", editorPassword))), adminToken)
                .andExpect(status().isUnauthorized());
        call(post("/api/auth/verify-password").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("password", adminPassword))), adminToken)
                .andExpect(status().isNoContent());
        call(delete(ROLES + "/" + id).contentType(MediaType.APPLICATION_JSON).content("{}"), adminToken)
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from panel_role", Integer.class)).isEqualTo(1);
    }

    @Test
    void assignmentRequiresActiveExistingEditorAndIsUnique() throws Exception {
        call(post(ROLES).contentType(MediaType.APPLICATION_JSON).content(body("missing@panel.test", Map.of())), adminToken)
                .andExpect(status().isNotFound());
        call(post(ROLES).contentType(MediaType.APPLICATION_JSON).content(body(admin.getEmail(), Map.of())), adminToken)
                .andExpect(status().isConflict());
        jdbc.update("update usuarios set activo=false where id=?", editor.getId());
        call(post(ROLES).contentType(MediaType.APPLICATION_JSON).content(body(editor.getEmail(), Map.of())), adminToken)
                .andExpect(status().isConflict());
        jdbc.update("update usuarios set activo=true where id=?", editor.getId());
        String id = create(editor.getEmail(), Map.of());
        create(other.getEmail(), Map.of());
        call(post(ROLES).contentType(MediaType.APPLICATION_JSON).content(body(editor.getEmail(), Map.of())), adminToken)
                .andExpect(status().isConflict());
        call(put(ROLES + "/" + id).contentType(MediaType.APPLICATION_JSON).content(body(other.getEmail(), Map.of())), adminToken)
                .andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"contenido\":{\"view\":false,\"manage\":true}}", "{\"contenido\":{\"view\":true}}",
            "{\"contenido\":null}", "{\"usuarios\":{\"view\":true,\"manage\":true}}", "null"})
    void invalidPermissionMapsAreRejected(String permissions) throws Exception {
        String body = mapper.writeValueAsString(Map.of("name", "Test", "email", editor.getEmail(), "currentPassword", adminPassword));
        body = body.substring(0, body.length() - 1) + ",\"permissions\":" + permissions + "}";
        call(post(ROLES).contentType(MediaType.APPLICATION_JSON).content(body), adminToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.trace").doesNotExist());
        assertThat(jdbc.queryForObject("select count(*) from panel_role", Integer.class)).isZero();
    }

    @Test
    void permissionsApplyToTheSameJwtOnTheNextRequest() throws Exception {
        String id = create(editor.getEmail(), Map.of("contenido", permission(true, false)));
        call(get("/api/admin/noticias"), editorToken).andExpect(status().isOk());
        call(head("/api/admin/noticias"), editorToken).andExpect(status().isOk());
        String news = "{\"titulo\":\"Prueba permisos\",\"resumen\":\"Resumen\",\"contenido\":\"Texto\"}";
        call(post("/api/admin/noticias").contentType(MediaType.APPLICATION_JSON).content(news), editorToken)
                .andExpect(status().isForbidden());
        call(put(ROLES + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(body(editor.getEmail(), Map.of("contenido", permission(true, true)))), adminToken).andExpect(status().isOk());
        call(post("/api/admin/noticias").contentType(MediaType.APPLICATION_JSON).content(news), editorToken)
                .andExpect(status().isCreated());
        call(put(ROLES + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(body(editor.getEmail(), Map.of())), adminToken).andExpect(status().isOk());
        call(get("/api/admin/noticias"), editorToken).andExpect(status().isForbidden());
        call(get("/api/auth/me/panel-permissions"), editorToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.contenido.view").value(false));
    }

    @ParameterizedTest
    @CsvSource({"destacados,destacados", "informacion,documentos", "informacion,carreras", "informacion,autoridades",
            "informacion,enlaces", "contenido,noticias", "contenido,eventos", "contenido,galeria", "instagram,meta/posts"})
    void viewIsScopedToEachSectionAndIncludesHead(String section, String path) throws Exception {
        create(editor.getEmail(), Map.of(section, permission(true, false)));
        call(get("/api/admin/" + path), editorToken).andExpect(status().isOk());
        call(head("/api/admin/" + path), editorToken).andExpect(status().isOk());
        String forbidden = section.equals("contenido") ? "documentos" : "noticias";
        call(get("/api/admin/" + forbidden), editorToken).andExpect(status().isForbidden());
        call(get("/api/admin/test"), editorToken).andExpect(status().isOk());
        call(get("/api/admin/storage/usage"), editorToken).andExpect(status().isServiceUnavailable());
    }

    @Test
    void viewOnlyCannotMutateOrUploadInAnySection() throws Exception {
        var permissions = new java.util.LinkedHashMap<String, Map<String, Boolean>>();
        for (var section : SeccionPanel.values()) { permissions.put(section.id(), permission(true, false)); }
        create(editor.getEmail(), permissions);
        String id = UUID.randomUUID().toString();
        String[][] requests = {{"POST", "noticias"}, {"PUT", "noticias/" + id}, {"PUT", "noticias/" + id + "/estado"},
                {"PUT", "noticias/" + id + "/portada"}, {"DELETE", "noticias/" + id + "/portada"},
                {"POST", "eventos/" + id + "/fotos"}, {"PUT", "eventos/" + id + "/fotos/" + id + "/archivo"},
                {"DELETE", "eventos/" + id + "/fotos/" + id}, {"PUT", "documentos/" + id + "/archivo"},
                {"PUT", "institucion/datos-sitio"}, {"PUT", "destacados"}, {"POST", "meta/sync"},
                {"PUT", "meta/posts/123/visibilidad"}, {"DELETE", "meta/posts/123/storage"},
                {"PATCH", "noticias/" + id}};
        for (var r : requests) {
            call(request(HttpMethod.valueOf(r[0]), "/api/admin/" + r[1]).contentType(MediaType.APPLICATION_JSON).content("{}"), editorToken)
                    .andExpect(status().isForbidden());
        }
        call(multipart(HttpMethod.PUT, "/api/admin/noticias/" + id + "/portada")
                .file("file", new byte[]{1, 2, 3}), editorToken).andExpect(status().isForbidden());
    }

    @Test
    void reassignmentChangesAccessWithoutChangingEitherLogin() throws Exception {
        String id = create(editor.getEmail(), Map.of("instagram", permission(true, true)));
        call(put(ROLES + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(body(other.getEmail(), Map.of("instagram", permission(true, true)))), adminToken).andExpect(status().isOk());
        call(get("/api/admin/meta/posts"), editorToken).andExpect(status().isForbidden());
        call(get("/api/admin/meta/posts"), otherToken).andExpect(status().isOk());
        call(post("/api/admin/meta/sync"), otherToken).andExpect(status().isServiceUnavailable());
        assertThat(usuarios.findById(editor.getId()).orElseThrow().getEmail()).isEqualTo(editor.getEmail());
        assertThat(usuarios.findById(other.getId()).orElseThrow().getEmail()).isEqualTo(other.getEmail());
    }

    @Test
    void emailChangesPreserveTheAssignmentByUserId() throws Exception {
        create(editor.getEmail(), Map.of("contenido", permission(true, false)));
        call(put("/api/auth/me/email").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", "changed@panel.test", "currentPassword", editorPassword))), editorToken)
                .andExpect(status().isOk());
        call(get(ROLES), adminToken).andExpect(status().isOk()).andExpect(jsonPath("$[0].email").value("changed@panel.test"));
        call(get("/api/admin/noticias"), editorToken).andExpect(status().isOk());
    }

    @Test
    void promotionRemovesLatentPermissionsAndInactiveAccountsCannotUseTheirJwt() throws Exception {
        create(editor.getEmail(), Map.of("contenido", permission(true, true)));
        updateEditor("ADMIN", true);
        assertThat(jdbc.queryForObject("select count(*) from panel_role", Integer.class)).isZero();
        call(get("/api/auth/me"), editorToken).andExpect(status().isUnauthorized());
        call(get("/api/admin/usuarios"), jwt.issue(editor.getId(), Rol.ADMIN).accessToken()).andExpect(status().isOk());
        updateEditor("EDITOR", true);
        call(get("/api/admin/noticias"), editorToken).andExpect(status().isForbidden());
        create(editor.getEmail(), Map.of("contenido", permission(true, false)));
        updateEditor("EDITOR", false);
        call(get("/api/auth/me"), editorToken).andExpect(status().isUnauthorized());
        updateEditor("EDITOR", true);
        call(get("/api/admin/noticias"), editorToken).andExpect(status().isOk());
    }

    @Test
    void concurrentAssignmentsProduceOneCreatedAndOneConflict() throws Exception {
        var start = new CountDownLatch(1);
        String payload = body(editor.getEmail(), Map.of());
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> assign = () -> {
                start.await(10, TimeUnit.SECONDS);
                return call(post(ROLES).contentType(MediaType.APPLICATION_JSON).content(payload), adminToken)
                        .andReturn().getResponse().getStatus();
            };
            var first = executor.submit(assign);
            var second = executor.submit(assign);
            start.countDown();
            assertThat(java.util.List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("select count(*) from panel_role", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from panel_role_permission", Integer.class)).isEqualTo(4);
    }

    @Test
    void databaseEnforcesPermissionChecksAndCascades() throws Exception {
        UUID id = UUID.fromString(create(editor.getEmail(), Map.of()));
        assertThatThrownBy(() -> jdbc.update("update panel_role_permission set can_manage=true where role_id=?", id))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into panel_role_permission(role_id,section) values (?, 'unknown')", id))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into panel_role_permission(role_id,section) values (?, 'contenido')", id))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.update("delete from panel_role where id=?", id);
        assertThat(jdbc.queryForObject("select count(*) from panel_role_permission", Integer.class)).isZero();
    }

    @Test
    void openApiDocumentsBearerAndWriteOnlyConfirmation() throws Exception {
        var spec = json(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));
        for (String operation : new String[]{"get", "post", "put", "delete"}) {
            String path = operation.equals("get") || operation.equals("post") ? ROLES : ROLES + "/{id}";
            assertThat(spec.path("paths").path(path).path(operation).at("/security/0/bearerAuth").isArray()).isTrue();
        }
        assertThat(spec.at("/components/schemas/PanelRolRequest/properties/currentPassword/writeOnly").asBoolean()).isTrue();
        assertThat(spec.at("/components/schemas/PanelRolRequest/properties/knownSections").isMissingNode()).isTrue();
    }

    private void updateEditor(String role, boolean active) throws Exception {
        call(put("/api/admin/usuarios/" + editor.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("nombre", "Editor", "apellido", "Panel", "email", editor.getEmail(),
                        "rol", role, "activo", active))), adminToken).andExpect(status().isOk());
    }
    private String create(String email, Map<String, ?> permissions) throws Exception {
        return json(call(post(ROLES).contentType(MediaType.APPLICATION_JSON).content(body(email, permissions)), adminToken)
                .andExpect(status().isCreated())).path("id").asText();
    }
    private String body(String email, Map<String, ?> permissions) throws Exception {
        return mapper.writeValueAsString(Map.of("name", "Bedelia", "email", email, "permissions", permissions, "currentPassword", adminPassword));
    }
    private Map<String, Boolean> permission(boolean view, boolean manage) { return Map.of("view", view, "manage", manage); }
    private ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }
    private JsonNode json(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
