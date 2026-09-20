package ar.edu.ifts2.shared.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Hidden;

@Hidden
@RestController
public class ApiErrorController implements ErrorController {
    @RequestMapping("/error")
    public ResponseEntity<ApiError> error(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = code instanceof Integer value ? HttpStatus.resolve(value) : null;
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        Object originalPath = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String path = originalPath instanceof String value ? value : request.getRequestURI();
        String message = status.is5xxServerError() ? "Error interno del servidor" : "Solicitud no valida";
        return ResponseEntity.status(status).body(ApiError.of(status, message, path));
    }
}
