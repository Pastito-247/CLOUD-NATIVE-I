package com.ecommerce.users.event;

import java.time.LocalDateTime;

/**
 * Evento {@code user.registered} publicado por users-service al registrar una
 * cuenta nueva desde la pagina. Se consume de forma asincrona para generar el
 * correo de bienvenida sin bloquear la respuesta HTTP.
 */
public record UserRegisteredEvent(
        Long userId,
        String email,
        String name,
        String role,
        LocalDateTime registeredAt
) {
}
