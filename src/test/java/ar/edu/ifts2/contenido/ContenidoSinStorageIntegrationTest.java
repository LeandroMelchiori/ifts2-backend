package ar.edu.ifts2.contenido;

import ar.edu.ifts2.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ContenidoSinStorageIntegrationTest extends PostgresIntegrationTest {
    private final MockMvc mvc;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;

    @Autowired
    ContenidoSinStorageIntegrationTest(MockMvc mvc, ObjectMapper mapper, JdbcTemplate jdbc) {
        this.mvc = mvc;
        this.mapper = mapper;
        this.jdbc = jdbc;
    }

    static Stream<Arguments> contentWithFiles() {
        return Stream.of(
                Arguments.of("eventos", "portada", "{\"titulo\":\"Evento\",\"resumen\":\"Resumen\",\"descripcion\":\"Descripcion\",\"fechaInicio\":\"2030-01-01T12:00:00Z\",\"fechaFin\":\"2030-01-01T13:00:00Z\",\"lugar\":\"Auditorio\"}"),
                Arguments.of("carreras", "imagen", "{\"nombre\":\"Carrera\",\"tituloOtorgado\":\"Titulo\",\"descripcion\":\"Descripcion\",\"duracion\":\"3 anos\",\"modalidad\":\"Presencial\",\"orden\":0}"),
                Arguments.of("autoridades", "foto", "{\"nombre\":\"Nombre\",\"apellido\":\"Apellido\",\"cargo\":\"Rectoria\",\"orden\":0}"),
                Arguments.of("documentos", "archivo", "{\"titulo\":\"Documento\",\"tipo\":\"OTRO\"}")
        );
    }

    @ParameterizedTest
    @MethodSource("contentWithFiles")
    void contentRemainsAvailableAndFileOperationsFailClearlyWithoutProvider(String module, String attachment, String json) throws Exception {
        var result = mvc.perform(post("/api/admin/" + module).with(user("editor").roles("EDITOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isCreated()).andReturn();
        String id = mapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
        String base = "/api/admin/" + module + "/" + id;
        mvc.perform(multipart(HttpMethod.PUT, base + "/" + attachment)
                        .file(new MockMultipartFile("file", "test", "image/png", new byte[]{1})).with(user("editor").roles("EDITOR")))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value(503));
        mvc.perform(delete(base + "/" + attachment).with(user("editor").roles("EDITOR"))).andExpect(status().isNoContent());
        String key = module + "/" + UUID.randomUUID() + (module.equals("documentos") ? ".pdf" : ".png");
        jdbc.update("update " + module + " set " + attachment + "_object_key = ? where id = ?", key, UUID.fromString(id));
        mvc.perform(put(base + "/estado").with(user("editor").roles("EDITOR")).contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"PUBLICADA\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/" + module + "/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$." + attachment + "Url").isEmpty());
        mvc.perform(get("/api/" + module)).andExpect(status().isOk());
        mvc.perform(put(base + "/estado").with(user("editor").roles("EDITOR")).contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"BORRADOR\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete(base + "/" + attachment).with(user("editor").roles("EDITOR")))
                .andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("select " + attachment + "_object_key from " + module + " where id=?", String.class, UUID.fromString(id))).isEqualTo(key);
    }
}
