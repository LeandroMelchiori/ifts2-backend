package ar.edu.ifts2.usuario.service;

import ar.edu.ifts2.usuario.entity.Rol;
import ar.edu.ifts2.usuario.entity.Usuario;
import ar.edu.ifts2.usuario.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.ifts2.usuario.validation.PasswordPolicy;

@Component
@ConditionalOnProperty(prefix = "app.bootstrap-admin", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(BootstrapAdminProperties.class)
public class BootstrapAdminInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);
    private final UsuarioRepository repository;
    private final PasswordEncoder encoder;
    private final BootstrapAdminProperties properties;

    public BootstrapAdminInitializer(UsuarioRepository repository, PasswordEncoder encoder,
                                    BootstrapAdminProperties properties) {
        this.repository = repository;
        this.encoder = encoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            log.info("Carga inicial omitida: ya existen usuarios");
            return;
        }
        String password = properties.password();
        // Validacion manual para que un error de binding nunca imprima el valor de la password.
        if (!PasswordPolicy.isValidNewPassword(password)) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD debe tener al menos 12 caracteres y hasta 72 bytes UTF-8");
        }
        repository.save(new Usuario(properties.nombre(), properties.apellido(), properties.email(),
                encoder.encode(password), Rol.ADMIN));
        log.info("Administrador inicial creado; deshabilitar BOOTSTRAP_ADMIN_ENABLED");
    }
}
