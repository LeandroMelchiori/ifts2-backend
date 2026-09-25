package ar.edu.ifts2.meta;

import ar.edu.ifts2.meta.client.*;
import ar.edu.ifts2.meta.entity.TipoMedia;
import ar.edu.ifts2.meta.service.MetaPostService;
import ar.edu.ifts2.security.JwtService;
import ar.edu.ifts2.storage.*;
import ar.edu.ifts2.storage.model.*;
import ar.edu.ifts2.support.PostgresIntegrationTest;
import ar.edu.ifts2.usuario.entity.*;
import ar.edu.ifts2.usuario.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MetaIntegrationTest extends PostgresIntegrationTest {
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    @MockitoBean private MetaGraphClient graph;
    @MockitoBean private MetaPreviewDownloader downloader;
    @MockitoBean private StorageService storage;
    private final MockMvc mvc;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final UsuarioRepository usuarios;
    private final JwtService tokens;
    private final PasswordEncoder encoder;
    private final MetaPostService service;
    private final TransactionTemplate tx;
    private String token;
    private String admin;
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Autowired
    MetaIntegrationTest(MockMvc mvc, ObjectMapper mapper, JdbcTemplate jdbc, UsuarioRepository usuarios,
            JwtService tokens, PasswordEncoder encoder, MetaPostService service, PlatformTransactionManager transactions) {
        this.mvc = mvc; this.mapper = mapper; this.jdbc = jdbc; this.usuarios = usuarios;
        this.tokens = tokens; this.service = service; this.tx = new TransactionTemplate(transactions);
        this.encoder = encoder;
    }

    @BeforeAll
    void users() {
        String hash = encoder.encode(UUID.randomUUID().toString());
        Usuario editor = usuarios.saveAndFlush(new Usuario("Meta", "Editor", "editor@meta.test", hash, Rol.EDITOR));
        Usuario owner = usuarios.saveAndFlush(new Usuario("Meta", "Admin", "admin@meta.test", hash, Rol.ADMIN));
        token = tokens.issue(editor.getId(), Rol.EDITOR).accessToken();
        admin = tokens.issue(owner.getId(), Rol.ADMIN).accessToken();
    }

    @BeforeEach
    void setup() {
        jdbc.update("delete from destacados");
        jdbc.update("delete from meta_posts");
        jdbc.update("delete from noticias");
        objects.clear();
        when(graph.recientes()).thenReturn(List.of(media("100", "Primero", TipoMedia.IMAGE), media("200", "Segundo", TipoMedia.VIDEO)));
        when(graph.obtener(anyString())).thenAnswer(call -> media(call.getArgument(0), "Actualizado", TipoMedia.IMAGE));
        when(downloader.descargar(any())).thenReturn(new MetaPreviewDownloader.Preview("image/png", PNG));
        when(storage.upload(anyString(), anyString(), anyLong(), any(InputStream.class))).thenAnswer(call -> {
            String key = "meta/" + UUID.randomUUID() + ".png";
            objects.put(key, call.<InputStream>getArgument(3).readAllBytes());
            return new StoredFile(key, "image/png", (long) PNG.length);
        });
        when(storage.resolvePublicUrl(anyString())).thenAnswer(call -> URI.create("https://cdn.example.invalid/" + call.getArgument(0)));
        doAnswer(call -> { objects.remove(call.<String>getArgument(0)); return null; }).when(storage).delete(anyString());
        when(storage.listObjects()).thenAnswer(call -> objects.entrySet().stream()
                .map(entry -> new StorageObject(entry.getKey(), entry.getValue().length)).toList());
    }

    @Test
    void syncUpsertsHiddenPostsAndDoesNotDownloadUntilExplicitPublication() throws Exception {
        sync(); sync();
        assertThat(jdbc.queryForObject("select count(*) from meta_posts", Integer.class)).isEqualTo(2);
        mvc.perform(auth(get("/api/admin/meta/posts"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content[0].visible").value(false));
        mvc.perform(get("/api/meta/posts")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        verifyNoInteractions(downloader, storage);
    }

    @Test
    void publishingCopiesPreviewOnceAndPublicDtoDoesNotLeakStorageKeys() throws Exception {
        sync(); visibility("100", true); visibility("100", true);
        mvc.perform(get("/api/meta/posts")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value("100")).andExpect(jsonPath("$.content[0].imagenUrl").isNotEmpty())
                .andExpect(jsonPath("$.content[0].objectKey").doesNotExist()).andExpect(jsonPath("$.content[0].visible").doesNotExist());
        verify(storage, times(1)).upload(eq("meta"), eq("image/png"), eq(8L), any(InputStream.class));
        verify(graph, times(1)).obtener("100");
    }

    @Test
    void syncPreservesVisibilityStorageAndFeaturedOrder() throws Exception {
        sync(); visibility("100", true); select("100");
        String key = key("100");
        when(graph.recientes()).thenReturn(List.of(media("100", "Texto nuevo", TipoMedia.IMAGE)));
        sync();
        assertThat(key("100")).isEqualTo(key);
        mvc.perform(get("/api/destacados")).andExpect(status().isOk()).andExpect(jsonPath("$[0].metaPostId").value("100"))
                .andExpect(jsonPath("$[0].tipo").value("META")).andExpect(jsonPath("$[0].resumen").value("Texto nuevo"));
        assertThat(jdbc.queryForObject("select count(*) from meta_posts", Integer.class)).isEqualTo(2);
        visibility("100", false); sync();
        mvc.perform(get("/api/meta/posts")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/destacados")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(auth(get("/api/admin/destacados"))).andExpect(jsonPath("$[0].disponible").value(false));
    }

    @Test
    void purgeRequiresHiddenAndUnselectedThenIsIdempotentAndSyncDoesNotRecopy() throws Exception {
        sync(); visibility("100", true); select("100");
        mvc.perform(auth(delete("/api/admin/meta/posts/100/storage"))).andExpect(status().isConflict());
        visibility("100", false);
        mvc.perform(auth(delete("/api/admin/meta/posts/100/storage"))).andExpect(status().isConflict());
        mvc.perform(auth(put("/api/admin/destacados")).contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isOk());
        String key = key("100");
        mvc.perform(auth(delete("/api/admin/meta/posts/100/storage"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.liberado").value(true)).andExpect(jsonPath("$.objectKey").value(key));
        mvc.perform(auth(delete("/api/admin/meta/posts/100/storage"))).andExpect(status().isOk());
        sync();
        assertThat(key("100")).isNull(); assertThat(objects).isEmpty();
        verify(storage, times(1)).delete(key);
        visibility("100", true);
        assertThat(key("100")).isNotEqualTo(key).isNotNull();
    }

    @Test
    void failedPurgeKeepsReferenceAndNeverReportsSuccess() throws Exception {
        sync(); visibility("100", true); visibility("100", false);
        String key = key("100");
        doThrow(new StorageException(StorageException.Reason.PROVIDER_FAILURE)).when(storage).delete(key);
        mvc.perform(auth(delete("/api/admin/meta/posts/100/storage"))).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502)).andExpect(jsonPath("$.liberado").doesNotExist());
        assertThat(key("100")).isEqualTo(key);
    }

    @Test
    void missingRemoteObjectCanBePurgedAgainAfterDatabaseFailure() throws Exception {
        sync(); visibility("100", true); visibility("100", false);
        String key = key("100");
        doThrow(new StorageException(StorageException.Reason.NOT_FOUND)).when(storage).delete(key);
        mvc.perform(auth(delete("/api/admin/meta/posts/100/storage"))).andExpect(status().isOk());
        assertThat(key("100")).isNull();
    }

    @Test
    void failedDownloadOrInvalidSignatureLeavesPostHiddenWithoutFiles() throws Exception {
        sync();
        when(downloader.descargar(any())).thenReturn(new MetaPreviewDownloader.Preview("image/png", new byte[]{1, 2, 3}));
        mvc.perform(auth(put("/api/admin/meta/posts/100/visibilidad")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"visible\":true}")).andExpect(status().isBadRequest());
        assertThat(key("100")).isNull(); assertThat(objects).isEmpty();
        assertThat(jdbc.queryForObject("select visible from meta_posts where id='100'", Boolean.class)).isFalse();
    }

    @Test
    void sqlRollbackCompensatesUploadedPreview() throws Exception {
        sync();
        tx.executeWithoutResult(status -> { service.visibilidad("100", true); status.setRollbackOnly(); });
        assertThat(key("100")).isNull(); assertThat(objects).isEmpty();
        verify(storage).delete(anyString());
    }

    @Test
    void unavailableMetaReturnsSanitized503AndKeepsExistingData() throws Exception {
        sync();
        when(graph.recientes()).thenThrow(new MetaException(MetaException.Reason.TOKEN_INVALID));
        mvc.perform(auth(post("/api/admin/meta/sync"))).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503)).andExpect(jsonPath("$.path").value("/api/admin/meta/sync"));
        assertThat(jdbc.queryForObject("select count(*) from meta_posts", Integer.class)).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"visible\":null}", "{\"visible\":true,\"objectKey\":\"otro\"}"})
    void visibilityValidatesRequest(String json) throws Exception {
        mvc.perform(auth(put("/api/admin/meta/posts/100/visibilidad")).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void hiddenMissingDuplicateAndAmbiguousFeaturedReferencesAreRejected() throws Exception {
        sync();
        featured("{\"items\":[{\"metaPostId\":\"100\"}]}", 409);
        featured("{\"items\":[{\"metaPostId\":\"999\"}]}", 404);
        visibility("100", true); select("100");
        featured("{\"items\":[{\"metaPostId\":\"100\"},{\"metaPostId\":\"100\"}]}", 409);
        featured("{\"items\":[{\"metaPostId\":\"100\",\"noticiaId\":\"" + UUID.randomUUID() + "\"}]}", 400);
        featured("{\"items\":[{\"metaPostId\":\"ig:100\"}]}", 400);
        mvc.perform(get("/api/destacados")).andExpect(jsonPath("$[0].metaPostId").value("100"));
    }

    @Test
    void mixedCarouselSharesGlobalSixItemLimit() throws Exception {
        var items = new ArrayList<Map<String, String>>();
        for (int i = 1; i <= 6; i++) {
            when(graph.recientes()).thenReturn(List.of(media("" + i, "Post " + i, TipoMedia.IMAGE)));
            sync(); visibility("" + i, true); items.add(Map.of("metaPostId", "" + i));
        }
        featured(mapper.writeValueAsString(Map.of("items", items)), 200);
        items.add(Map.of("metaPostId", "1"));
        featured(mapper.writeValueAsString(Map.of("items", items)), 400);
        var news = mvc.perform(auth(post("/api/admin/noticias")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"titulo\":\"Noticia\",\"resumen\":\"Resumen\",\"contenido\":\"Contenido\"}"))
                .andExpect(status().isCreated()).andReturn();
        String id = mapper.readTree(news.getResponse().getContentAsString()).path("id").asText();
        mvc.perform(auth(put("/api/admin/noticias/" + id + "/estado")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"PUBLICADA\"}")).andExpect(status().isOk());
        featured("{\"items\":[{\"metaPostId\":\"1\"},{\"noticiaId\":\"" + id + "\"}]}", 200);
        mvc.perform(get("/api/destacados")).andExpect(jsonPath("$[0].tipo").value("META"))
                .andExpect(jsonPath("$[1].tipo").value("NOTICIA")).andExpect(jsonPath("$[1].noticiaId").value(id));
    }

    @Test
    void concurrentPublicationDoesNotUploadTwice() throws Exception {
        sync();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(1);
            var a = executor.submit(() -> { ready.await(); return service.visibilidad("100", true); });
            var b = executor.submit(() -> { ready.await(); return service.visibilidad("100", true); });
            ready.countDown();
            assertThat(a.get(15, TimeUnit.SECONDS).objectKey()).isEqualTo(b.get(15, TimeUnit.SECONDS).objectKey());
        }
        verify(storage, times(1)).upload(anyString(), anyString(), anyLong(), any(InputStream.class));
    }

    @Test
    void concurrentPurgeAndPublicationNeverLeaveVisiblePostWithoutCopy() throws Exception {
        sync(); visibility("100", true); visibility("100", false);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> service.visibilidad("100", true));
            var b = executor.submit(() -> {
                try { service.liberarStorage("100"); }
                catch (ar.edu.ifts2.shared.error.BusinessConflictException expected) { }
            });
            a.get(15, TimeUnit.SECONDS); b.get(15, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("select visible from meta_posts where id='100'", Boolean.class)).isTrue();
        assertThat(objects).containsKey(key("100"));
    }

    @Test
    void concurrentSyncDoesNotDuplicatePosts() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(service::sincronizar);
            var b = executor.submit(service::sincronizar);
            a.get(15, TimeUnit.SECONDS); b.get(15, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("select count(*) from meta_posts", Integer.class)).isEqualTo(2);
    }

    @Test
    void adminSecurityAndPublicAccess() throws Exception {
        for (var request : List.of(post("/api/admin/meta/sync"), get("/api/admin/meta/posts"), get("/api/admin/storage/usage"),
                delete("/api/admin/meta/posts/100/storage"), put("/api/admin/meta/posts/100/visibilidad")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"visible\":true}"))) {
            mvc.perform(request).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/admin/meta/sync").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OTHER"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/meta/sync").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/admin/usuarios"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/meta/posts")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:5173", "http://127.0.0.1:5173", "http://localhost:4173"})
    void localCorsAllowsAuthenticatedRequestsAndLoginPreflight(String origin) throws Exception {
        for (String route : List.of("/api/auth/login", "/api/admin/meta/sync")) {
            mvc.perform(options(route).header(HttpHeaders.ORIGIN, origin)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                    .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
        }
        mvc.perform(get("/api/admin/meta/posts").header(HttpHeaders.ORIGIN, origin)).andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
    }

    @Test
    void unapprovedOriginAndHeadersAreRejected() throws Exception {
        mvc.perform(options("/api/auth/login").header(HttpHeaders.ORIGIN, "https://untrusted.vercel.app")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")).andExpect(status().isForbidden());
        mvc.perform(options("/api/auth/login").header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "x-unapproved")).andExpect(status().isForbidden());
    }

    @Test
    void usageMeasuresStoredBytesAndDoesNotInventQuota() throws Exception {
        sync(); visibility("100", true);
        mvc.perform(auth(get("/api/admin/storage/usage"))).andExpect(status().isOk()).andExpect(jsonPath("$.usedBytes").value(8))
                .andExpect(jsonPath("$.scope").value("BUCKET")).andExpect(jsonPath("$.limitBytes").isEmpty())
                .andExpect(jsonPath("$.breakdown[0].bytes").value(8)).andExpect(jsonPath("$.breakdown[0].files").value(1));
    }

    @Test
    void validatesPaginationAndMissingPost() throws Exception {
        mvc.perform(get("/api/meta/posts").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/admin/meta/posts").param("page", "-1"))).andExpect(status().isBadRequest());
        mvc.perform(auth(delete("/api/admin/meta/posts/999/storage"))).andExpect(status().isNotFound());
        mvc.perform(auth(delete("/api/admin/meta/posts/not-an-id/storage"))).andExpect(status().isBadRequest());
    }

    @Test
    void migrationV6PreservesExistingFeaturedReferences() {
        String schema = "meta_upgrade";
        jdbc.execute("create schema " + schema);
        try {
            Flyway.configure().dataSource(jdbc.getDataSource()).schemas(schema).defaultSchema(schema).target("5").load().migrate();
            UUID id = UUID.randomUUID();
            jdbc.update("insert into " + schema + ".noticias (id,titulo,resumen,contenido,estado,created_at,updated_at,fecha) values (?, 'Titulo','Resumen','Contenido','BORRADOR',now(),now(),current_date)", id);
            jdbc.update("insert into " + schema + ".destacados (posicion,noticia_id) values (1,?)", id);
            var flyway = Flyway.configure().dataSource(jdbc.getDataSource()).schemas(schema).defaultSchema(schema).load();
            flyway.migrate();
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("6");
            assertThat(jdbc.queryForObject("select noticia_id from " + schema + ".destacados where posicion=1", UUID.class)).isEqualTo(id);
            assertThatThrownBy(() -> jdbc.update("insert into " + schema + ".destacados (posicion) values (2)"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        } finally { jdbc.execute("drop schema " + schema + " cascade"); }
    }

    @Test
    void openApiDocumentsMetaAndUsage() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/admin/meta/posts/{id}/visibilidad'].put").exists())
                .andExpect(jsonPath("$.paths['/api/admin/storage/usage'].get").exists())
                .andExpect(jsonPath("$.paths['/api/meta/posts'].get").exists());
    }

    private MetaMedia media(String id, String text, TipoMedia type) {
        return new MetaMedia(id, text, type, Instant.parse("2026-09-20T12:00:00Z"), "https://www.instagram.com/p/" + id + "/",
                URI.create("https://scontent.cdninstagram.com/" + id + ".jpg"));
    }
    private void sync() throws Exception { mvc.perform(auth(post("/api/admin/meta/sync"))).andExpect(status().isOk()); }
    private void visibility(String id, boolean visible) throws Exception {
        mvc.perform(auth(put("/api/admin/meta/posts/" + id + "/visibilidad")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"visible\":" + visible + "}")).andExpect(status().isOk());
    }
    private void select(String id) throws Exception { featured("{\"items\":[{\"metaPostId\":\"" + id + "\"}]}", 200); }
    private void featured(String json, int status) throws Exception {
        mvc.perform(auth(put("/api/admin/destacados")).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().is(status));
    }
    private String key(String id) { return jdbc.queryForObject("select object_key from meta_posts where id=?", String.class, id); }
    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
