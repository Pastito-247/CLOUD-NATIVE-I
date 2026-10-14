package com.ecommerce.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Solicitud de inicio de sesion con una cuenta local (registrada en /register).
 */
public record LoginRequest(

        @NotBlank(message = "email es obligatorio")
        @Email(message = "email debe ser valido")
        String email,

        @NotBlank(message = "password es obligatoria")
        String password
) {
}
