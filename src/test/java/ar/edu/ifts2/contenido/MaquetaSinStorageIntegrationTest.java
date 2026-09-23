package ar.edu.ifts2.contenido;

import ar.edu.ifts2.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MaquetaSinStorageIntegrationTest extends PostgresIntegrationTest {
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private final MockMvc mvc;
    private final JdbcTemplate jdbc;

    @Autowired
    MaquetaSinStorageIntegrationTest(MockMvc mvc, JdbcTemplate jdbc) {
        this.mvc = mvc;
        this.jdbc = jdbc;
    }

    @Test
    void galleryAndHighlightsCanBeReadWithoutProviderButFilesCannotBeChanged() throws Exception {
        UUID event = UUID.randomUUID();
        UUID photo = UUID.randomUUID();
        String key = "eventos/" + UUID.randomUUID() + ".png";
        jdbc.update("""
                insert into eventos(id,titulo,resumen,descripcion,fecha_inicio,fecha_fin,lugar,estado,publicada_at,portada_object_key)
                values(?,?,?,?,'2030-01-01T12:00:00Z','2030-01-01T13:00:00Z',?,'PUBLICADA',now(),?)
                """, event, "Evento", "Resumen", "Descripcion", "Instituto", "eventos/" + UUID.randomUUID() + ".png");
        jdbc.update("insert into evento_fotos(id,evento_id,object_key,etiqueta,orden,created_at,updated_at) values(?,?,?,'Foto',0,now(),now())",
                photo, event, key);
        jdbc.update("insert into destacados(posicion,evento_id) values(1,?)", event);
        mvc.perform(get("/api/galeria")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].imagenUrl").isEmpty())
                .andExpect(jsonPath("$.content[0].objectKey").doesNotExist());
        mvc.perform(get("/api/destacados")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].portadaUrl").isEmpty());
        String path = "/api/admin/eventos/" + event + "/fotos";
        mvc.perform(multipart(path).file(new MockMultipartFile("file", "x.png", "image/png", PNG))
                .file(new MockMultipartFile("datos", "", "application/json", "{\"etiqueta\":\"Nueva\",\"orden\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .with(user("editor").roles("EDITOR"))).andExpect(status().isServiceUnavailable());
        mvc.perform(multipart(HttpMethod.PUT, path + "/" + photo + "/archivo")
                .file(new MockMultipartFile("file", "x.png", "image/png", PNG)).with(user("editor").roles("EDITOR")))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(delete(path + "/" + photo).with(user("editor").roles("EDITOR"))).andExpect(status().isServiceUnavailable());
        mvc.perform(put(path + "/" + photo).with(user("editor").roles("EDITOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"etiqueta\":\"Editada\",\"orden\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.objectKey").value(key))
                .andExpect(jsonPath("$.etiqueta").value("Editada"));
        mvc.perform(get(path).with(user("editor").roles("EDITOR"))).andExpect(jsonPath("$.length()").value(1));
    }
}
