package com.ecommerce.users.service;

import com.ecommerce.users.dto.RegisterRequest;
import com.ecommerce.users.exception.DuplicateEmailException;
import com.ecommerce.users.exception.InvalidCredentialsException;
import com.ecommerce.users.messaging.UserEventPublisher;
import com.ecommerce.users.model.User;
import com.ecommerce.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas del registro y login local: guarda la contrasena hasheada, publica
 * user.registered y valida credenciales.
 */
class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserEventPublisher publisher = mock(UserEventPublisher.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private final UserService service =
            new UserService(userRepository, publisher, passwordEncoder);

    @Test
    void registraCuentaNuevaConRolPorDefectoYContrasenaHasheada() {
        when(userRepository.findByEmail("cliente@tuki.cl")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User created = service.register(
                new RegisterRequest("Cliente@Tuki.cl", "Cliente", "secreta123", null));

        assertThat(created.getEmail()).isEqualTo("cliente@tuki.cl");
        assertThat(created.getRole()).isEqualTo("customer");
        assertThat(created.getPasswordHash()).isNotNull();
        assertThat(created.getPasswordHash()).isNotEqualTo("secreta123");
        assertThat(passwordEncoder.matches("secreta123", created.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("incorrecta", created.getPasswordHash())).isFalse();
        verify(publisher).publishUserRegistered(any());
    }

    @Test
    void rechazaEmailDuplicado() {
        when(userRepository.findByEmail("cliente@tuki.cl"))
                .thenReturn(Optional.of(new User("cliente@tuki.cl", "Cliente", "customer")));

        assertThatThrownBy(() -> service.register(
                new RegisterRequest("cliente@tuki.cl", "Otro", "secreta123", "customer")))
                .isInstanceOf(DuplicateEmailException.class);

        verify(userRepository, never()).save(any());
        verify(publisher, never()).publishUserRegistered(any());
    }

    @Test
    void autenticaConCredencialesCorrectas() {
        User user = new User("cliente@tuki.cl", "Cliente", "customer");
        user.setPasswordHash(passwordEncoder.encode("secreta123"));
        when(userRepository.findByEmail("cliente@tuki.cl")).thenReturn(Optional.of(user));

        User authenticated = service.authenticate("Cliente@Tuki.cl", "secreta123");

        assertThat(authenticated.getEmail()).isEqualTo("cliente@tuki.cl");
    }

    @Test
    void rechazaContrasenaIncorrecta() {
        User user = new User("cliente@tuki.cl", "Cliente", "customer");
        user.setPasswordHash(passwordEncoder.encode("secreta123"));
        when(userRepository.findByEmail("cliente@tuki.cl")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.authenticate("cliente@tuki.cl", "incorrecta"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void rechazaEmailInexistente() {
        when(userRepository.findByEmail("nadie@tuki.cl")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate("nadie@tuki.cl", "secreta123"))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
