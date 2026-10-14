package com.ecommerce.users.controller;

import com.ecommerce.users.dto.RegisterRequest;
import com.ecommerce.users.model.User;
import com.ecommerce.users.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API publica de usuarios.
 *
 * Expone el registro de cuentas desde la pagina. Es publico (no requiere JWT)
 * porque el usuario aun no ha iniciado sesion; por eso vive bajo {@code /api/public}.
 */
@RestController
@RequestMapping("/api/public/users")
public class PublicUserController {

    private final UserService userService;

    public PublicUserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<User> register(@Valid @RequestBody RegisterRequest request) {
        User created = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
