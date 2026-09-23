package ar.edu.ifts2.contenido;

import ar.edu.ifts2.destacado.dto.DestacadosRequest;
import ar.edu.ifts2.destacado.service.DestacadoService;
import ar.edu.ifts2.evento.dto.EventoFotoRequest;
import ar.edu.ifts2.evento.service.EventoFotoService;
import ar.edu.ifts2.security.JwtService;
import ar.edu.ifts2.shared.error.BusinessConflictException;
import ar.edu.ifts2.storage.*;
import ar.edu.ifts2.storage.model.StoredFile;
import ar.edu.ifts2.support.PostgresIntegrationTest;
import ar.edu.ifts2.usuario.entity.*;
import ar.edu.ifts2.usuario.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MaquetaIntegrationTest extends PostgresIntegrationTest {
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    @MockitoBean
    private StorageService storage;
    private final MockMvc mvc;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final DestacadoService destacados;
    private final EventoFotoService fotos;
    private final TransactionTemplate tx;
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private String token;
    private String adminToken;
    private String inactiveToken;

    @Autowired
    MaquetaIntegrationTest(MockMvc mvc, ObjectMapper mapper, JdbcTemplate jdbc, UsuarioRepository usuarios,
            PasswordEncoder encoder, JwtService jwt, DestacadoService destacados, EventoFotoService fotos,
            PlatformTransactionManager transactions) {
        this.mvc = mvc;
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.jwt = jwt;
        this.destacados = destacados;
        this.fotos = fotos;
        this.tx = new TransactionTemplate(transactions);
    }

    @BeforeAll
    void seedUsers() {
        String hash = encoder.encode(UUID.randomUUID().toString());
        usuarios.saveAndFlush(new Usuario("Editor", "Test", "editor@maqueta.test", hash, Rol.EDITOR));
        usuarios.saveAndFlush(new Usuario("Admin", "Test", "admin@maqueta.test", hash, Rol.ADMIN));
        Usuario inactive = new Usuario("Inactive", "Test", "inactive@maqueta.test", hash, Rol.EDITOR);
        inactive.desactivar();
        usuarios.saveAndFlush(inactive);
    }

    @BeforeEach
    void prepare() {
        token = jwt.issue(usuarios.findByEmail("editor@maqueta.test").orElseThrow().getId(), Rol.EDITOR).accessToken();
        adminToken = jwt.issue(usuarios.findByEmail("admin@maqueta.test").orElseThrow().getId(), Rol.ADMIN).accessToken();
        inactiveToken = jwt.issue(usuarios.findByEmail("inactive@maqueta.test").orElseThrow().getId(), Rol.EDITOR).accessToken();
        for (String table : List.of("destacados", "evento_fotos", "noticias", "eventos", "institucion")) {
            jdbc.update("delete from " + table);
        }
        objects.clear();
        doAnswer(call -> {
            String key = call.<String>getArgument(0) + "/" + UUID.randomUUID() + ".png";
            objects.put(key, call.<InputStream>getArgument(3).readAllBytes());
            return new StoredFile(key, call.getArgument(1), call.getArgument(2));
        }).when(storage).upload(anyString(), anyString(), anyLong(), any(InputStream.class));
        doAnswer(call -> URI.create("https://cdn.example.invalid/" + call.getArgument(0)))
                .when(storage).resolvePublicUrl(anyString());
        doAnswer(call -> { objects.remove(call.<String>getArgument(0)); return null; })
                .when(storage).delete(anyString());
    }

    @Test
    void newsAcceptOldPayloadAndPreserveEditorialMetadataOnLegacyEdit() throws Exception {
        String id = create("noticias", news());
        mvc.perform(auth(get("/api/admin/noticias/" + id))).andExpect(jsonPath("$.area").value("GENERAL"))
                .andExpect(jsonPath("$.mostrarEnNovedades").value(true)).andExpect(jsonPath("$.fecha").exists());
        ObjectNode extended = news().put("area", "ALUMNOS").put("mostrarEnNovedades", false)
                .put("enlaceUrl", "https://example.test/recurso").put("fecha", "2025-03-10");
        mvc.perform(auth(put("/api/admin/noticias/" + id)).contentType(MediaType.APPLICATION_JSON).content(json(extended)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.area").value("ALUMNOS"));
        publish("noticias", id, "PUBLICADA");
        mvc.perform(get("/api/noticias/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.enlaceUrl").value("https://example.test/recurso"))
                .andExpect(jsonPath("$.fecha").value("2025-03-10"));
        mvc.perform(auth(put("/api/admin/noticias/" + id)).contentType(MediaType.APPLICATION_JSON).content(json(news())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.area").value("ALUMNOS"))
                .andExpect(jsonPath("$.mostrarEnNovedades").value(false))
                .andExpect(jsonPath("$.fecha").value("2025-03-10")).andExpect(jsonPath("$.enlaceUrl").isEmpty())
                .andExpect(jsonPath("$.estado").value("PUBLICADA"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GENERAL", "ALUMNOS", "DOCENTES", "TUTORIA", "EVENTOS"})
    void filtersCombineAreaPublicationAndNewsVisibility(String area) throws Exception {
        String visible = create("noticias", news().put("area", area).put("mostrarEnNovedades", true));
        String other = create("noticias", news().put("area", area).put("mostrarEnNovedades", false));
        create("noticias", news().put("area", area));
        publish("noticias", visible, "PUBLICADA");
        publish("noticias", other, "PUBLICADA");
        mvc.perform(get("/api/noticias").param("area", area).param("mostrarEnNovedades", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(visible));
        mvc.perform(get("/api/noticias").param("area", area).param("mostrarEnNovedades", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(other));
        mvc.perform(auth(get("/api/admin/noticias").param("area", area)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "http://example.test", "https://user:pass@example.test", "https://", ""})
    void editorialLinksRejectUnsafeUrls(String url) throws Exception {
        mvc.perform(auth(post("/api/admin/noticias")).contentType(MediaType.APPLICATION_JSON)
                .content(json(news().put("enlaceUrl", url)))).andExpect(status().isBadRequest());
    }

    @Test
    void filtersAndEditorialInputAreValidated() throws Exception {
        for (String query : List.of("area=DESCONOCIDA", "mostrarEnNovedades=invalid", "page=1000001")) {
            mvc.perform(get("/api/noticias?" + query)).andExpect(status().isBadRequest());
        }
        mvc.perform(auth(post("/api/admin/noticias")).contentType(MediaType.APPLICATION_JSON)
                .content(json(news().put("fecha", "2025-02-30")))).andExpect(status().isBadRequest());
        mvc.perform(auth(post("/api/admin/noticias")).contentType(MediaType.APPLICATION_JSON)
                .content(json(news().put("area", "DESCONOCIDA")))).andExpect(status().isBadRequest());
    }

    @Test
    void siteSettingsPreserveInstitutionAndHideDisabledSocialUrls() throws Exception {
        initializeInstitution();
        ObjectNode data = siteData().put("facebookVisible", false);
        mvc.perform(auth(put("/api/admin/institucion/datos-sitio")).contentType(MediaType.APPLICATION_JSON).content(json(data)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("IFTS 2"))
                .andExpect(jsonPath("$.descripcion").value("Institucion")).andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.facebookUrl").value("https://www.facebook.com/ifts2"));
        mvc.perform(get("/api/institucion")).andExpect(status().isNotFound());
        publish("institucion", null, "PUBLICADA");
        mvc.perform(get("/api/institucion")).andExpect(status().isOk())
                .andExpect(jsonPath("$.busquedaMapa").value("Villa Lugano, Buenos Aires"))
                .andExpect(jsonPath("$.instagramUrl").value("https://www.instagram.com/ifts2"))
                .andExpect(jsonPath("$.facebookUrl").isEmpty());
        initializeInstitution();
        mvc.perform(auth(get("/api/admin/institucion"))).andExpect(jsonPath("$.instagramVisible").value(true));
    }

    @Test
    void siteSettingsRequireExistingInstitutionAndValidVisibleProfiles() throws Exception {
        mvc.perform(auth(put("/api/admin/institucion/datos-sitio")).contentType(MediaType.APPLICATION_JSON)
                .content(json(siteData()))).andExpect(status().isNotFound());
        initializeInstitution();
        for (ObjectNode invalid : List.of(siteData().putNull("instagramUrl"), siteData().putNull("facebookUrl"),
                siteData().put("sitioOficialUrl", "javascript:alert(1)"), siteData().put("email", "invalid"),
                siteData().putNull("instagramVisible"), siteData().put("busquedaMapa", "x".repeat(501)))) {
            mvc.perform(auth(put("/api/admin/institucion/datos-sitio")).contentType(MediaType.APPLICATION_JSON)
                    .content(json(invalid))).andExpect(status().isBadRequest());
        }
        assertThatThrownBy(() -> jdbc.update("update institucion set instagram_visible = true"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void carouselSupportsMixedSelectionOrderWithdrawalAndEmptyList() throws Exception {
        mvc.perform(get("/api/destacados")).andExpect(status().isOk()).andExpect(content().json("[]"));
        String n = create("noticias", news());
        String e = create("eventos", event());
        publish("noticias", n, "PUBLICADA");
        publish("eventos", e, "PUBLICADA");
        setHighlights(List.of(Map.of("noticiaId", n), Map.of("eventoId", e)), 200);
        mvc.perform(get("/api/destacados")).andExpect(jsonPath("$[0].noticiaId").value(n))
                .andExpect(jsonPath("$[1].eventoId").value(e)).andExpect(jsonPath("$[0].objectKey").doesNotExist());
        setHighlights(List.of(Map.of("eventoId", e), Map.of("noticiaId", n)), 200);
        mvc.perform(get("/api/destacados")).andExpect(jsonPath("$[0].eventoId").value(e));
        publish("noticias", n, "ARCHIVADA");
        mvc.perform(get("/api/destacados")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(auth(get("/api/admin/destacados"))).andExpect(jsonPath("$[1].disponible").value(false));
        publish("noticias", n, "PUBLICADA");
        mvc.perform(get("/api/destacados")).andExpect(jsonPath("$.length()").value(2));
        setHighlights(List.of(), 200);
        mvc.perform(get("/api/destacados")).andExpect(content().json("[]"));
    }

    @Test
    void invalidCarouselUpdateDoesNotLosePreviousSelection() throws Exception {
        String n = create("noticias", news());
        String draft = create("noticias", news());
        publish("noticias", n, "PUBLICADA");
        setHighlights(List.of(Map.of("noticiaId", n)), 200);
        setHighlights(List.of(Map.of("noticiaId", n), Map.of("noticiaId", n)), 409);
        setHighlights(List.of(Map.of("noticiaId", draft)), 409);
        setHighlights(List.of(Map.of("eventoId", UUID.randomUUID().toString())), 404);
        setHighlights(Collections.nCopies(7, Map.of("noticiaId", n)), 400);
        setHighlights(List.of(Map.of()), 400);
        setHighlights(List.of(Map.of("noticiaId", n, "eventoId", n)), 400);
        setHighlights(Arrays.asList((Object) null), 400);
        mvc.perform(get("/api/destacados")).andExpect(jsonPath("$[0].noticiaId").value(n));
    }

    @Test
    void carouselConcurrentEditsKeepOneCompleteList() throws Exception {
        List<DestacadosRequest.Referencia> first = new ArrayList<>();
        List<DestacadosRequest.Referencia> second = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String a = create("noticias", news().put("titulo", "Primero " + i));
            String b = create("eventos", event().put("titulo", "Segundo " + i));
            publish("noticias", a, "PUBLICADA");
            publish("eventos", b, "PUBLICADA");
            first.add(new DestacadosRequest.Referencia(UUID.fromString(a), null));
            second.add(new DestacadosRequest.Referencia(null, UUID.fromString(b)));
        }
        race(() -> { destacados.reemplazar(new DestacadosRequest(first)); return true; },
                () -> { destacados.reemplazar(new DestacadosRequest(second)); return true; });
        var result = destacados.listarAdmin();
        assertThat(result).hasSize(6);
        assertThat(result.stream().allMatch(x -> x.noticiaId() != null)
                || result.stream().allMatch(x -> x.eventoId() != null)).isTrue();
        assertThat(result.stream().map(x -> x.posicion()).toList()).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    void galleryCrudAndReplacementPreserveParentAndCleanStorage() throws Exception {
        String event = create("eventos", event());
        JsonNode first = upload(event, "Primera", 2);
        JsonNode second = upload(event, "Segunda", 1);
        String firstKey = first.path("objectKey").asText();
        String path = "/api/admin/eventos/" + event + "/fotos/" + first.path("id").asText();
        mvc.perform(auth(get("/api/admin/eventos/" + event + "/fotos")))
                .andExpect(jsonPath("$[0].id").value(second.path("id").asText()));
        mvc.perform(get("/api/eventos/" + event + "/fotos")).andExpect(status().isNotFound());
        mvc.perform(get("/api/galeria")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(auth(put(path)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"etiqueta\":\"Preparacion\",\"orden\":0}")).andExpect(status().isOk());
        publish("eventos", event, "PUBLICADA");
        mvc.perform(get("/api/eventos/" + event + "/fotos")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].etiqueta").value("Preparacion"))
                .andExpect(jsonPath("$[0].objectKey").doesNotExist());
        mvc.perform(get("/api/galeria").param("eventoId", event)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(auth(get("/api/admin/galeria").param("estado", "PUBLICADA")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        JsonNode replaced = read(mvc.perform(auth(multipart(HttpMethod.PUT, path + "/archivo").file(image())))
                .andExpect(status().isOk()).andReturn());
        assertThat(objects).doesNotContainKey(firstKey).containsKey(replaced.path("objectKey").asText());
        mvc.perform(auth(delete(path))).andExpect(status().isNoContent());
        assertThat(objects).hasSize(1);
        mvc.perform(auth(delete(path))).andExpect(status().isNotFound());
        publish("eventos", event, "ARCHIVADA");
        mvc.perform(get("/api/galeria")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(auth(get("/api/admin/galeria"))).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void galleryCannotModifyPhotoThroughAnotherEvent() throws Exception {
        String owner = create("eventos", event());
        String other = create("eventos", event());
        JsonNode photo = upload(owner, "Foto", 0);
        String path = "/api/admin/eventos/" + other + "/fotos/" + photo.path("id").asText();
        mvc.perform(auth(put(path)).contentType(MediaType.APPLICATION_JSON).content("{\"etiqueta\":\"Otra\",\"orden\":1}"))
                .andExpect(status().isNotFound());
        mvc.perform(auth(multipart(HttpMethod.PUT, path + "/archivo").file(image()))).andExpect(status().isNotFound());
        mvc.perform(auth(delete(path))).andExpect(status().isNotFound());
        assertThat(objects).hasSize(1);
        verify(storage, times(1)).upload(anyString(), anyString(), anyLong(), any());
        verify(storage, never()).delete(anyString());
    }

    @Test
    void gallerySortsByEventDateAndThenPhotoOrder() throws Exception {
        String old = create("eventos", event());
        String recent = create("eventos", event().put("fechaInicio", "2031-02-01T12:00:00Z").put("fechaFin", "2031-02-01T13:00:00Z"));
        upload(old, "Anterior", 0);
        upload(recent, "Segunda", 2);
        upload(recent, "Primera", 1);
        publish("eventos", old, "PUBLICADA");
        publish("eventos", recent, "PUBLICADA");
        mvc.perform(get("/api/galeria").param("size", "2")).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].etiqueta").value("Primera"))
                .andExpect(jsonPath("$.content[1].etiqueta").value("Segunda"));
        mvc.perform(get("/api/galeria").param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.content[0].etiqueta").value("Anterior"));
        mvc.perform(get("/api/galeria").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/galeria").param("page", "1000001")).andExpect(status().isBadRequest());
    }

    @Test
    void galleryValidatesMetadataFileTypesAndMissingPartsBeforeUpload() throws Exception {
        String e = create("eventos", event());
        String path = "/api/admin/eventos/" + e + "/fotos";
        for (String data : List.of("{\"etiqueta\":\" \",\"orden\":0}", "{\"etiqueta\":\"Foto\",\"orden\":-1}",
                "{\"etiqueta\":\"Foto\",\"orden\":10001}", "{\"etiqueta\":\"Foto\"}",
                "{\"etiqueta\":\"Foto\",\"orden\":0,\"objectKey\":\"malicioso\"}")) {
            mvc.perform(auth(multipart(path).file(image()).file(dataPart(data)))).andExpect(status().isBadRequest());
        }
        mvc.perform(auth(multipart(path).file(image()))).andExpect(status().isBadRequest());
        mvc.perform(auth(multipart(path).file(dataPart("{\"etiqueta\":\"Foto\",\"orden\":0}"))
                .file(new MockMultipartFile("file", "x.pdf", "application/pdf", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)))))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(auth(multipart(path).file(dataPart("{\"etiqueta\":\"Foto\",\"orden\":0}"))
                .file(new MockMultipartFile("file", "x.png", "image/png", new byte[]{1, 2, 3}))))
                .andExpect(status().isBadRequest());
        verify(storage, never()).upload(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void failedPhotoReplacementRollsBackDatabaseAndDeletesOnlyNewObject() throws Exception {
        String event = create("eventos", event());
        JsonNode photo = upload(event, "Original", 0);
        String key = photo.path("objectKey").asText();
        doThrow(new StorageException(StorageException.Reason.PROVIDER_FAILURE)).when(storage).resolvePublicUrl(anyString());
        mvc.perform(auth(multipart(HttpMethod.PUT, "/api/admin/eventos/" + event + "/fotos/" + photo.path("id").asText() + "/archivo")
                .file(image()))).andExpect(status().isBadGateway());
        assertThat(objects.keySet()).containsExactly(key);
        assertThat(jdbc.queryForObject("select object_key from evento_fotos where id=?", String.class,
                UUID.fromString(photo.path("id").asText()))).isEqualTo(key);
    }

    @Test
    void transactionRollbackPreservesDeletedPhotoAndItsObject() throws Exception {
        String event = create("eventos", event());
        JsonNode photo = upload(event, "Original", 0);
        tx.executeWithoutResult(status -> {
            fotos.eliminar(UUID.fromString(event), UUID.fromString(photo.path("id").asText()));
            status.setRollbackOnly();
        });
        assertThat(objects).containsKey(photo.path("objectKey").asText());
        assertThat(jdbc.queryForObject("select count(*) from evento_fotos", Long.class)).isEqualTo(1);
        verify(storage, never()).delete(anyString());
    }

    @Test
    void uploadIsCompensatedOnExplicitRollbackAndFailedCleanupDoesNotUndoDeletion() throws Exception {
        UUID e = UUID.fromString(create("eventos", event()));
        tx.executeWithoutResult(status -> {
            fotos.agregar(e, new EventoFotoRequest("Foto", 0), "image/png", PNG.length, new ByteArrayInputStream(PNG));
            status.setRollbackOnly();
        });
        assertThat(objects).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from evento_fotos", Long.class)).isZero();
        JsonNode photo = upload(e.toString(), "Foto", 0);
        doThrow(new StorageException(StorageException.Reason.PROVIDER_FAILURE)).when(storage).delete(anyString());
        mvc.perform(auth(delete("/api/admin/eventos/" + e + "/fotos/" + photo.path("id").asText())))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from evento_fotos", Long.class)).isZero();
        assertThat(objects).hasSize(1);
    }

    @Test
    void concurrentPhotoUploadsCannotExceedLimit() throws Exception {
        UUID event = UUID.fromString(create("eventos", event()));
        for (int i = 0; i < 49; i++) {
            jdbc.update("insert into evento_fotos(id,evento_id,object_key,etiqueta,orden,created_at,updated_at) values(?,?,?,?,?,now(),now())",
                    UUID.randomUUID(), event, "eventos/" + UUID.randomUUID() + ".png", "Fixture", i);
        }
        Callable<Boolean> upload = () -> {
            try {
                fotos.agregar(event, new EventoFotoRequest("Ultima", 50), "image/png", PNG.length, new ByteArrayInputStream(PNG));
                return true;
            } catch (BusinessConflictException ex) { return false; }
        };
        assertThat(race(upload, upload)).containsExactlyInAnyOrder(true, false);
        assertThat(jdbc.queryForObject("select count(*) from evento_fotos", Long.class)).isEqualTo(50);
        assertThat(objects).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/destacados", "/api/admin/galeria"})
    void newAdminReadsRequireActiveUserAndAllowBothRoles(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + inactiveToken)).andExpect(status().isUnauthorized());
        mvc.perform(auth(get(path))).andExpect(status().isOk());
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)).andExpect(status().isOk());
    }

    @Test
    void writesAreProtectedAndEditorStillCannotManageUsers() throws Exception {
        String event = create("eventos", event());
        mvc.perform(put("/api/admin/destacados").contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/admin/institucion/datos-sitio").contentType(MediaType.APPLICATION_JSON).content(json(siteData())))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/admin/eventos/" + event + "/fotos").file(image())
                .file(dataPart("{\"etiqueta\":\"Foto\",\"orden\":0}"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/galeria")).andExpect(status().isUnauthorized());
        mvc.perform(auth(post("/api/destacados"))).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/admin/usuarios"))).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/destacados").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}")).andExpect(status().isOk());
    }

    @Test
    void migrationV5PreservesExistingContentAndAddsConstraints() {
        String schema = "maqueta_upgrade_test";
        try {
            Flyway.configure().dataSource(Objects.requireNonNull(jdbc.getDataSource())).schemas(schema).defaultSchema(schema)
                    .target("4").load().migrate();
            UUID id = UUID.randomUUID();
            jdbc.update("insert into maqueta_upgrade_test.noticias(id,titulo,resumen,contenido,estado,publicada_at) values(?,?,?,?,?,?::timestamptz)",
                    id, "Anterior", "Resumen", "Texto", "PUBLICADA", "2025-03-10T23:30:00Z");
            var flyway = Flyway.configure().dataSource(jdbc.getDataSource()).schemas(schema).defaultSchema(schema).load();
            flyway.migrate();
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("5");
            assertThat(jdbc.queryForObject("select area from maqueta_upgrade_test.noticias where id=?", String.class, id)).isEqualTo("GENERAL");
            assertThat(jdbc.queryForObject("select mostrar_en_novedades from maqueta_upgrade_test.noticias where id=?", Boolean.class, id)).isTrue();
            assertThat(jdbc.queryForObject("select fecha::text from maqueta_upgrade_test.noticias where id=?", String.class, id)).isEqualTo("2025-03-10");
            assertThat(jdbc.queryForObject("select count(*) from maqueta_upgrade_test.carrusel", Long.class)).isEqualTo(1);
            assertThatThrownBy(() -> jdbc.update("insert into maqueta_upgrade_test.destacados(posicion,noticia_id) values(7,?)", id))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("insert into maqueta_upgrade_test.destacados(posicion) values(1)"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("update maqueta_upgrade_test.noticias set area='INVALID'"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbc.execute("drop schema if exists maqueta_upgrade_test cascade");
        }
    }

    @Test
    void openApiIncludesFiltersAndMultipartWithoutJpaEntities() throws Exception {
        JsonNode spec = read(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn());
        assertThat(spec.at("/paths/~1api~1destacados/get/security").isMissingNode()).isTrue();
        assertThat(spec.at("/paths/~1api~1admin~1destacados/put/security/0/bearerAuth").isArray()).isTrue();
        assertThat(spec.at("/paths/~1api~1admin~1eventos~1{eventoId}~1fotos/post/requestBody/content/multipart~1form-data").isMissingNode()).isFalse();
        assertThat(spec.at("/components/schemas/NoticiaRequest/properties/area").isMissingNode()).isFalse();
        assertThat(spec.at("/components/schemas/EventoFotoPublicaResponse/properties/objectKey").isMissingNode()).isTrue();
        assertThat(spec.at("/components/schemas/DestacadosRequest/properties/items/maxItems").asInt()).isEqualTo(6);
    }

    private ObjectNode news() {
        return mapper.createObjectNode().put("titulo", "Publicacion").put("resumen", "Resumen").put("contenido", "Texto");
    }

    private ObjectNode event() {
        return mapper.createObjectNode().put("titulo", "Evento").put("resumen", "Resumen").put("descripcion", "Texto")
                .put("fechaInicio", "2030-01-01T12:00:00Z").put("fechaFin", "2030-01-01T13:00:00Z").put("lugar", "Instituto");
    }

    private ObjectNode siteData() {
        return mapper.createObjectNode().put("direccion", "Villa Lugano").put("email", "ifts@example.test").put("telefono", "123456")
                .put("busquedaMapa", "Villa Lugano, Buenos Aires").put("sitioOficialUrl", "https://example.test")
                .put("instagramUrl", "https://www.instagram.com/ifts2").put("instagramVisible", true)
                .put("facebookUrl", "https://www.facebook.com/ifts2").put("facebookVisible", true);
    }

    private void initializeInstitution() throws Exception {
        mvc.perform(auth(put("/api/admin/institucion")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"IFTS 2\",\"descripcion\":\"Institucion\",\"direccion\":\"Buenos Aires\",\"email\":\"ifts@example.test\"}"))
                .andExpect(status().isOk());
    }

    private String create(String module, ObjectNode body) throws Exception {
        return read(mvc.perform(auth(post("/api/admin/" + module)).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated()).andReturn()).path("id").asText();
    }

    private void publish(String module, String id, String estado) throws Exception {
        mvc.perform(auth(put("/api/admin/" + module + (id == null ? "" : "/" + id) + "/estado"))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("estado", estado)))).andExpect(status().isOk());
    }

    private void setHighlights(List<?> items, int status) throws Exception {
        mvc.perform(auth(put("/api/admin/destacados")).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("items", items))))
                .andExpect(status().is(status));
    }

    private JsonNode upload(String event, String etiqueta, int orden) throws Exception {
        return read(mvc.perform(auth(multipart("/api/admin/eventos/" + event + "/fotos").file(image())
                .file(dataPart(json(Map.of("etiqueta", etiqueta, "orden", orden))))))
                .andExpect(status().isCreated()).andReturn());
    }

    private MockMultipartFile image() { return new MockMultipartFile("file", "ignored.png", "image/png", PNG); }
    private MockMultipartFile dataPart(String json) {
        return new MockMultipartFile("datos", "", "application/json", json.getBytes(StandardCharsets.UTF_8));
    }
    private String json(Object body) throws Exception { return mapper.writeValueAsString(body); }
    private JsonNode read(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsString()); }
    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
    private <T> List<T> race(Callable<T> first, Callable<T> second) throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Future<T> a = pool.submit(() -> { start.await(); return first.call(); });
            Future<T> b = pool.submit(() -> { start.await(); return second.call(); });
            start.countDown();
            return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        }
    }
}
