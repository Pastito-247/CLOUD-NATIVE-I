package com.ecommerce.users.config;

import com.ecommerce.users.model.User;
import com.ecommerce.users.service.LocalTokenService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica el contrato del JWT local y el enrutamiento del decoder dual.
 *
 * Comprueba que el token emitido por users-service (HS256) se decodifica con el
 * MISMO esquema que usan los resource servers (secreto compartido + issuer), y
 * que el enrutamiento {@link SecurityConfig#isLocalToken(String)} distingue el
 * token local (HS256) del de Azure AD (RS256).
 */
class DualJwtDecoderTest {

    private static final String LOCAL_ISSUER = "tuki-tech-local";
    private static final String LOCAL_SECRET = "tuki-tech-local-dev-secret-change-me-0123456789";
    private static final String OTHER_SECRET = "otro-secreto-distinto-para-probar-0123456789abc";
    private static final String AUDIENCE = "api://tuki-tech-api";

    private SecretKey key(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private LocalTokenService tokenService(String issuer, String secret) {
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key(secret)));
        LocalTokenService service = new LocalTokenService(encoder);
        ReflectionTestUtils.setField(service, "issuer", issuer);
        ReflectionTestUtils.setField(service, "expirationMinutes", 60L);
        ReflectionTestUtils.setField(service, "audiences", AUDIENCE + ",66c0f84b-3701-42fb-9189-3f4092cba201");
        return service;
    }

    private User sampleUser() {
        User user = new User("cliente@tuki.cl", "Cliente", "admin");
        user.setId(42L);
        return user;
    }

    /** Igual que la rama local del SecurityConfig de produccion. */
    private JwtDecoder localDecoder(String issuer, String secret) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(key(secret))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer)));
        return decoder;
    }

    @Test
    void elTokenLocalEsHs256YSeDecodificaConElSecretoCompartido() throws ParseException {
        String token = tokenService(LOCAL_ISSUER, LOCAL_SECRET).generateToken(sampleUser());

        assertThat(SignedJWT.parse(token).getHeader().getAlgorithm())
                .isEqualTo(JWSAlgorithm.HS256);

        Jwt jwt = localDecoder(LOCAL_ISSUER, LOCAL_SECRET).decode(token);

        assertThat(jwt.getClaimAsString("iss")).isEqualTo(LOCAL_ISSUER);
        assertThat(jwt.getAudience()).contains(AUDIENCE);
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("cliente@tuki.cl");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("admin");
    }

    @Test
    void elEnrutamientoReconoceElTokenLocalComoHs256() {
        String localToken = tokenService(LOCAL_ISSUER, LOCAL_SECRET).generateToken(sampleUser());

        assertThat(SecurityConfig.isLocalToken(localToken)).isTrue();
    }

    @Test
    void elEnrutamientoDescartaTokensQueNoSeanHs256() {
        assertThat(SecurityConfig.isLocalToken(fakeToken("RS256"))).isFalse();
        assertThat(SecurityConfig.isLocalToken("esto-no-es-un-jwt")).isFalse();
    }

    @Test
    void unTokenFirmadoConOtroSecretoEsRechazado() {
        String token = tokenService(LOCAL_ISSUER, OTHER_SECRET).generateToken(sampleUser());

        assertThatThrownBy(() -> localDecoder(LOCAL_ISSUER, LOCAL_SECRET).decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void unTokenDeOtroEmisorEsRechazado() {
        String token = tokenService("otro-emisor", LOCAL_SECRET).generateToken(sampleUser());

        assertThatThrownBy(() -> localDecoder(LOCAL_ISSUER, LOCAL_SECRET).decode(token))
                .isInstanceOf(JwtException.class);
    }

    private String fakeToken(String alg) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString(
                ("{\"alg\":\"" + alg + "\",\"typ\":\"JWT\"}").getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString("{}".getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".firma";
    }
}
