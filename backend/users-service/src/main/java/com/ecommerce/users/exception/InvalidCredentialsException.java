package com.ecommerce.users.exception;

/**
 * Credenciales locales invalidas (email inexistente o contrasena incorrecta).
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Email o contrasena incorrectos");
    }
}
