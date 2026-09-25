package ar.edu.ifts2.meta;

import ar.edu.ifts2.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MetaSinIntegracionIntegrationTest extends PostgresIntegrationTest {
    private final MockMvc mvc;
    @Autowired
    MetaSinIntegracionIntegrationTest(MockMvc mvc) { this.mvc = mvc; }

    @Test
    void unconfiguredIntegrationsDoNotPreventStartupAndReport503NotEmptySuccess() throws Exception {
        mvc.perform(post("/api/admin/meta/sync").with(user("editor").roles("EDITOR"))).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/admin/storage/usage").with(user("editor").roles("EDITOR"))).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/admin/meta/posts").with(user("editor").roles("EDITOR"))).andExpect(status().isOk());
        mvc.perform(get("/api/meta/posts")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }
}
