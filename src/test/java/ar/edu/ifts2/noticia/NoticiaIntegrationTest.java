package ar.edu.ifts2.noticia;

import ar.edu.ifts2.noticia.dto.NoticiaRequest;
import ar.edu.ifts2.noticia.entity.EstadoNoticia;
import ar.edu.ifts2.noticia.entity.Noticia;
import ar.edu.ifts2.noticia.repository.NoticiaRepository;
import ar.edu.ifts2.noticia.service.NoticiaService;
import ar.edu.ifts2.security.JwtService;
import ar.edu.ifts2.support.PostgresIntegrationTest;
import ar.edu.ifts2.usuario.entity.Rol;
import ar.edu.ifts2.usuario.entity.Usuario;
import ar.edu.ifts2.usuario.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NoticiaIntegrationTest extends PostgresIntegrationTest {
    private static final String ADMIN_PATH = "/api/admin/noticias";
    private final MockMvc mvc;
    private final ObjectMapper mapper;
    private final NoticiaRepository repository;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final NoticiaService service;
    private final JdbcTemplate jdbc;
    private final Flyway flyway;
    private String adminToken;
    private String editorToken;
    private String inactiveToken;

    @Autowired
    NoticiaIntegrationTest(MockMvc mvc, ObjectMapper mapper, NoticiaRepository repository,
                           UsuarioRepository usuarios, PasswordEncoder encoder, JwtService jwtService,
                           NoticiaService service, JdbcTemplate jdbc, Flyway flyway) {
        this.mvc = mvc;
        this.mapper = mapper;
        this.repository = repository;
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.jwtService = jwtService;
        this.service = service;
        this.jdbc = jdbc;
        this.flyway = flyway;
    }

    @BeforeAll
    void createUsers() {
        String hash = encoder.encode(UUID.randomUUID().toString());
        Usuario admin = usuarios.saveAndFlush(new Usuario("Admin", "Test", "admin@example.test", hash, Rol.ADMIN));
        Usuario editor = usuarios.saveAndFlush(new Usuario("Editor", "Test", "editor@example.test", hash, Rol.EDITOR));
        Usuario inactive = new Usuario("Inactive", "Test", "inactive@example.test", hash, Rol.EDITOR);
        inactive.desactivar();
        usuarios.saveAndFlush(inactive);
        adminToken = jwtService.issue(admin.getId(), admin.getRol()).accessToken();
        editorToken = jwtService.issue(editor.getId(), editor.getRol()).accessToken();
        inactiveToken = jwtService.issue(inactive.getId(), inactive.getRol()).accessToken();
    }

    @BeforeEach
    void clearNews() { repository.deleteAllInBatch(); }

    @ParameterizedTest
    @EnumSource(Rol.class)
    void bothRolesCanCreateEditPublishAndArchive(Rol rol) throws Exception {
        String token = rol == Rol.ADMIN ? adminToken : editorToken;
        var result = mvc.perform(authorized(post(ADMIN_PATH), token).contentType(MediaType.APPLICATION_JSON)
                        .content(body("  Jornada institucional  ")))
                .andExpect(status().isCreated()).andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.titulo").value("Jornada institucional"))
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.publicadaAt").isEmpty())
                .andExpect(jsonPath("$.createdAt").exists()).andExpect(jsonPath("$.updatedAt").exists()).andReturn();
        UUID id = UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(ADMIN_PATH + "/" + id);
        mvc.perform(authorized(get(ADMIN_PATH + "/{id}", id), token)).andExpect(status().isOk());
        mvc.perform(authorized(get(ADMIN_PATH), token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(authorized(put(ADMIN_PATH + "/{id}", id), token).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Titulo editado"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Titulo editado"));
        for (String estado : List.of("PUBLICADA", "ARCHIVADA")) {
            mvc.perform(authorized(put(ADMIN_PATH + "/{id}/estado", id), token).contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of("estado", estado))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value(estado));
        }
        assertThat(repository.existsById(id)).isTrue();
        mvc.perform(get("/api/noticias/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void publicListContainsOnlyPublishedSummariesAndIsPaginated() throws Exception {
        seed("Borrador privado", EstadoNoticia.BORRADOR);
        seed("Archivada privada", EstadoNoticia.ARCHIVADA);
        Noticia older = seed("Publicada anterior", EstadoNoticia.PUBLICADA);
        Noticia newer = seed("Publicada reciente", EstadoNoticia.PUBLICADA);
        jdbc.update("update noticias set publicada_at = ? where id = ?",
                java.sql.Timestamp.from(Instant.parse("2020-01-01T00:00:00Z")), older.getId());
        mvc.perform(get("/api/noticias").param("size", "1").param("estado", "BORRADOR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(newer.getId().toString()))
                .andExpect(jsonPath("$.content[0].contenido").doesNotExist())
                .andExpect(jsonPath("$.content[0].estado").doesNotExist())
                .andExpect(jsonPath("$.content[0].createdAt").doesNotExist());
        mvc.perform(get("/api/noticias").param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(older.getId().toString()));
        mvc.perform(get("/api/noticias").param("page", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void publicDetailAndHeadWorkWithoutAuthentication() throws Exception {
        Noticia noticia = seed("Publica", EstadoNoticia.PUBLICADA);
        var result = mvc.perform(get("/api/noticias/{id}", noticia.getId()))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.contenido").value("Contenido institucional"))
                .andExpect(jsonPath("$.publicadaAt").exists()).andExpect(jsonPath("$.estado").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").doesNotExist()).andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        mvc.perform(head("/api/noticias/{id}", noticia.getId())).andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoNoticia.class, names = {"BORRADOR", "ARCHIVADA"})
    void nonPublicNewsAlwaysReturn404OnPublicRoutes(EstadoNoticia estado) throws Exception {
        Noticia noticia = seed("Privada", estado);
        for (String token : List.of("", adminToken, editorToken)) {
            var request = get("/api/noticias/{id}", noticia.getId());
            if (!token.isEmpty()) { authorized(request, token); }
            mvc.perform(request).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Noticia no encontrada"))
                    .andExpect(jsonPath("$.contenido").doesNotExist());
        }
        mvc.perform(head("/api/noticias/{id}", noticia.getId())).andExpect(status().isNotFound());
    }

    @Test
    void emptyListIsPublic() throws Exception {
        mvc.perform(get("/api/noticias")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void allAdministrativeOperationsRequireJwt() throws Exception {
        UUID id = seed("Borrador", EstadoNoticia.BORRADOR).getId();
        var requests = List.of(get(ADMIN_PATH), get(ADMIN_PATH + "/" + id), post(ADMIN_PATH),
                put(ADMIN_PATH + "/" + id), put(ADMIN_PATH + "/" + id + "/estado"));
        for (var request : requests) {
            mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        }
    }

    @Test
    void invalidAndInactiveTokensCannotManageNews() throws Exception {
        for (String token : List.of("invalid-token", inactiveToken)) {
            mvc.perform(authorized(post(ADMIN_PATH), token).contentType(MediaType.APPLICATION_JSON).content(body("Nueva")))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(repository.count()).isZero();
    }

    @Test
    void publicRoutesNeverPermitWrites() throws Exception {
        UUID id = seed("Borrador", EstadoNoticia.BORRADOR).getId();
        var requests = List.of(post("/api/noticias"), put("/api/noticias/" + id), delete("/api/noticias/" + id));
        for (var request : requests) {
            mvc.perform(authorized(request, adminToken).contentType(MediaType.APPLICATION_JSON).content(body("Intento")))
                    .andExpect(status().isForbidden());
        }
        assertThat(repository.findById(id).orElseThrow().getTitulo()).isEqualTo("Borrador");
    }

    @ParameterizedTest
    @EnumSource(EstadoNoticia.class)
    void adminCanFilterEveryState(EstadoNoticia estado) throws Exception {
        for (EstadoNoticia value : EstadoNoticia.values()) { seed(value.name(), value); }
        mvc.perform(authorized(get(ADMIN_PATH).param("estado", estado.name()).param("size", "1"), editorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].estado").value(estado.name()));
        mvc.perform(authorized(get(ADMIN_PATH).param("size", "2"), adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void publicationIsIdempotentAndCanBeWithdrawnAndRestored() throws Exception {
        UUID id = seed("Nueva", EstadoNoticia.BORRADOR).getId();
        changeState(id, "PUBLICADA");
        Instant firstPublication = repository.findById(id).orElseThrow().getPublicadaAt();
        changeState(id, "PUBLICADA");
        assertThat(repository.findById(id).orElseThrow().getPublicadaAt()).isEqualTo(firstPublication);
        mvc.perform(get("/api/noticias/{id}", id)).andExpect(status().isOk());
        for (String hidden : List.of("BORRADOR", "ARCHIVADA")) {
            changeState(id, hidden);
            mvc.perform(get("/api/noticias/{id}", id)).andExpect(status().isNotFound());
            mvc.perform(get("/api/noticias")).andExpect(jsonPath("$.totalElements").value(0));
            assertThat(repository.findById(id).orElseThrow().getPublicadaAt()).isEqualTo(firstPublication);
        }
        jdbc.update("update noticias set publicada_at = ? where id = ?",
                java.sql.Timestamp.from(Instant.parse("2020-01-01T00:00:00Z")), id);
        changeState(id, "PUBLICADA");
        assertThat(repository.findById(id).orElseThrow().getPublicadaAt()).isAfter(firstPublication);
        mvc.perform(get("/api/noticias/{id}", id)).andExpect(status().isOk());
    }

    @Test
    void editingPublishedContentPreservesStateAndPublicationDate() throws Exception {
        UUID id = seed("Original", EstadoNoticia.PUBLICADA).getId();
        Noticia before = repository.findById(id).orElseThrow();
        mvc.perform(authorized(put(ADMIN_PATH + "/{id}", id), editorToken).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Actualizada"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PUBLICADA"));
        Noticia after = repository.findById(id).orElseThrow();
        assertThat(after.getPublicadaAt()).isEqualTo(before.getPublicadaAt());
        assertThat(after.getCreatedAt()).isEqualTo(before.getCreatedAt());
        assertThat(after.getUpdatedAt()).isAfter(before.getUpdatedAt());
        mvc.perform(get("/api/noticias/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Actualizada"));
    }

    @Test
    void concurrentContentAndStateUpdatesPreserveBothChanges() throws Exception {
        UUID id = seed("Original", EstadoNoticia.PUBLICADA).getId();
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var edit = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return service.actualizar(id, new NoticiaRequest("Editada", "Resumen", "Contenido actualizado"));
            });
            var archive = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return service.cambiarEstado(id, EstadoNoticia.ARCHIVADA);
            });
            edit.get(20, TimeUnit.SECONDS);
            archive.get(20, TimeUnit.SECONDS);
        }
        Noticia persisted = repository.findById(id).orElseThrow();
        assertThat(persisted.getTitulo()).isEqualTo("Editada");
        assertThat(persisted.getEstado()).isEqualTo(EstadoNoticia.ARCHIVADA);
    }

    @ParameterizedTest
    @CsvSource({"titulo,200", "resumen,500", "contenido,50000"})
    void contentFieldsAreRequiredAndBounded(String field, int max) throws Exception {
        UUID id = seed("Original", EstadoNoticia.BORRADOR).getId();
        for (String invalid : List.of(" ", "x".repeat(max + 1))) {
            var request = mapper.createObjectNode().put("titulo", "Titulo").put("resumen", "Resumen").put("contenido", "Contenido");
            request.put(field, invalid);
            for (var operation : List.of(post(ADMIN_PATH), put(ADMIN_PATH + "/" + id))) {
                mvc.perform(authorized(operation, editorToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value(field));
            }
        }
        var missing = mapper.createObjectNode().put("titulo", "Titulo").put("resumen", "Resumen").put("contenido", "Contenido");
        missing.remove(field);
        mvc.perform(authorized(post(ADMIN_PATH), editorToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(missing)))
                .andExpect(status().isBadRequest());
        assertThat(repository.findById(id).orElseThrow().getTitulo()).isEqualTo("Original");
    }

    @ParameterizedTest
    @ValueSource(strings = {"estado", "publicadaAt", "id", "createdAt", "updatedAt", "imagenObjectKey"})
    void contentRequestsRejectServerManagedAndUnsupportedFields(String field) throws Exception {
        UUID id = seed("Original", EstadoNoticia.BORRADOR).getId();
        var request = mapper.createObjectNode().put("titulo", "Titulo").put("resumen", "Resumen").put("contenido", "Contenido");
        request.put(field, "PUBLICADA");
        for (var operation : List.of(post(ADMIN_PATH), put(ADMIN_PATH + "/" + id))) {
            mvc.perform(authorized(operation, adminToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
        assertThat(repository.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoNoticia.BORRADOR);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"estado\":null}", "{\"estado\":\"OTRO\"}", "{\"estado\":\"PUBLICADA\",\"publicadaAt\":\"2099-01-01T00:00:00Z\"}"})
    void invalidStateRequestsFailWithoutPublishing(String json) throws Exception {
        UUID id = seed("Original", EstadoNoticia.BORRADOR).getId();
        mvc.perform(authorized(put(ADMIN_PATH + "/{id}/estado", id), editorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isBadRequest());
        assertThat(repository.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoNoticia.BORRADOR);
    }

    @ParameterizedTest
    @CsvSource({"page,-1", "size,0", "size,101", "size,-1", "page,text"})
    void paginationIsValidatedOnBothRoutes(String parameter, String value) throws Exception {
        for (String path : List.of("/api/noticias", ADMIN_PATH)) {
            mvc.perform(authorized(get(path).param(parameter, value), editorToken))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
    }

    @Test
    void invalidIdentifiersFiltersAndMissingNewsUseConsistentErrors() throws Exception {
        UUID missing = UUID.randomUUID();
        for (var request : List.of(get(ADMIN_PATH + "/" + missing), get("/api/noticias/" + missing),
                put(ADMIN_PATH + "/" + missing).contentType(MediaType.APPLICATION_JSON).content(body("Missing")),
                put(ADMIN_PATH + "/" + missing + "/estado").contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"PUBLICADA\"}"))) {
            mvc.perform(authorized(request, adminToken)).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Noticia no encontrada"))
                    .andExpect(jsonPath("$.trace").doesNotExist());
        }
        mvc.perform(get("/api/noticias/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(authorized(get(ADMIN_PATH).param("estado", "OTRO"), adminToken)).andExpect(status().isBadRequest());
    }

    @Test
    void archivalIsLogicalAndPhysicalDeletionIsNotExposed() throws Exception {
        UUID id = seed("Conservar", EstadoNoticia.PUBLICADA).getId();
        changeState(id, "ARCHIVADA");
        mvc.perform(authorized(delete(ADMIN_PATH + "/{id}", id), adminToken)).andExpect(status().isMethodNotAllowed());
        assertThat(repository.findById(id).orElseThrow().getContenido()).isEqualTo("Contenido institucional");
    }

    @Test
    void flywayEnforcesStateAndPublicationConstraints() {
        assertThat(flyway.info().applied()).anySatisfy(migration ->
                assertThat(migration.getVersion().getVersion()).isEqualTo("2"));
        UUID id = seed("Valida", EstadoNoticia.BORRADOR).getId();
        assertThatThrownBy(() -> jdbc.update("update noticias set estado = 'PUBLICADA' where id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update noticias set estado = 'OTRO' where id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update noticias set contenido = ? where id = ?", "x".repeat(50001), id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void swaggerSeparatesPublicAndAuthenticatedNewsContracts() throws Exception {
        var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        JsonNode spec = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(spec.at("/paths/~1api~1noticias/get/security").isMissingNode()).isTrue();
        assertThat(spec.at("/paths/~1api~1noticias~1{id}/get/responses/404").isMissingNode()).isFalse();
        assertThat(spec.at("/paths/~1api~1admin~1noticias/post/responses/201").isMissingNode()).isFalse();
        assertThat(spec.at("/paths/~1api~1admin~1noticias~1{id}~1estado/put/security/0/bearerAuth").isArray()).isTrue();
        assertThat(spec.at("/components/schemas/NoticiaRequest/properties/titulo/maxLength").asInt()).isEqualTo(200);
        assertThat(spec.at("/components/schemas/NoticiaRequest/properties/estado").isMissingNode()).isTrue();
        assertThat(spec.at("/components/schemas/NoticiaPublicaResponse/properties/estado").isMissingNode()).isTrue();
    }

    private Noticia seed(String titulo, EstadoNoticia estado) {
        Noticia noticia = new Noticia(titulo, "Resumen institucional", "Contenido institucional");
        noticia.cambiarEstado(estado);
        return repository.saveAndFlush(noticia);
    }

    private String body(String titulo) throws Exception {
        return mapper.writeValueAsString(new NoticiaRequest(titulo, "Resumen institucional", "Contenido institucional"));
    }

    private void changeState(UUID id, String estado) throws Exception {
        mvc.perform(authorized(put(ADMIN_PATH + "/{id}/estado", id), editorToken).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("estado", estado))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value(estado));
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
