package com.ecommerce.users.service;

import com.ecommerce.users.dto.RegisterRequest;
import com.ecommerce.users.exception.DuplicateEmailException;
import com.ecommerce.users.messaging.UserEventPublisher;
import com.ecommerce.users.model.User;
import com.ecommerce.users.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas del registro de cuentas: normaliza el email, asigna rol por defecto,
 * rechaza duplicados y publica user.registered.
 */
class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserEventPublisher publisher = mock(UserEventPublisher.class);

    private final UserService service = new UserService(userRepository, publisher);

    @Test
    void registraCuentaNuevaConRolPorDefecto() {
        when(userRepository.findByEmail("cliente@tuki.cl")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User created = service.register(new RegisterRequest("Cliente@Tuki.cl", "Cliente", null));

        assertThat(created.getEmail()).isEqualTo("cliente@tuki.cl");
        assertThat(created.getRole()).isEqualTo("customer");
        verify(publisher).publishUserRegistered(any());
    }

    @Test
    void rechazaEmailDuplicado() {
        when(userRepository.findByEmail("cliente@tuki.cl"))
                .thenReturn(Optional.of(new User("cliente@tuki.cl", "Cliente", "customer")));

        assertThatThrownBy(() -> service.register(
                new RegisterRequest("cliente@tuki.cl", "Otro", "customer")))
                .isInstanceOf(DuplicateEmailException.class);

        verify(userRepository, never()).save(any());
        verify(publisher, never()).publishUserRegistered(any());
    }
}
