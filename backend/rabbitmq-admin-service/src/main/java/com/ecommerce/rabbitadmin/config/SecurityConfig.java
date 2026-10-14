package com.ecommerce.rabbitadmin.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.audiences}")
    private String audiences;

    @Value("${app.security.local-jwt.issuer}")
    private String localIssuer;

    @Value("${app.security.local-jwt.secret}")
    private String localSecret;

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // La administracion de RabbitMQ requiere rol admin.
                .requestMatchers("/api/rabbit/**").hasRole("admin")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        // Token de Azure AD (RS256, validado contra el issuer remoto).
        NimbusJwtDecoder azureDecoder =
            NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
        azureDecoder.setJwtValidator(
            new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                audienceValidator()
            )
        );

        // Token local emitido por users-service (HS256, secreto compartido).
        NimbusJwtDecoder localDecoder =
            NimbusJwtDecoder.withSecretKey(localSecretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        localDecoder.setJwtValidator(
            new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(localIssuer),
                audienceValidator()
            )
        );

        // Se enruta segun el algoritmo de firma: HS256 = local, RS256 = Azure AD.
        return token -> isLocalToken(token)
            ? localDecoder.decode(token)
            : azureDecoder.decode(token);
    }

    private SecretKey localSecretKey() {
        return new SecretKeySpec(localSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    static boolean isLocalToken(String token) {
        try {
            SignedJWT signed = SignedJWT.parse(token);
            return JWSAlgorithm.HS256.equals(signed.getHeader().getAlgorithm());
        } catch (ParseException ex) {
            return false;
        }
    }

    private OAuth2TokenValidator<Jwt> audienceValidator() {
        List<String> expectedAudiences = Arrays.stream(audiences.split(","))
                .map(String::trim)
                .filter(aud -> !aud.isEmpty())
                .toList();

        return token -> {
            Object aud = token.getClaims().get("aud");
            boolean matches = false;

            if (aud instanceof String singleAud) {
                matches = expectedAudiences.contains(singleAud);
            } else if (aud instanceof List<?> audList) {
                matches = audList.stream()
                        .anyMatch(a -> a instanceof String s && expectedAudiences.contains(s));
            }

            if (matches) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token",
                    "The required audience is missing", null)
            );
        };
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");
        grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return jwtAuthenticationConverter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
