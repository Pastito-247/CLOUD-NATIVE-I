package com.ecommerce.users.service;

import com.ecommerce.users.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Emision del JWT local (HS256) para las cuentas creadas en el registro.
 *
 * El token incluye el claim {@code roles} (el converter de los resource servers
 * lo mapea a ROLE_*) y la misma audiencia que el token de Azure AD, de modo que
 * cualquier microservicio lo acepta con el secreto compartido.
 */
@Service
public class LocalTokenService {

    private final JwtEncoder jwtEncoder;

    @Value("${app.security.local-jwt.issuer}")
    private String issuer;

    @Value("${app.security.local-jwt.expiration-minutes}")
    private long expirationMinutes;

    @Value("${spring.security.oauth2.resourceserver.jwt.audiences}")
    private String audiences;

    public LocalTokenService(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    public String generateToken(User user) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .audience(List.of(primaryAudience()))
                .issuedAt(now)
                .expiresAt(now.plus(expirationMinutes, ChronoUnit.MINUTES))
                .claim("email", user.getEmail())
                .claim("name", user.getName())
                .claim("roles", List.of(user.getRole()))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long getExpiresInSeconds() {
        return expirationMinutes * 60;
    }

    private String primaryAudience() {
        for (String audience : audiences.split(",")) {
            if (!audience.isBlank()) {
                return audience.trim();
            }
        }
        return audiences.trim();
    }
}
