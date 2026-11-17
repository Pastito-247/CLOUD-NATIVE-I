package com.ecommerce.users.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API publica de usuarios.
 *
 * El registro publico se elimino en el EFT: los clientes se auto-registran en
 * AWS Cognito (siempre con rol customer). Este endpoint se conserva solo para
 * responder 410 y evitar que un visitante cree cuentas con rol arbitrario.
 */
@RestController
@RequestMapping("/api/public/users")
public class PublicUserController {

    @PostMapping("/register")
    public ResponseEntity<Void> register() {
        return ResponseEntity.status(HttpStatus.GONE).build();
    }
}
