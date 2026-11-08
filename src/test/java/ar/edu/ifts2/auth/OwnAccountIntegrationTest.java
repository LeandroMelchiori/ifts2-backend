package ar.edu.ifts2.auth;

import ar.edu.ifts2.auth.dto.*;
import ar.edu.ifts2.panelrol.dto.EliminarPanelRolRequest;
import ar.edu.ifts2.panelrol.dto.PanelRolRequest;
import ar.edu.ifts2.security.JwtService;
import ar.edu.ifts2.support.PostgresIntegrationTest;
import ar.edu.ifts2.usuario.entity.Rol;
import ar.edu.ifts2.usuario.entity.Usuario;
import ar.edu.ifts2.usuario.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OwnAccountIntegrationTest extends PostgresIntegrationTest {
    private final MockMvc mvc;
    private final ObjectMapper mapper;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final JdbcTemplate jdbc;
    private final String password = UUID.randomUUID().toString();
    private Usuario editor;
    private String token, hash;

    @Autowired
    OwnAccountIntegrationTest(MockMvc mvc, ObjectMapper mapper, UsuarioRepository usuarios,
                             PasswordEncoder encoder, JwtService jwt, JdbcTemplate jdbc) {
        this.mvc = mvc; this.mapper = mapper; this.usuarios = usuarios;
        this.encoder = encoder; this.jwt = jwt; this.jdbc = jdbc;
    }

    @BeforeAll
    void users() {
        hash = encoder.encode(password);
        editor = usuarios.saveAndFlush(new Usuario("Mi", "Cuenta", "editor@account.test", hash, Rol.EDITOR));
        usuarios.saveAndFlush(new Usuario("Otra", "Cuenta", "taken@account.test", hash, Rol.ADMIN));
        token = "Bearer " + jwt.issue(editor.getId(), Rol.EDITOR).accessToken();
    }
    @BeforeEach
    void reset() {
        jdbc.update("update usuarios set email=?, password_hash=? where id=?", editor.getEmail(), hash, editor.getId());
    }

    @Test
    void profileIsMinimalPrivateAndDoesNotRequireSectionPermissions() throws Exception {
        var result = mvc.perform(get("/api/auth/me").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(editor.getId().toString()))
                .andExpect(jsonPath("$.rol").value("EDITOR")).andExpect(jsonPath("$.activo").value(true))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Set-Cookie")).andReturn();
        var profile = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(profile.size()).isEqualTo(6);
        assertThat(profile.toString()).doesNotContain("password", hash);
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void ownEmailChangeNormalizesAndChangesLoginWithoutChangingRole() throws Exception {
        mvc.perform(put("/api/auth/me/email").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new ChangeOwnEmailRequest(" NEW@ACCOUNT.TEST ", password))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("new@account.test"))
                .andExpect(jsonPath("$.rol").value("EDITOR"));
        login(editor.getEmail(), password, 401);
        login("new@account.test", password, 200);
        mvc.perform(get("/api/auth/me").header("Authorization", token)).andExpect(status().isOk());
    }

    @Test
    void emailChangeRejectsWrongPasswordAndDuplicateEmail() throws Exception {
        for (var request : new ChangeOwnEmailRequest[]{new ChangeOwnEmailRequest("new@account.test", "wrong-password"),
                new ChangeOwnEmailRequest("taken@account.test", password)}) {
            mvc.perform(put("/api/auth/me/email").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(request))).andExpect(status().isConflict());
        }
        assertThat(usuarios.findById(editor.getId()).orElseThrow().getEmail()).isEqualTo(editor.getEmail());
    }

    @Test
    void passwordChangeChecksCurrentPasswordAndStoresOnlyBcrypt() throws Exception {
        String newPassword = UUID.randomUUID().toString();
        mvc.perform(put("/api/auth/me/password").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new ChangeOwnPasswordRequest("wrong-password", newPassword))))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/auth/me/password").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new ChangeOwnPasswordRequest(password, newPassword))))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        login(editor.getEmail(), password, 401);
        login(editor.getEmail(), newPassword, 200);
        String stored = usuarios.findById(editor.getId()).orElseThrow().getPasswordHash();
        assertThat(stored).startsWith("$2a$12$").isNotEqualTo(hash).isNotEqualTo(newPassword);
        assertThat(encoder.matches(newPassword, stored)).isTrue();
        // Contrato existente: cambiar password no revoca JWT emitidos; conservan su TTL.
        mvc.perform(get("/api/auth/me").header("Authorization", token)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "            ", ""})
    void rejectsWeakNewPasswords(String newPassword) throws Exception {
        mvc.perform(put("/api/auth/me/password").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new ChangeOwnPasswordRequest(password, newPassword))))
                .andExpect(status().isBadRequest());
        assertThat(usuarios.findById(editor.getId()).orElseThrow().getPasswordHash()).isEqualTo(hash);
    }

    @Test
    void rejectsUtf8PasswordsOverBcryptLimitWithoutTruncating() throws Exception {
        String oversized = "é".repeat(37);
        mvc.perform(post("/api/auth/verify-password").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new VerifyPasswordRequest(oversized)))).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/auth/me/password").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new ChangeOwnPasswordRequest(password, oversized)))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/auth/me/email").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new ChangeOwnEmailRequest("new@account.test", oversized)))).andExpect(status().isConflict());
    }

    @Test
    void verifyPasswordDoesNotIssueTokenOrSession() throws Exception {
        var result = mvc.perform(post("/api/auth/verify-password").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new VerifyPasswordRequest(password))))
                .andExpect(status().isNoContent()).andExpect(content().string(""))
                .andExpect(header().doesNotExist("Set-Cookie")).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        mvc.perform(post("/api/auth/verify-password").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new VerifyPasswordRequest("wrong-password"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void bodyCannotChooseAnotherUserOrEscalateRole() throws Exception {
        for (String field : new String[]{"id", "rol", "activo"}) {
            String json = mapper.writeValueAsString(Map.of("email", "new@account.test", "currentPassword", password, field, "ADMIN"));
            mvc.perform(put("/api/auth/me/email").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest());
        }
        assertThat(usuarios.findById(editor.getId()).orElseThrow().getRol()).isEqualTo(Rol.EDITOR);
    }

    @Test
    void requestToStringsNeverIncludePasswords() {
        for (Object request : new Object[]{new ChangeOwnEmailRequest(editor.getEmail(), password),
                new ChangeOwnPasswordRequest(password, password), new VerifyPasswordRequest(password),
                new PanelRolRequest("Test", editor.getEmail(), Map.of(), password), new EliminarPanelRolRequest(password)}) {
            assertThat(request.toString()).doesNotContain(password);
        }
    }

    private void login(String email, String providedPassword, int expected) throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new LoginRequest(email, providedPassword)))).andExpect(status().is(expected));
    }
}
