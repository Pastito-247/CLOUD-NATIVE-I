package com.ecommerce.users.dto;

/**
 * Respuesta del login local: contiene el JWT emitido por users-service y los
 * datos basicos del usuario autenticado.
 */
public record AuthResponse(

        String accessToken,
        String tokenType,
        long expiresIn,
        Long userId,
        String email,
        String name,
        String role
) {
}
