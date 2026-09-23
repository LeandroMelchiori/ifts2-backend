package ar.edu.ifts2.noticia;

import ar.edu.ifts2.noticia.dto.NoticiaRequest;
import ar.edu.ifts2.noticia.entity.EstadoNoticia;
import ar.edu.ifts2.noticia.entity.Noticia;
import ar.edu.ifts2.noticia.repository.NoticiaRepository;
import ar.edu.ifts2.noticia.service.NoticiaPortadaService;
import ar.edu.ifts2.noticia.service.NoticiaService;
import ar.edu.ifts2.security.JwtService;
import ar.edu.ifts2.storage.StorageException;
import ar.edu.ifts2.storage.StorageService;
import ar.edu.ifts2.storage.model.StoredFile;
import ar.edu.ifts2.support.PostgresIntegrationTest;
import ar.edu.ifts2.usuario.entity.Rol;
import ar.edu.ifts2.usuario.entity.Usuario;
import ar.edu.ifts2.usuario.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = "app.storage.max-image-size=1KB")
class NoticiaPortadaIntegrationTest extends PostgresIntegrationTest {
    private static final String PATH = "/api/admin/noticias/{id}/portada";
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aXioAAAAASUVORK5CYII=");
    @MockitoBean
    private StorageService storage;
    private final MockMvc mvc;
    private final ObjectMapper mapper;
    private final NoticiaRepository repository;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final NoticiaPortadaService portadas;
    private final NoticiaService noticias;
    private final TransactionTemplate transaction;
    private final JdbcTemplate jdbc;
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private String adminToken;
    private String editorToken;
    private String inactiveToken;

    @Autowired
    NoticiaPortadaIntegrationTest(MockMvc mvc, ObjectMapper mapper, NoticiaRepository repository,
                                  UsuarioRepository usuarios, PasswordEncoder encoder, JwtService jwtService,
                                  NoticiaPortadaService portadas, NoticiaService noticias,
                                  PlatformTransactionManager transactions, JdbcTemplate jdbc) {
        this.mvc = mvc;
        this.mapper = mapper;
        this.repository = repository;
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.jwtService = jwtService;
        this.portadas = portadas;
        this.noticias = noticias;
        this.transaction = new TransactionTemplate(transactions);
        this.jdbc = jdbc;
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
    void setupStorage() {
        repository.deleteAllInBatch();
        objects.clear();
        when(storage.upload(anyString(), anyString(), anyLong(), any(InputStream.class))).thenAnswer(invocation -> {
            String extension = switch (invocation.<String>getArgument(1)) {
                case "image/jpeg" -> "jpg";
                case "image/webp" -> "webp";
                default -> "png";
            };
            String key = invocation.<String>getArgument(0) + "/" + UUID.randomUUID() + "." + extension;
            objects.put(key, invocation.<InputStream>getArgument(3).readAllBytes());
            return new StoredFile(key, invocation.getArgument(1), invocation.getArgument(2));
        });
        when(storage.resolvePublicUrl(anyString())).thenAnswer(invocation -> URI.create("https://cdn.example.invalid/" + invocation.getArgument(0)));
        doAnswer(invocation -> { objects.remove(invocation.<String>getArgument(0)); return null; }).when(storage).delete(anyString());
    }

    @ParameterizedTest
    @EnumSource(Rol.class)
    void bothRolesCanUploadReplaceAndRemoveCover(Rol role) throws Exception {
        String token = role == Rol.ADMIN ? adminToken : editorToken;
        UUID id = seed(EstadoNoticia.PUBLICADA);
        var before = repository.findById(id).orElseThrow();
        var response = mvc.perform(multipart(HttpMethod.PUT, PATH, id).file(file("image/png", PNG))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.objectKey").exists())
                .andExpect(jsonPath("$.publicUrl").exists()).andReturn();
        String firstKey = mapper.readTree(response.getResponse().getContentAsString()).get("objectKey").asText();
        assertThat(firstKey).matches("noticias/[0-9a-f-]{36}\\.png");
        assertThat(objects.get(firstKey)).isEqualTo(PNG);
        mvc.perform(multipart(HttpMethod.PUT, PATH, id).file(file("image/png", PNG))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isOk());
        String secondKey = repository.findById(id).orElseThrow().getPortadaObjectKey();
        assertThat(secondKey).isNotEqualTo(firstKey);
        assertThat(objects).containsOnlyKeys(secondKey);
        verify(storage).delete(firstKey);
        var after = repository.findById(id).orElseThrow();
        assertThat(after.getEstado()).isEqualTo(before.getEstado());
        assertThat(after.getPublicadaAt()).isEqualTo(before.getPublicadaAt());
        assertThat(after.getCreatedAt()).isEqualTo(before.getCreatedAt());
        assertThat(after.getUpdatedAt()).isAfter(before.getUpdatedAt());
        mvc.perform(delete(PATH, id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(delete(PATH, id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isNoContent());
        assertThat(repository.findById(id).orElseThrow().getPortadaObjectKey()).isNull();
        verify(storage, times(1)).delete(secondKey);
        assertThat(objects).isEmpty();
    }

    @Test
    void readsResolveUrlsButNeverExposeKeysPublicly() throws Exception {
        UUID id = seed(EstadoNoticia.PUBLICADA);
        String key = upload(id);
        String url = "https://cdn.example.invalid/" + key;
        mvc.perform(get("/api/noticias/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.portadaUrl").value(url)).andExpect(jsonPath("$.portadaObjectKey").doesNotExist());
        mvc.perform(get("/api/noticias")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].portadaUrl").value(url))
                .andExpect(jsonPath("$.content[0].portadaObjectKey").doesNotExist());
        mvc.perform(get("/api/admin/noticias/{id}", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.portadaObjectKey").value(key))
                .andExpect(jsonPath("$.portadaUrl").value(url));
        when(storage.resolvePublicUrl(key)).thenReturn(URI.create("https://new-cdn.example.invalid/" + key));
        mvc.perform(get("/api/noticias/{id}", id)).andExpect(jsonPath("$.portadaUrl").value("https://new-cdn.example.invalid/" + key));
        assertThat(repository.findById(id).orElseThrow().getPortadaObjectKey()).isEqualTo(key);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoNoticia.class, names = {"BORRADOR", "ARCHIVADA"})
    void coverDoesNotExposeUnpublishedNews(EstadoNoticia state) throws Exception {
        UUID id = seed(state);
        upload(id);
        mvc.perform(get("/api/noticias/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/noticias")).andExpect(jsonPath("$.totalElements").value(0));
        assertThat(repository.findById(id).orElseThrow().getEstado()).isEqualTo(state);
    }

    @Test
    void textAndStateChangesPreserveCover() throws Exception {
        UUID id = seed(EstadoNoticia.BORRADOR);
        String key = upload(id);
        noticias.actualizar(id, new NoticiaRequest("Editada", "Resumen", "Contenido"));
        noticias.cambiarEstado(id, EstadoNoticia.PUBLICADA);
        noticias.cambiarEstado(id, EstadoNoticia.ARCHIVADA);
        assertThat(repository.findById(id).orElseThrow().getPortadaObjectKey()).isEqualTo(key);
        verify(storage, never()).delete(anyString());
    }

    @Test
    void writesRequireValidJwtAndAdministrativeRole() throws Exception {
        UUID id = seed(EstadoNoticia.PUBLICADA);
        mvc.perform(multipart(HttpMethod.PUT, PATH, id).file(file("image/png", PNG))).andExpect(status().isUnauthorized());
        mvc.perform(delete(PATH, id)).andExpect(status().isUnauthorized());
        for (String token : Set.of("invalid", inactiveToken)) {
            mvc.perform(multipart(HttpMethod.PUT, PATH, id).file(file("image/png", PNG))
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isUnauthorized());
            mvc.perform(delete(PATH, id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isUnauthorized());
        }
        mvc.perform(multipart(HttpMethod.PUT, PATH, id).file(file("image/png", PNG)).with(user("visitor").roles("VISITOR")))
                .andExpect(status().isForbidden());
        mvc.perform(delete(PATH, id).with(user("visitor").roles("VISITOR"))).andExpect(status().isForbidden());
        verify(storage, never()).upload(any(), any(), anyLong(), any());
        verify(storage, never()).delete(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/pdf", "image/svg+xml", "image/gif", "text/html", "application/octet-stream"})
    void nonImageAndUnsupportedMimeTypesAreRejected(String mime) throws Exception {
        UUID id = seed(EstadoNoticia.BORRADOR);
        mvc.perform(multipart(HttpMethod.PUT, PATH, id).file(file(mime, PNG))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + editorToken))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.status").value(415));
        verify(storage, never()).upload(any(), any(), anyLong(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/jpeg", "image/webp", " IMAGE/PNG "})
    void supportsAllowedImageFormatsAndNormalizesMime(String mime) throws Exception {
        UUID id = seed(EstadoNoticia.BORRADOR);
        byte[] bytes = mime.equals("image/jpeg") ? new byte[]{(byte) 255, (byte) 216, (byte) 255, 0}
                : mime.equals("image/webp") ? "RIFF0000WEBPVP8 ".getBytes(java.nio.charset.StandardCharsets.US_ASCII) : PNG;
        mvc.perform(multipart(HttpMethod.PUT, PATH, id).file(file(mime, bytes))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + editorToken)).andExpect(status().isOk());
        assertThat(objects).hasSize(1);
    }

    @Test
    void rejectsSpoofedEmptyMissingAndOversizedImagesWithoutChangingCurrentCover() throws Exception {
        UUID id = seed(EstadoNoticia.PUBLICADA);
        String oldKey = upload(id);
        for (MockMultipartFile invalid : new MockMultipartFile[]{file("image/jpeg", PNG), file("image/png", new byte[0])}) {
            mvc.perform(multipart(HttpMethod.PUT, PATH, id).file(invalid).header(HttpHeaders.AUTHORIZATION, "Bearer " + editorToken))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(multipart(HttpMethod.PUT, PATH, id).header(HttpHeaders.AUTHORIZATION, "Bearer " + editorToken))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(HttpMethod.PUT, PATH, id).file(file("image/png", java.util.Arrays.copyOf(PNG, 1025)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + editorToken)).andExpect(status().isPayloadTooLarge());
        assertThat(repository.findById(id).orElseThrow().getPortadaObjectKey()).isEqualTo(oldKey);
        assertThat(objects).containsOnlyKeys(oldKey);
        verify(storage, times(1)).upload(any(), any(), anyLong(), any());
        verify(storage, never()).delete(anyString());
    }

    @Test
    void missingNewsAndMalformedUuidDoNotUploadFiles() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(multipart(HttpMethod.PUT, PATH, id).file(file("image/png", PNG))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + editorToken)).andExpect(status().isNotFound());
        mvc.perform(delete(PATH, id).header(HttpHeaders.AUTHORIZATION, "Bearer " + editorToken)).andExpect(status().isNotFound());
        mvc.perform(multipart(HttpMethod.PUT, PATH, "invalid").file(file("image/png", PNG))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + editorToken)).andExpect(status().isBadRequest());
        verify(storage, never()).upload(any(), any(), anyLong(), any());
    }

    @Test
    void uploadFailurePreservesOldCoverAndReturnsSanitizedError() throws Exception {
        UUID id = seed(EstadoNoticia.PUBLICADA);
        String oldKey = upload(id);
        when(storage.upload(any(), any(), anyLong(), any())).thenThrow(new StorageException(StorageException.Reason.PROVIDER_FAILURE));
        mvc.perform(multipart(HttpMethod.PUT, PATH, id).file(file("image/png", PNG))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + editorToken))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.message").value("No se pudo completar la operacion de almacenamiento"));
        assertThat(repository.findById(id).orElseThrow().getPortadaObjectKey()).isEqualTo(oldKey);
        assertThat(objects).containsOnlyKeys(oldKey);
        verify(storage, never()).delete(anyString());
    }

    @Test
    void failureAfterUploadRollsBackReferenceAndRemovesNewObject() throws Exception {
        UUID id = seed(EstadoNoticia.PUBLICADA);
        String oldKey = upload(id);
        when(storage.resolvePublicUrl(anyString())).thenThrow(new StorageException(StorageException.Reason.PROVIDER_FAILURE));
        mvc.perform(multipart(HttpMethod.PUT, PATH, id).file(file("image/png", PNG))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + editorToken)).andExpect(status().isBadGateway());
        assertThat(repository.findById(id).orElseThrow().getPortadaObjectKey()).isEqualTo(oldKey);
        assertThat(objects).containsOnlyKeys(oldKey);
        verify(storage, never()).delete(oldKey);
    }

    @Test
    void commitFailureCompensatesUploadAndKeepsOldObject() {
        UUID id = seed(EstadoNoticia.PUBLICADA);
        String oldKey = upload(id);
        assertThatThrownBy(() -> transaction.execute(status -> {
            var response = portadas.subir(id, "image/png", PNG.length, new ByteArrayInputStream(PNG));
            assertThat(objects).containsKeys(oldKey, response.objectKey());
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) { throw new DataIntegrityViolationException("simulated commit failure"); }
            });
            return response;
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(repository.findById(id).orElseThrow().getPortadaObjectKey()).isEqualTo(oldKey);
        assertThat(objects).containsOnlyKeys(oldKey);
        verify(storage, never()).delete(oldKey);
    }

    @Test
    void rollbackOfRemovalKeepsFileAndReference() {
        UUID id = seed(EstadoNoticia.PUBLICADA);
        String key = upload(id);
        transaction.executeWithoutResult(status -> { portadas.quitar(id); status.setRollbackOnly(); });
        assertThat(repository.findById(id).orElseThrow().getPortadaObjectKey()).isEqualTo(key);
        assertThat(objects).containsOnlyKeys(key);
        verify(storage, never()).delete(anyString());
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void deletionFailureDoesNotUndoSuccessfulReplacementAndLogsOnlySafeDetails(CapturedOutput output) throws Exception {
        UUID id = seed(EstadoNoticia.PUBLICADA);
        String oldKey = upload(id);
        doThrow(new IllegalStateException("provider-secret-should-not-be-logged")).when(storage).delete(oldKey);
        mvc.perform(multipart(HttpMethod.PUT, PATH, id).file(file("image/png", PNG))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + editorToken)).andExpect(status().isOk());
        String currentKey = repository.findById(id).orElseThrow().getPortadaObjectKey();
        assertThat(currentKey).isNotEqualTo(oldKey);
        assertThat(objects).containsKeys(oldKey, currentKey);
        assertThat(output).contains("Limpieza pendiente de portada", oldKey).doesNotContain("provider-secret-should-not-be-logged");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void failedRemovalCleanupStillDetachesAndReportsPendingObject(CapturedOutput output) throws Exception {
        UUID id = seed(EstadoNoticia.PUBLICADA);
        String key = upload(id);
        doThrow(new StorageException(StorageException.Reason.PROVIDER_FAILURE)).when(storage).delete(key);
        mvc.perform(delete(PATH, id).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)).andExpect(status().isNoContent());
        assertThat(repository.findById(id).orElseThrow().getPortadaObjectKey()).isNull();
        assertThat(output).contains("Limpieza pendiente de portada", key);
    }

    @Test
    void alreadyMissingObjectIsSuccessfulRemoval() throws Exception {
        UUID id = seed(EstadoNoticia.PUBLICADA);
        String key = upload(id);
        objects.remove(key);
        doThrow(new StorageException(StorageException.Reason.NOT_FOUND)).when(storage).delete(key);
        mvc.perform(delete(PATH, id).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)).andExpect(status().isNoContent());
        assertThat(repository.findById(id).orElseThrow().getPortadaObjectKey()).isNull();
    }

    @Test
    void simultaneousUploadsLeaveOneReferencedObjectWithoutDeletingWinner() throws Exception {
        UUID id = seed(EstadoNoticia.PUBLICADA);
        upload(id);
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(5, TimeUnit.SECONDS); return upload(id); });
            var second = executor.submit(() -> { start.await(5, TimeUnit.SECONDS); return upload(id); });
            String firstKey = first.get(20, TimeUnit.SECONDS);
            String secondKey = second.get(20, TimeUnit.SECONDS);
            String current = repository.findById(id).orElseThrow().getPortadaObjectKey();
            assertThat(current).isIn(firstKey, secondKey);
            assertThat(objects).containsOnlyKeys(current);
            verify(storage, never()).delete(current);
        }
    }

    @Test
    void simultaneousTextEditDoesNotLoseCover() throws Exception {
        UUID id = seed(EstadoNoticia.PUBLICADA);
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var cover = executor.submit(() -> { start.await(5, TimeUnit.SECONDS); return upload(id); });
            var edit = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return noticias.actualizar(id, new NoticiaRequest("Editada", "Resumen", "Contenido"));
            });
            String key = cover.get(20, TimeUnit.SECONDS);
            edit.get(20, TimeUnit.SECONDS);
            var persisted = repository.findById(id).orElseThrow();
            assertThat(persisted.getPortadaObjectKey()).isEqualTo(key);
            assertThat(persisted.getTitulo()).isEqualTo("Editada");
        }
    }

    @Test
    void databaseRejectsForeignKeysUrlsAndSharedCovers() {
        UUID first = seed(EstadoNoticia.PUBLICADA);
        UUID second = seed(EstadoNoticia.BORRADOR);
        String key = upload(first);
        for (String invalid : Set.of("../../file.png", "https://cdn.example.invalid/image.png", "pruebas/" + UUID.randomUUID() + ".png", key)) {
            assertThatThrownBy(() -> jdbc.update("update noticias set portada_object_key = ? where id = ?", invalid, second))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void swaggerDocumentsMultipartSecurityAndStorageErrors() throws Exception {
        var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var spec = mapper.readTree(result.getResponse().getContentAsString());
        var operation = spec.at("/paths/~1api~1admin~1noticias~1{id}~1portada/put");
        assertThat(operation.at("/security/0/bearerAuth").isArray()).isTrue();
        assertThat(operation.at("/requestBody/content/multipart~1form-data").isMissingNode()).isFalse();
        for (String code : Set.of("200", "400", "401", "403", "404", "413", "415", "502", "503")) {
            assertThat(operation.path("responses").has(code)).as(code).isTrue();
        }
        assertThat(spec.at("/paths/~1api~1admin~1noticias~1{id}~1portada/delete/responses/204").isMissingNode()).isFalse();
        assertThat(spec.at("/components/schemas/NoticiaPublicaResponse/properties/portadaUrl").isMissingNode()).isFalse();
        assertThat(spec.at("/components/schemas/NoticiaPublicaResponse/properties/portadaObjectKey").isMissingNode()).isTrue();
    }

    private UUID seed(EstadoNoticia state) {
        Noticia noticia = new Noticia("Titulo", "Resumen", "Contenido");
        noticia.cambiarEstado(state);
        return repository.saveAndFlush(noticia).getId();
    }

    private String upload(UUID id) {
        return portadas.subir(id, "image/png", PNG.length, new ByteArrayInputStream(PNG)).objectKey();
    }

    private MockMultipartFile file(String mime, byte[] bytes) {
        return new MockMultipartFile("file", "../../untrusted.svg", mime, bytes);
    }
}
