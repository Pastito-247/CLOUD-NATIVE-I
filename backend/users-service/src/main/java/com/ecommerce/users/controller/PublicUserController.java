package com.ecommerce.users.controller;

import com.ecommerce.users.dto.AuthResponse;
import com.ecommerce.users.dto.LoginRequest;
import com.ecommerce.users.dto.RegisterRequest;
import com.ecommerce.users.model.User;
import com.ecommerce.users.service.LocalTokenService;
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
 * Expone el registro de cuentas y el login local (email + contrasena) desde la
 * pagina. Es publico (no requiere JWT) porque el usuario aun no ha iniciado
 * sesion; por eso vive bajo {@code /api/public}.
 */
@RestController
@RequestMapping("/api/public/users")
public class PublicUserController {

    private final UserService userService;
    private final LocalTokenService tokenService;

    public PublicUserController(UserService userService, LocalTokenService tokenService) {
        this.userService = userService;
        this.tokenService = tokenService;
    }

    @PostMapping("/register")
    public ResponseEntity<User> register(@Valid @RequestBody RegisterRequest request) {
        User created = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userService.authenticate(request.email(), request.password());
        String token = tokenService.generateToken(user);
        return ResponseEntity.ok(new AuthResponse(
                token,
                "Bearer",
                tokenService.getExpiresInSeconds(),
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole()));
    }
}
