package ar.edu.ifts2.shared.error;

import ar.edu.ifts2.auth.controller.AuthController;
import ar.edu.ifts2.auth.dto.LoginRequest;
import ar.edu.ifts2.auth.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    @Test
    void unexpectedErrorsNeverExposeInternalMessages() throws Exception {
        AuthService service = mock(AuthService.class);
        when(service.login(any())).thenThrow(new IllegalStateException("SQL and sensitive internals"));
        var mvc = MockMvcBuilders.standaloneSetup(new AuthController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        String body = new ObjectMapper().writeValueAsString(
                new LoginRequest("admin@example.test", UUID.randomUUID().toString()));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Error interno del servidor"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }
}
