package ar.edu.ifts2.usuario.service;

import ar.edu.ifts2.usuario.entity.Rol;
import ar.edu.ifts2.usuario.entity.Usuario;
import ar.edu.ifts2.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BootstrapAdminInitializerTest {
    @Mock
    private UsuarioRepository repository;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    @Test
    void createsInitialAdminWithBcrypt() {
        String password = UUID.randomUUID().toString();
        initializer(password).run(new DefaultApplicationArguments());
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(repository).save(captor.capture());
        Usuario user = captor.getValue();
        assertThat(user.getRol()).isEqualTo(Rol.ADMIN);
        assertThat(user.getEmail()).isEqualTo("admin@example.test");
        assertThat(user.isActivo()).isTrue();
        assertThat(user.getPasswordHash()).isNotEqualTo(password);
        assertThat(encoder.matches(password, user.getPasswordHash())).isTrue();
    }

    @Test
    void neverOverwritesExistingUsers() {
        when(repository.count()).thenReturn(1L);
        initializer(null).run(new DefaultApplicationArguments());
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsMissingPassword() {
        assertThatThrownBy(() -> initializer(null).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsShortPassword() {
        assertThatThrownBy(() -> initializer(UUID.randomUUID().toString().substring(0, 8))
                .run(new DefaultApplicationArguments())).isInstanceOf(IllegalStateException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsPasswordsOverBcryptByteLimit() {
        assertThatThrownBy(() -> initializer("\u00e9".repeat(37)).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class);
        verify(repository, never()).save(any());
    }

    private BootstrapAdminInitializer initializer(String password) {
        return new BootstrapAdminInitializer(repository, encoder,
                new BootstrapAdminProperties("Admin", "Prueba", "ADMIN@EXAMPLE.TEST", password));
    }
}
