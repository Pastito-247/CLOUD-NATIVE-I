package com.ecommerce.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Solicitud de registro de una cuenta desde la pagina.
 *
 * La validacion de parametros de entrada se declara con Bean Validation; el
 * rol es opcional y por defecto se asigna "customer".
 */
public record RegisterRequest(

        @NotBlank(message = "email es obligatorio")
        @Email(message = "email debe ser valido")
        String email,

        @NotBlank(message = "name es obligatorio")
        @Size(min = 2, max = 100, message = "name debe tener entre 2 y 100 caracteres")
        String name,

        @Size(max = 30, message = "role no debe superar 30 caracteres")
        String role
) {
}
