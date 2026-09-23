package ar.edu.ifts2.usuario;

import ar.edu.ifts2.auth.dto.LoginRequest;
import ar.edu.ifts2.shared.error.BusinessConflictException;
import ar.edu.ifts2.support.PostgresIntegrationTest;
import ar.edu.ifts2.usuario.dto.ActualizarUsuarioRequest;
import ar.edu.ifts2.usuario.entity.Rol;
import ar.edu.ifts2.usuario.entity.Usuario;
import ar.edu.ifts2.usuario.repository.UsuarioRepository;
import ar.edu.ifts2.usuario.service.UsuarioService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UsuarioIntegrationTest extends PostgresIntegrationTest {
    private final MockMvc mvc;
    private final ObjectMapper mapper;
    private final UsuarioRepository repository;
    private final PasswordEncoder encoder;
    private final UsuarioService service;
    private final String password = UUID.randomUUID().toString();
    private String hash;
    private Usuario admin;
    private Usuario editor;
    private String adminToken;

    @Autowired
    UsuarioIntegrationTest(MockMvc mvc, ObjectMapper mapper, UsuarioRepository repository,
                           PasswordEncoder encoder, UsuarioService service) {
        this.mvc = mvc;
        this.mapper = mapper;
        this.repository = repository;
        this.encoder = encoder;
        this.service = service;
    }

    @BeforeAll
    void encodePassword() { hash = encoder.encode(password); }

    @BeforeEach
    void seed() throws Exception {
        repository.deleteAllInBatch();
        admin = repository.saveAndFlush(new Usuario("Admin", "Test", "admin@example.test", hash, Rol.ADMIN));
        editor = repository.saveAndFlush(new Usuario("Editor", "Test", "editor@example.test", hash, Rol.EDITOR));
        adminToken = login(admin.getEmail(), password);
    }

    @Test
    void adminCanListAndGetUsersWithoutCredentials() throws Exception {
        mvc.perform(authorized(get("/api/admin/usuarios").param("page", "0").param("size", "1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].password").doesNotExist())
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
        mvc.perform(authorized(get("/api/admin/usuarios/{id}", editor.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(editor.getId().toString()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void editorCannotUseAnyUserManagementOperation() throws Exception {
        String token = login(editor.getEmail(), password);
        var requests = java.util.List.of(get("/api/admin/usuarios"), get("/api/admin/usuarios/" + admin.getId()),
                post("/api/admin/usuarios"), put("/api/admin/usuarios/" + admin.getId()),
                put("/api/admin/usuarios/" + admin.getId() + "/password"), delete("/api/admin/usuarios/" + admin.getId()));
        for (var request : requests) {
            mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void createsEditorWithNormalizedUniqueEmailAndBcrypt() throws Exception {
        String newPassword = UUID.randomUUID().toString();
        var response = mvc.perform(authorized(post("/api/admin/usuarios")).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("nombre", "Nuevo", "apellido", "Usuario",
                                "email", " NEW@EXAMPLE.TEST ", "password", newPassword, "rol", "EDITOR"))))
                .andExpect(status().isCreated()).andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.email").value("new@example.test")).andExpect(jsonPath("$.rol").value("EDITOR"))
                .andExpect(jsonPath("$.activo").value(true)).andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist()).andReturn();
        Usuario created = repository.findByEmail("new@example.test").orElseThrow();
        assertThat(encoder.matches(newPassword, created.getPasswordHash())).isTrue();
        assertThat(created.getPasswordHash()).startsWith("$2a$12$");
        assertThat(response.getResponse().getContentAsString()).doesNotContain(newPassword, created.getPasswordHash());
    }

    @Test
    void duplicateEmailFails() throws Exception {
        mvc.perform(authorized(post("/api/admin/usuarios")).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("nombre", "Duplicado", "apellido", "Test",
                                "email", " ADMIN@EXAMPLE.TEST ", "password", password, "rol", "EDITOR"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("El email ya esta registrado"));
    }

    @Test
    void duplicateEmailOnUpdateFails() throws Exception {
        update(editor.getId(), new ActualizarUsuarioRequest("Editor", "Test", admin.getEmail(), Rol.EDITOR, true), 409);
    }

    @Test
    void updatesOnlyAdministrableFields() throws Exception {
        update(editor.getId(), new ActualizarUsuarioRequest("Nuevo", "Apellido", " CHANGED@EXAMPLE.TEST ", Rol.ADMIN, true), 200);
        Usuario updated = repository.findById(editor.getId()).orElseThrow();
        assertThat(updated.getNombre()).isEqualTo("Nuevo");
        assertThat(updated.getApellido()).isEqualTo("Apellido");
        assertThat(updated.getEmail()).isEqualTo("changed@example.test");
        assertThat(updated.getRol()).isEqualTo(Rol.ADMIN);
        assertThat(updated.getPasswordHash()).isEqualTo(hash);
    }

    @Test
    void deactivationBlocksExistingJwtAndCanBeReversed() throws Exception {
        String editorToken = login(editor.getEmail(), password);
        update(editor.getId(), request(editor, Rol.EDITOR, false), 200);
        mvc.perform(get("/api/admin/test").header(HttpHeaders.AUTHORIZATION, "Bearer " + editorToken))
                .andExpect(status().isUnauthorized());
        assertThat(repository.findById(editor.getId()).orElseThrow().isActivo()).isFalse();
        update(editor.getId(), request(editor, Rol.EDITOR, true), 200);
        assertThat(login(editor.getEmail(), password)).isNotBlank();
    }

    @Test
    void roleChangesInvalidatePreviousJwt() throws Exception {
        Usuario otherAdmin = repository.saveAndFlush(new Usuario("Otro", "Admin", "other@example.test", hash, Rol.ADMIN));
        String oldToken = login(otherAdmin.getEmail(), password);
        update(otherAdmin.getId(), request(otherAdmin, Rol.EDITOR, true), 200);
        mvc.perform(get("/api/admin/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + login(otherAdmin.getEmail(), password)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotDeactivateLastAdmin() throws Exception {
        update(admin.getId(), request(admin, Rol.ADMIN, false), 409);
        assertThat(repository.findById(admin.getId()).orElseThrow().isActivo()).isTrue();
    }

    @Test
    void cannotDemoteLastAdmin() throws Exception {
        update(admin.getId(), request(admin, Rol.EDITOR, true), 409);
        assertThat(repository.findById(admin.getId()).orElseThrow().getRol()).isEqualTo(Rol.ADMIN);
    }

    @Test
    void cannotDisableOrDemoteSelfEvenWithAnotherAdmin() throws Exception {
        repository.saveAndFlush(new Usuario("Otro", "Admin", "other@example.test", hash, Rol.ADMIN));
        update(admin.getId(), request(admin, Rol.ADMIN, false), 409);
        update(admin.getId(), request(admin, Rol.EDITOR, true), 409);
    }

    @Test
    void concurrentDemotionsCannotRemoveEveryActiveAdmin() throws Exception {
        Usuario other = repository.saveAndFlush(new Usuario("Otro", "Admin", "other@example.test", hash, Rol.ADMIN));
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> demote(start, admin, other));
            var two = executor.submit(() -> demote(start, other, admin));
            assertThat(java.util.List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(repository.findAll().stream().filter(u -> u.getRol() == Rol.ADMIN && u.isActivo()).count()).isEqualTo(1);
    }

    @Test
    void resetsPasswordWithBcryptAndNewCredentialsWork() throws Exception {
        String replacement = UUID.randomUUID().toString();
        mvc.perform(authorized(put("/api/admin/usuarios/{id}/password", editor.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("password", replacement))))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        String updatedHash = repository.findById(editor.getId()).orElseThrow().getPasswordHash();
        assertThat(encoder.matches(replacement, updatedHash)).isTrue();
        assertThat(encoder.matches(password, updatedHash)).isFalse();
        assertThat(login(editor.getEmail(), replacement)).isNotBlank();
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginRequest(editor.getEmail(), password))))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"password", "passwordHash"})
    void updateRejectsCredentialFields(String field) throws Exception {
        var body = mapper.valueToTree(request(editor, Rol.EDITOR, true));
        ((com.fasterxml.jackson.databind.node.ObjectNode) body).put(field, password);
        mvc.perform(authorized(put("/api/admin/usuarios/{id}", editor.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
        assertThat(repository.findById(editor.getId()).orElseThrow().getPasswordHash()).isEqualTo(hash);
    }

    @Test
    void createRejectsPasswordHash() throws Exception {
        mvc.perform(authorized(post("/api/admin/usuarios")).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("nombre", "Nuevo", "apellido", "Test", "email", "new@example.test",
                                "password", password, "passwordHash", hash, "rol", "EDITOR"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidPasswordFailsValidationWithoutEchoingValue() throws Exception {
        for (String invalid : java.util.List.of("short", "\u00e9".repeat(37))) {
            var result = mvc.perform(authorized(put("/api/admin/usuarios/{id}/password", editor.getId()))
                            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("password", invalid))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("password")).andReturn();
            assertThat(result.getResponse().getContentAsString()).doesNotContain(invalid);
        }
    }

    @Test
    void missingUserAndInvalidUuidHaveConsistentErrors() throws Exception {
        mvc.perform(authorized(get("/api/admin/usuarios/{id}", UUID.randomUUID())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Usuario no encontrado"));
        mvc.perform(authorized(get("/api/admin/usuarios/not-a-uuid"))).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "101", "-1"})
    void paginationIsBounded(String size) throws Exception {
        mvc.perform(authorized(get("/api/admin/usuarios").param("size", size))).andExpect(status().isBadRequest());
    }

    @Test
    void noPhysicalDeleteEndpointExists() throws Exception {
        mvc.perform(authorized(delete("/api/admin/usuarios/{id}", editor.getId()))).andExpect(status().isMethodNotAllowed());
        assertThat(repository.existsById(editor.getId())).isTrue();
    }

    @Test
    void swaggerDescribesUserContractsWithoutHashFields() throws Exception {
        var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        JsonNode spec = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(spec.at("/paths/~1api~1admin~1usuarios/post/responses/201").isMissingNode()).isFalse();
        assertThat(spec.at("/paths/~1api~1admin~1usuarios/get/security/0/bearerAuth").isArray()).isTrue();
        assertThat(spec.at("/components/schemas/UsuarioResponse/properties").has("passwordHash")).isFalse();
        assertThat(spec.at("/components/schemas/UsuarioResponse/properties").has("password")).isFalse();
        assertThat(spec.at("/components/schemas/ActualizarUsuarioRequest/properties").has("password")).isFalse();
        assertThat(spec.at("/paths/~1api~1admin~1storage~1test").isMissingNode()).isTrue();
    }

    private boolean demote(CyclicBarrier start, Usuario actor, Usuario target) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        try {
            service.actualizar(target.getId(), actor.getId(), request(target, Rol.EDITOR, true));
            return true;
        } catch (BusinessConflictException ex) {
            return false;
        }
    }

    private ActualizarUsuarioRequest request(Usuario user, Rol rol, boolean activo) {
        return new ActualizarUsuarioRequest(user.getNombre(), user.getApellido(), user.getEmail(), rol, activo);
    }

    private void update(UUID id, ActualizarUsuarioRequest body, int expected) throws Exception {
        mvc.perform(authorized(put("/api/admin/usuarios/{id}", id)).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body))).andExpect(status().is(expected));
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
    }

    private String login(String email, String rawPassword) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginRequest(email, rawPassword))))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
