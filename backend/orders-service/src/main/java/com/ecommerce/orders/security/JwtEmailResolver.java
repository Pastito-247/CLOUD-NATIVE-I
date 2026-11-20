package com.ecommerce.orders.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public final class JwtEmailResolver {

    private static final List<String> EMAIL_CLAIMS = List.of("email", "preferred_username", "upn", "unique_name");

    private JwtEmailResolver() {
    }

    public static String resolve(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("Se requiere un token JWT");
        }
        for (String claim : EMAIL_CLAIMS) {
            String value = jwt.getClaimAsString(claim);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return jwt.getSubject();
    }

    public static boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_admin"::equals);
    }

    public static void requireOwnerOrAdmin(Authentication authentication, String ownerEmail) {
        if (isAdmin(authentication)) {
            return;
        }
        String email = resolve(authentication);
        if (ownerEmail == null || !ownerEmail.equalsIgnoreCase(email)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Acceso denegado");
        }
    }
}
