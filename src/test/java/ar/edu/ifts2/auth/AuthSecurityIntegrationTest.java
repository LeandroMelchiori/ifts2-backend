package ar.edu.ifts2.auth;

import ar.edu.ifts2.auth.dto.LoginRequest;
import ar.edu.ifts2.security.JwtProperties;
import ar.edu.ifts2.usuario.entity.Rol;
import ar.edu.ifts2.usuario.entity.Usuario;
import ar.edu.ifts2.usuario.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ar.edu.ifts2.support.PostgresIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthSecurityIntegrationTest extends PostgresIntegrationTest {
    private final MockMvc mvc;
    private final ObjectMapper mapper;
    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtDecoder decoder;
    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Flyway flyway;
    private final String password = UUID.randomUUID().toString();
    private Usuario admin;
    private Usuario editor;

    @Autowired
    AuthSecurityIntegrationTest(MockMvc mvc, ObjectMapper mapper, UsuarioRepository repository,
            PasswordEncoder passwordEncoder, JwtDecoder decoder, JwtEncoder encoder,
            JwtProperties properties, Flyway flyway) {
        this.mvc = mvc;
        this.mapper = mapper;
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.decoder = decoder;
        this.encoder = encoder;
        this.properties = properties;
        this.flyway = flyway;
    }

    @BeforeAll
    void createUsers() {
        String hash = passwordEncoder.encode(password);
        admin = repository.saveAndFlush(new Usuario("Admin", "Prueba", "admin@example.test", hash, Rol.ADMIN));
        editor = repository.saveAndFlush(new Usuario("Editor", "Prueba", "editor@example.test", hash, Rol.EDITOR));
        Usuario inactive = new Usuario("Inactivo", "Prueba", "inactive@example.test", hash, Rol.EDITOR);
        inactive.desactivar();
        repository.saveAndFlush(inactive);
    }

    @Test
    void loginReturnsMinimalSignedJwtAndNoSession() throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginRequest(" ADMIN@EXAMPLE.TEST ", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn();
        Jwt jwt = decoder.decode(mapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText());
        assertThat(jwt.getSubject()).isEqualTo(admin.getId().toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
        assertThat(jwt.getClaims()).containsOnlyKeys("sub", "role", "iss", "iat", "exp");
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(900));
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void incorrectPasswordReturns401() throws Exception {
        expectInvalidLogin("admin@example.test", UUID.randomUUID().toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing@example.test", "inactive@example.test"})
    void missingAndInactiveUsersReturnSame401(String email) throws Exception {
        expectInvalidLogin(email, password);
    }

    @Test
    void oversizedUtf8PasswordIsRejectedWithoutBcryptTruncation() throws Exception {
        expectInvalidLogin("admin@example.test", "\u00e9".repeat(37));
    }

    @Test
    void loginValidatesDtoWithoutEchoingRejectedValues() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginRequest("invalid-email", " "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/auth/login"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void malformedJsonUsesCommonErrorFormat() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Solicitud no valida"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void healthIsPublicAndStateless() throws Exception {
        var result = mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE)).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/test", "/api/admin/usuarios/test", "/api/admin/usuarios"})
    void protectedEndpointsRequireToken(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value(path));
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin@example.test", "editor@example.test"})
    void bothRolesCanAccessGeneralAdminArea(String email) throws Exception {
        mvc.perform(get("/api/admin/test").header(HttpHeaders.AUTHORIZATION, "Bearer " + login(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void adminCanAccessUserAdministrationProbe() throws Exception {
        mvc.perform(get("/api/admin/usuarios/test").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + login("admin@example.test")))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/usuarios/test", "/api/admin/usuarios"})
    void editorCannotAccessUserAdministration(String path) throws Exception {
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + login("editor@example.test")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("No tiene permisos para acceder al recurso"));
    }

    @Test
    void expiredJwtIsRejected() throws Exception {
        expectInvalidToken(sign(claims().issuedAt(Instant.now().minusSeconds(100)).expiresAt(Instant.now().minusSeconds(1))));
    }

    @Test
    void incorrectIssuerIsRejected() throws Exception {
        expectInvalidToken(sign(claims().issuer("another-api")));
    }

    @Test
    void missingExpirationIsRejected() throws Exception {
        expectInvalidToken(sign(JwtClaimsSet.builder().issuer(properties.issuer())
                .subject(admin.getId().toString()).claim("role", "ADMIN").issuedAt(Instant.now())));
    }

    @Test
    void missingRoleIsRejected() throws Exception {
        expectInvalidToken(sign(JwtClaimsSet.builder().issuer(properties.issuer())
                .subject(admin.getId().toString()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))));
    }

    @Test
    void invalidSubjectIsRejected() throws Exception {
        expectInvalidToken(sign(claims().subject("not-a-user-id")));
    }

    @Test
    void unsupportedRoleIsRejected() throws Exception {
        expectInvalidToken(sign(claims().claim("role", "SUPERADMIN")));
    }

    @Test
    void futureNotBeforeIsRejected() throws Exception {
        expectInvalidToken(sign(claims().notBefore(Instant.now().plusSeconds(30))));
    }

    @Test
    void modifiedSignatureIsRejected() throws Exception {
        String[] parts = login("admin@example.test").split("\\.");
        parts[2] = (parts[2].charAt(0) == 'A' ? "B" : "A") + parts[2].substring(1);
        expectInvalidToken(String.join(".", parts));
    }

    @Test
    void malformedTokenIsRejected() throws Exception {
        expectInvalidToken("not-a-jwt");
    }

    @Test
    void queryParameterTokensAreNotAccepted() throws Exception {
        mvc.perform(get("/api/admin/test").param("access_token", login("admin@example.test")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unspecifiedRoutesAreDenied() throws Exception {
        mvc.perform(get("/api/undeclared").header(HttpHeaders.AUTHORIZATION, "Bearer " + login("admin@example.test")))
                .andExpect(status().isForbidden());
    }

    @Test
    void swaggerDocumentsBearerAuthenticationAndPublicOperations() throws Exception {
        var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        JsonNode document = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(document.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
        assertThat(document.at("/paths/~1api~1admin~1test/get/security/0/bearerAuth").isArray()).isTrue();
        assertThat(document.at("/paths/~1api~1auth~1login/post/security").isMissingNode()).isTrue();
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void migrationAndPasswordPersistenceAreCorrect() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        Usuario persisted = repository.findById(admin.getId()).orElseThrow();
        assertThat(persisted.getPasswordHash()).startsWith("$2a$12$").isNotEqualTo(password);
        assertThat(passwordEncoder.matches(password, persisted.getPasswordHash())).isTrue();
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isEqualTo(persisted.getCreatedAt());
    }

    @Test
    @Transactional
    void normalizedEmailMustBeUnique() {
        Usuario duplicate = new Usuario("Otra", "Persona", " ADMIN@EXAMPLE.TEST ", admin.getPasswordHash(), Rol.EDITOR);
        assertThatThrownBy(() -> repository.saveAndFlush(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void updatesMaintainAuditTimestamps() {
        Usuario user = repository.findById(editor.getId()).orElseThrow();
        Instant created = user.getCreatedAt();
        user.desactivar();
        repository.flush();
        assertThat(user.getCreatedAt()).isEqualTo(created);
        assertThat(user.getUpdatedAt()).isAfter(created);
    }

    private String login(String email) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private void expectInvalidLogin(String email, String providedPassword) throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginRequest(email, providedPassword))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Credenciales invalidas"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    private JwtClaimsSet.Builder claims() {
        return JwtClaimsSet.builder().issuer(properties.issuer()).subject(admin.getId().toString())
                .claim("role", "ADMIN").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
    }

    private String sign(JwtClaimsSet.Builder claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }

    private void expectInvalidToken(String token) throws Exception {
        mvc.perform(get("/api/admin/test").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Autenticacion requerida o token invalido"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

}
