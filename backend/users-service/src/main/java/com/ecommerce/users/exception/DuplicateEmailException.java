package com.ecommerce.users.exception;

/**
 * Se lanza al intentar registrar una cuenta con un email ya existente.
 */
public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String email) {
        super("Ya existe una cuenta registrada con el email: " + email);
    }
}
