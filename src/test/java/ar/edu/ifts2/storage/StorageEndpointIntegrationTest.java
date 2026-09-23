package ar.edu.ifts2.storage;

import ar.edu.ifts2.storage.model.StoredFile;
import ar.edu.ifts2.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.io.InputStream;
import java.net.URI;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties = {
        "app.storage.provider=supabase", "app.storage.test-endpoints-enabled=true",
        "app.storage.supabase.url=https://storage.example.invalid", "app.storage.supabase.bucket=tests"
})
class StorageEndpointIntegrationTest extends PostgresIntegrationTest {
    @MockitoBean
    private StorageService storage;
    private final MockMvc mvc;
    private final ObjectMapper mapper;
    private final String key = "pruebas/" + UUID.randomUUID() + ".png";
    private final URI url = URI.create("https://cdn.example.invalid/" + key);

    @Autowired
    StorageEndpointIntegrationTest(MockMvc mvc, ObjectMapper mapper) {
        this.mvc = mvc;
        this.mapper = mapper;
    }

    @BeforeEach
    void responses() {
        when(storage.upload(eq("pruebas"), eq("image/png"), eq((long) FileValidatorTest.PNG.length), any(InputStream.class)))
                .thenReturn(new StoredFile(key, "image/png", FileValidatorTest.PNG.length));
        when(storage.resolvePublicUrl(key)).thenReturn(url);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "EDITOR"})
    void bothRolesCanUploadResolveAndDelete(String role) throws Exception {
        mvc.perform(multipart("/api/admin/storage/test").file(file()).with(user("test").roles(role)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.objectKey").value(key))
                .andExpect(jsonPath("$.publicUrl").value(url.toString()))
                .andExpect(jsonPath("$.serviceRoleKey").doesNotExist());
        mvc.perform(get("/api/admin/storage/test/url").param("objectKey", key).with(user("test").roles(role)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.publicUrl").value(url.toString()));
        mvc.perform(delete("/api/admin/storage/test").param("objectKey", key).with(user("test").roles(role)))
                .andExpect(status().isNoContent());
        verify(storage).delete(key);
    }

    @Test
    void anonymousCannotUploadOrDelete() throws Exception {
        mvc.perform(multipart("/api/admin/storage/test").file(file())).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/admin/storage/test").param("objectKey", key)).andExpect(status().isUnauthorized());
        verify(storage, never()).upload(any(), any(), anyLong(), any());
        verify(storage, never()).delete(any());
    }

    @Test
    void otherRolesAreForbidden() throws Exception {
        mvc.perform(multipart("/api/admin/storage/test").file(file()).with(user("visitor").roles("VISITOR")))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"../../private", "noticias/00000000-0000-0000-0000-000000000000.png", "pruebas/../file.pdf"})
    void probeCannotDeleteOutsideItsNamespace(String objectKey) throws Exception {
        mvc.perform(delete("/api/admin/storage/test").param("objectKey", objectKey).with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        verify(storage, never()).delete(any());
    }

    @ParameterizedTest
    @EnumSource(StorageException.Reason.class)
    void storageFailuresUseCommonErrorFormat(StorageException.Reason reason) throws Exception {
        when(storage.upload(any(), any(), anyLong(), any())).thenThrow(new StorageException(reason));
        int expected = switch (reason) {
            case INVALID_FILE -> 400;
            case TOO_LARGE -> 413;
            case UNSUPPORTED_TYPE -> 415;
            case NOT_FOUND -> 404;
            case PROVIDER_FAILURE -> 502;
        };
        mvc.perform(multipart("/api/admin/storage/test").file(file()).with(user("editor").roles("EDITOR")))
                .andExpect(status().is(expected)).andExpect(jsonPath("$.status").value(expected))
                .andExpect(jsonPath("$.timestamp").exists()).andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.path").value("/api/admin/storage/test"));
    }

    @Test
    void missingFileIsBadRequest() throws Exception {
        mvc.perform(multipart("/api/admin/storage/test").with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void swaggerDescribesMultipartBearerAndResponses() throws Exception {
        var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var spec = mapper.readTree(result.getResponse().getContentAsString());
        var operation = spec.at("/paths/~1api~1admin~1storage~1test/post");
        assertThat(operation.path("summary").asText()).contains("TEMPORAL");
        assertThat(operation.at("/security/0/bearerAuth").isArray()).isTrue();
        assertThat(operation.at("/requestBody/content/multipart~1form-data").isMissingNode()).isFalse();
        assertThat(operation.path("responses").has("413")).isTrue();
        assertThat(operation.path("responses").has("502")).isTrue();
        assertThat(spec.toString()).doesNotContain("serviceRoleKey", "SUPABASE_SERVICE_ROLE_KEY");
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "../../untrusted-name.png", MediaType.IMAGE_PNG_VALUE, FileValidatorTest.PNG);
    }
}
