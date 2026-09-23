package ar.edu.ifts2.contenido;

import ar.edu.ifts2.security.JwtService;
import ar.edu.ifts2.storage.StorageException;
import ar.edu.ifts2.storage.StorageService;
import ar.edu.ifts2.storage.model.StoredFile;
import ar.edu.ifts2.support.PostgresIntegrationTest;
import ar.edu.ifts2.usuario.entity.Rol;
import ar.edu.ifts2.usuario.entity.Usuario;
import ar.edu.ifts2.usuario.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContenidoIntegrationTest extends PostgresIntegrationTest {
    private static final List<String> COLLECTIONS = List.of("eventos", "carreras", "autoridades", "enlaces", "documentos");
    private static final Map<String, String> FILES = Map.of("eventos", "portada", "carreras", "imagen", "autoridades", "foto", "documentos", "archivo");
    private static final byte[] PNG = new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private static final byte[] PDF = "%PDF-1.7\nfixture".getBytes(StandardCharsets.US_ASCII);
    @MockitoBean
    private StorageService storage;
    private final MockMvc mvc;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private String adminToken;
    private String editorToken;
    private String inactiveToken;

    @Autowired
    ContenidoIntegrationTest(MockMvc mvc, ObjectMapper mapper, JdbcTemplate jdbc,
                             UsuarioRepository usuarios, PasswordEncoder encoder, JwtService jwt) {
        this.mvc = mvc;
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @BeforeAll
    void seedUsers() {
        String hash = encoder.encode(UUID.randomUUID().toString());
        Usuario admin = usuarios.saveAndFlush(new Usuario("Admin", "Test", "admin@example.test", hash, Rol.ADMIN));
        Usuario editor = usuarios.saveAndFlush(new Usuario("Editor", "Test", "editor@example.test", hash, Rol.EDITOR));
        Usuario inactive = new Usuario("Inactive", "Test", "inactive@example.test", hash, Rol.EDITOR);
        inactive.desactivar();
        usuarios.saveAndFlush(inactive);
    }

    @BeforeEach
    void refreshTokens() {
        Usuario admin = usuarios.findByEmail("admin@example.test").orElseThrow();
        Usuario editor = usuarios.findByEmail("editor@example.test").orElseThrow();
        Usuario inactive = usuarios.findByEmail("inactive@example.test").orElseThrow();
        adminToken = jwt.issue(admin.getId(), Rol.ADMIN).accessToken();
        editorToken = jwt.issue(editor.getId(), Rol.EDITOR).accessToken();
        inactiveToken = jwt.issue(inactive.getId(), Rol.EDITOR).accessToken();
    }

    @BeforeEach
    void resetContent() {
        for (String table : List.of("eventos", "carreras", "autoridades", "enlaces", "documentos", "institucion")) {
            jdbc.update("delete from " + table);
        }
        objects.clear();
        when(storage.upload(anyString(), anyString(), anyLong(), any(InputStream.class))).thenAnswer(call -> {
            String mime = call.getArgument(1);
            String key = call.<String>getArgument(0) + "/" + UUID.randomUUID() + (mime.equals("application/pdf") ? ".pdf" : ".png");
            objects.put(key, call.<InputStream>getArgument(3).readAllBytes());
            return new StoredFile(key, mime, call.getArgument(2));
        });
        when(storage.resolvePublicUrl(anyString())).thenAnswer(call -> URI.create("https://cdn.example.invalid/" + call.getArgument(0)));
        doAnswer(call -> { objects.remove(call.<String>getArgument(0)); return null; }).when(storage).delete(anyString());
    }

    static Stream<Arguments> modulesAndRoles() {
        return COLLECTIONS.stream().flatMap(module -> Stream.of("ADMIN", "EDITOR").map(role -> Arguments.of(module, role)));
    }

    @ParameterizedTest
    @MethodSource("modulesAndRoles")
    void editorialLifecycleWorksForBothRoles(String module, String role) throws Exception {
        String token = role.equals("ADMIN") ? adminToken : editorToken;
        String id = create(module, body(module), token);
        String admin = "/api/admin/" + module + "/" + id;
        String publicPath = "/api/" + module + "/" + id;
        mvc.perform(get(publicPath)).andExpect(status().isNotFound());
        mvc.perform(auth(get(publicPath), token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/" + module)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(auth(get(admin), token)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("BORRADOR"));
        mvc.perform(auth(get("/api/admin/" + module).param("estado", "BORRADOR"), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        ObjectNode updated = body(module).put(labelField(module), "Actualizado");
        mvc.perform(auth(put(admin), token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(updated)))
                .andExpect(status().isOk()).andExpect(jsonPath("$." + labelField(module)).value("Actualizado"));
        if (module.equals("documentos")) upload(module, id, token);
        changeState(module, id, "PUBLICADA", token, 200);
        mvc.perform(get(publicPath)).andExpect(status().isOk()).andExpect(jsonPath("$." + labelField(module)).value("Actualizado"))
                .andExpect(jsonPath("$.estado").doesNotExist()).andExpect(jsonPath("$.createdAt").doesNotExist());
        mvc.perform(head(publicPath)).andExpect(status().isOk());
        mvc.perform(get("/api/" + module).param("estado", "BORRADOR")).andExpect(jsonPath("$.totalElements").value(1));
        var first = readAdmin(module, id);
        changeState(module, id, "PUBLICADA", token, 200);
        assertThat(readAdmin(module, id).get("publicadaAt")).isEqualTo(first.get("publicadaAt"));
        changeState(module, id, "ARCHIVADA", token, 200);
        mvc.perform(get(publicPath)).andExpect(status().isNotFound());
        mvc.perform(get("/api/" + module)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(auth(get(admin), token)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ARCHIVADA"));
        changeState(module, id, "PUBLICADA", token, 200);
        mvc.perform(get(publicPath)).andExpect(status().isOk());
        mvc.perform(auth(delete(admin), token)).andExpect(status().isMethodNotAllowed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"eventos", "carreras", "autoridades", "enlaces", "documentos"})
    void writesAndAdministrativeReadsRequireJwt(String module) throws Exception {
        String id = create(module, body(module), editorToken);
        for (var request : List.of(get("/api/admin/" + module), get("/api/admin/" + module + "/" + id),
                post("/api/admin/" + module), put("/api/admin/" + module + "/" + id), put("/api/admin/" + module + "/" + id + "/estado"))) {
            mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        }
        mvc.perform(auth(post("/api/admin/" + module), inactiveToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body(module))))
                .andExpect(status().isUnauthorized());
        mvc.perform(auth(post("/api/" + module), editorToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body(module))))
                .andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/admin/usuarios"), editorToken)).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"eventos", "carreras", "autoridades", "enlaces", "documentos"})
    void listPaginationFiltersAndUnknownResourcesAreValidated(String module) throws Exception {
        create(module, body(module), editorToken);
        create(module, body(module), editorToken);
        mvc.perform(auth(get("/api/admin/" + module).param("size", "1"), editorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.content.length()").value(1));
        for (String path : List.of("/api/" + module, "/api/admin/" + module)) {
            for (String[] pair : new String[][]{{"size", "0"}, {"size", "101"}, {"page", "-1"}, {"page", "2147483647"}}) {
                mvc.perform(auth(get(path).param(pair[0], pair[1]), editorToken)).andExpect(status().isBadRequest());
            }
            mvc.perform(auth(get(path + "/invalid-uuid"), editorToken)).andExpect(status().isBadRequest());
            mvc.perform(auth(get(path + "/" + UUID.randomUUID()), editorToken)).andExpect(status().isNotFound());
        }
        mvc.perform(auth(get("/api/admin/" + module).param("estado", "OTRO"), editorToken)).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"eventos", "carreras", "autoridades", "enlaces", "documentos", "institucion"})
    void requestsRejectInvalidContentAndServerManagedFields(String module) throws Exception {
        String path = "/api/admin/" + module;
        for (String field : List.of("id", "estado", "publicadaAt", "createdAt", "updatedAt", "singleton", "archivoObjectKey", "portadaObjectKey", "imagenObjectKey", "fotoObjectKey")) {
            var request = module.equals("institucion") ? put(path) : post(path);
            mvc.perform(auth(request, editorToken).contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsBytes(body(module).put(field, "injected"))))
                    .andExpect(status().isBadRequest());
        }
        for (String invalid : List.of(" ", "x".repeat(201))) {
            var request = module.equals("institucion") ? put(path) : post(path);
            mvc.perform(auth(request, editorToken).contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsBytes(body(module).put(labelField(module), invalid))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isArray());
        }
        var request = module.equals("institucion") ? put(path) : post(path);
        mvc.perform(auth(request, editorToken).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void eventDatesAreConsistentAndPublicSinceFilterUsesInstants() throws Exception {
        ObjectNode invalid = body("eventos").put("fechaFin", "2030-01-01T12:00:00Z");
        mvc.perform(auth(post("/api/admin/eventos"), editorToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(invalid)))
                .andExpect(status().isBadRequest());
        String earlier = create("eventos", body("eventos"), editorToken);
        String later = create("eventos", body("eventos").put("fechaInicio", "2030-02-01T09:00:00-03:00").put("fechaFin", "2030-02-01T10:00:00-03:00"), editorToken);
        changeState("eventos", earlier, "PUBLICADA", editorToken, 200);
        changeState("eventos", later, "PUBLICADA", editorToken, 200);
        mvc.perform(get("/api/eventos")).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(earlier));
        mvc.perform(get("/api/eventos").param("desde", "2030-02-01T12:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(later));
        mvc.perform(get("/api/eventos").param("desde", "not-a-date")).andExpect(status().isBadRequest());
        assertThatThrownBy(() -> jdbc.update("update eventos set fecha_fin = fecha_inicio where id = ?", UUID.fromString(earlier)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"carreras", "autoridades", "enlaces"})
    void publicOrderIsExplicitAndBounded(String module) throws Exception {
        String high = create(module, body(module).put("orden", 8), editorToken);
        String low = create(module, body(module).put("orden", 1), editorToken);
        changeState(module, high, "PUBLICADA", editorToken, 200);
        changeState(module, low, "PUBLICADA", editorToken, 200);
        mvc.perform(get("/api/" + module)).andExpect(jsonPath("$.content[0].id").value(low));
        for (int order : new int[]{-1, 10001}) {
            mvc.perform(auth(post("/api/admin/" + module), editorToken).contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsBytes(body(module).put("orden", order))))
                    .andExpect(status().isBadRequest());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "data:text/html,test", "//example.test/path", "http://example.test", "https://user:password@example.test", "https://", "https://example.test:99999"})
    void unsafeExternalLinksAreRejected(String url) throws Exception {
        mvc.perform(auth(post("/api/admin/enlaces"), editorToken).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(body("enlaces").put("url", url))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void institutionHasOneEditorialRecordWithoutInventedInitialData() throws Exception {
        mvc.perform(get("/api/institucion")).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/admin/institucion"), editorToken)).andExpect(status().isNotFound());
        String id = create("institucion", body("institucion"), editorToken);
        mvc.perform(get("/api/institucion")).andExpect(status().isNotFound());
        changeState("institucion", id, "PUBLICADA", adminToken, 200);
        mvc.perform(get("/api/institucion")).andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Instituto de prueba"))
                .andExpect(jsonPath("$.estado").doesNotExist()).andExpect(jsonPath("$.singleton").doesNotExist());
        String updated = create("institucion", body("institucion").put("nombre", "Nombre actualizado"), editorToken);
        assertThat(updated).isEqualTo(id);
        assertThat(jdbc.queryForObject("select count(*) from institucion", Long.class)).isEqualTo(1);
        mvc.perform(get("/api/institucion")).andExpect(jsonPath("$.nombre").value("Nombre actualizado"));
        changeState("institucion", id, "ARCHIVADA", editorToken, 200);
        mvc.perform(get("/api/institucion")).andExpect(status().isNotFound());
        mvc.perform(auth(put("/api/admin/institucion"), editorToken).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(body("institucion").put("email", "invalid-email"))))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/institucion").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void simultaneousInstitutionCreationCannotCreateTwoRecords() throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> write = () -> {
                start.await(5, TimeUnit.SECONDS);
                return mvc.perform(auth(put("/api/admin/institucion"), editorToken).contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsBytes(body("institucion"))))
                        .andReturn().getResponse().getStatus();
            };
            var first = executor.submit(write);
            var second = executor.submit(write);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .contains(200).allMatch(status -> status == 200 || status == 409);
        }
        assertThat(jdbc.queryForObject("select count(*) from institucion", Long.class)).isEqualTo(1);
    }

    @Test
    void documentsNeedPdfAndCanBeFilteredByType() throws Exception {
        String id = create("documentos", body("documentos"), editorToken);
        changeState("documentos", id, "PUBLICADA", editorToken, 409);
        assertThatThrownBy(() -> jdbc.update("update documentos set estado='PUBLICADA', publicada_at=now() where id=?", UUID.fromString(id)))
                .isInstanceOf(DataIntegrityViolationException.class);
        String key = upload("documentos", id, editorToken);
        changeState("documentos", id, "PUBLICADA", editorToken, 200);
        mvc.perform(get("/api/documentos").param("tipo", "REGLAMENTO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/documentos").param("tipo", "HORARIO")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/documentos").param("tipo", "OTRO_TIPO")).andExpect(status().isBadRequest());
        mvc.perform(auth(delete("/api/admin/documentos/{id}/archivo", id), editorToken)).andExpect(status().isConflict());
        assertThat(objects).containsKey(key);
        changeState("documentos", id, "BORRADOR", editorToken, 200);
        mvc.perform(auth(delete("/api/admin/documentos/{id}/archivo", id), editorToken)).andExpect(status().isNoContent());
        assertThat(objects).doesNotContainKey(key);
        changeState("documentos", id, "PUBLICADA", editorToken, 409);
    }

    @ParameterizedTest
    @ValueSource(strings = {"eventos", "carreras", "autoridades", "documentos"})
    void fileOperationsUseOwnNamespaceAndCompensateFailedReplacement(String module) throws Exception {
        String id = create(module, body(module), editorToken);
        String path = "/api/admin/" + module + "/" + id + "/" + FILES.get(module);
        mvc.perform(multipart(HttpMethod.PUT, path).file(file(module))).andExpect(status().isUnauthorized());
        mvc.perform(delete(path)).andExpect(status().isUnauthorized());
        String oldKey = upload(module, id, editorToken);
        assertThat(oldKey).startsWith(module + "/");
        changeState(module, id, "PUBLICADA", editorToken, 200);
        mvc.perform(get("/api/" + module + "/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$." + FILES.get(module) + "Url").value("https://cdn.example.invalid/" + oldKey))
                .andExpect(jsonPath("$." + FILES.get(module) + "ObjectKey").doesNotExist());
        when(storage.resolvePublicUrl(anyString())).thenThrow(new StorageException(StorageException.Reason.PROVIDER_FAILURE));
        mvc.perform(auth(multipart(HttpMethod.PUT, path).file(file(module)), adminToken)).andExpect(status().isBadGateway());
        assertThat(objects).containsOnlyKeys(oldKey);
        assertThat(jdbc.queryForObject("select " + FILES.get(module) + "_object_key from " + module + " where id=?", String.class, UUID.fromString(id))).isEqualTo(oldKey);
        doAnswer(call -> URI.create("https://cdn.example.invalid/" + call.getArgument(0))).when(storage).resolvePublicUrl(anyString());
        String newKey = upload(module, id, adminToken);
        assertThat(objects).containsOnlyKeys(newKey);
        changeState(module, id, "BORRADOR", editorToken, 200);
        mvc.perform(auth(delete(path), editorToken)).andExpect(status().isNoContent());
        mvc.perform(auth(delete(path), editorToken)).andExpect(status().isNoContent());
        assertThat(objects).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"eventos", "carreras", "autoridades", "documentos"})
    void fileTypesAndSignaturesAreRestrictedByDomain(String module) throws Exception {
        String id = create(module, body(module), editorToken);
        String path = "/api/admin/" + module + "/" + id + "/" + FILES.get(module);
        boolean pdf = module.equals("documentos");
        mvc.perform(auth(multipart(HttpMethod.PUT, path).file(new MockMultipartFile("file", "ignored", pdf ? "image/png" : "application/pdf", pdf ? PNG : PDF)), editorToken))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(auth(multipart(HttpMethod.PUT, path).file(new MockMultipartFile("file", "ignored", pdf ? "application/pdf" : "image/png", "fake".getBytes(StandardCharsets.UTF_8))), editorToken))
                .andExpect(status().isBadRequest());
        mvc.perform(auth(multipart(HttpMethod.PUT, path), editorToken)).andExpect(status().isBadRequest());
        verify(storage, never()).upload(any(), any(), anyLong(), any());
    }

    @Test
    void swaggerDescribesAllModulesAndLimitsWithoutExposingEntities() throws Exception {
        var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        JsonNode spec = mapper.readTree(result.getResponse().getContentAsString());
        for (String module : COLLECTIONS) {
            assertThat(spec.at("/paths/~1api~1" + module + "/get/security").isMissingNode()).isTrue();
            assertThat(spec.at("/paths/~1api~1admin~1" + module + "/post/responses/201").isMissingNode()).isFalse();
            assertThat(spec.at("/paths/~1api~1admin~1" + module + "/get/security/0/bearerAuth").isArray()).isTrue();
        }
        for (var entry : FILES.entrySet()) {
            var operation = spec.at("/paths/~1api~1admin~1" + entry.getKey() + "~1{id}~1" + entry.getValue() + "/put");
            assertThat(operation.at("/requestBody/content/multipart~1form-data").isMissingNode()).isFalse();
            assertThat(operation.path("responses").has("503")).isTrue();
        }
        assertThat(spec.at("/components/schemas/EventoRequest/properties/titulo/maxLength").asInt()).isEqualTo(200);
        assertThat(spec.at("/components/schemas/DocumentoPublicaResponse/properties/archivoObjectKey").isMissingNode()).isTrue();
        assertThat(spec.at("/components/schemas/InstitucionRequest/properties/singleton").isMissingNode()).isTrue();
    }

    @Test
    void migrationFromPreviousMilestonePreservesExistingNewsAndFiles() {
        String schema = "migration_upgrade_test";
        try {
            Flyway.configure().dataSource(Objects.requireNonNull(jdbc.getDataSource())).schemas(schema).defaultSchema(schema)
                    .target("3").load().migrate();
            UUID id = UUID.randomUUID();
            String key = "noticias/" + UUID.randomUUID() + ".png";
            jdbc.update("insert into migration_upgrade_test.noticias(id,titulo,resumen,contenido,portada_object_key) values (?,?,?,?,?)",
                    id, "Conservar", "Resumen", "Contenido", key);
            var flyway = Flyway.configure().dataSource(jdbc.getDataSource()).schemas(schema).defaultSchema(schema).target("4").load();
            flyway.migrate();
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("4");
            assertThat(jdbc.queryForObject("select portada_object_key from migration_upgrade_test.noticias where id=?", String.class, id)).isEqualTo(key);
            assertThat(jdbc.queryForObject("select count(*) from migration_upgrade_test.institucion", Long.class)).isZero();
        } finally {
            jdbc.execute("drop schema if exists migration_upgrade_test cascade");
        }
    }

    private ObjectNode body(String module) {
        ObjectNode body = mapper.createObjectNode();
        switch (module) {
            case "eventos" -> body.put("titulo", "Jornada").put("resumen", "Resumen").put("descripcion", "Descripcion")
                    .put("fechaInicio", "2030-01-01T12:00:00Z").put("fechaFin", "2030-01-01T13:00:00Z").put("lugar", "Auditorio");
            case "carreras" -> body.put("nombre", "Desarrollo de Software").put("tituloOtorgado", "Tecnico Superior")
                    .put("descripcion", "Descripcion").put("duracion", "3 anos").put("modalidad", "Presencial").put("orden", 0);
            case "autoridades" -> body.put("nombre", "Nombre").put("apellido", "Apellido").put("cargo", "Rectoria").put("orden", 0);
            case "enlaces" -> body.put("titulo", "Sistema externo").put("url", "https://example.test/sistema?view=public#inicio").put("categoria", "Sistemas").put("orden", 0);
            case "documentos" -> body.put("titulo", "Reglamento").put("tipo", "REGLAMENTO");
            case "institucion" -> body.put("nombre", "Instituto de prueba").put("descripcion", "Descripcion").put("direccion", "Direccion institucional").put("email", "contacto@example.test");
            default -> throw new IllegalArgumentException(module);
        }
        return body;
    }

    private String labelField(String module) { return Set.of("carreras", "autoridades", "institucion").contains(module) ? "nombre" : "titulo"; }

    private String create(String module, ObjectNode body, String token) throws Exception {
        var request = module.equals("institucion") ? put("/api/admin/institucion") : post("/api/admin/" + module);
        var result = mvc.perform(auth(request, token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)))
                .andExpect(status().is(module.equals("institucion") ? 200 : 201)).andReturn();
        String id = mapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
        if (!module.equals("institucion")) assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/admin/" + module + "/" + id);
        return id;
    }

    private void changeState(String module, String id, String state, String token, int expected) throws Exception {
        String path = "/api/admin/" + module + (module.equals("institucion") ? "" : "/" + id) + "/estado";
        mvc.perform(auth(put(path), token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("estado", state))))
                .andExpect(status().is(expected));
    }

    private JsonNode readAdmin(String module, String id) throws Exception {
        var result = mvc.perform(auth(get("/api/admin/" + module + "/" + id), editorToken)).andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private MockMultipartFile file(String module) {
        return new MockMultipartFile("file", "../../ignored", module.equals("documentos") ? "application/pdf" : "image/png", module.equals("documentos") ? PDF : PNG);
    }

    private String upload(String module, String id, String token) throws Exception {
        var result = mvc.perform(auth(multipart(HttpMethod.PUT, "/api/admin/" + module + "/" + id + "/" + FILES.get(module)).file(file(module)), token))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("objectKey").asText();
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
