package com.ecommerce.orders.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String entraIssuer;

    @Value("${spring.security.oauth2.resourceserver.jwt.audiences}")
    private String entraAudiences;

    @Value("${cognito.issuer:}")
    private String cognitoIssuer;

    @Value("${cognito.client-id:}")
    private String cognitoClientId;

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/orders").authenticated()
                .requestMatchers("/api/orders/**").authenticated()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .authenticationManagerResolver(authenticationManagerResolver())
            );
        return http.build();
    }

    @Bean
    public AuthenticationManagerResolver<jakarta.servlet.http.HttpServletRequest> authenticationManagerResolver() {
        Map<String, AuthenticationManager> managers = new HashMap<>();
        managers.put(entraIssuer, authenticationManager(decoder(entraIssuer, entraAudiences)));

        if (cognitoIssuer != null && !cognitoIssuer.isBlank()
                && cognitoClientId != null && !cognitoClientId.isBlank()
                && !cognitoClientId.startsWith("TU_")) {
            managers.put(cognitoIssuer, authenticationManager(decoder(cognitoIssuer, cognitoClientId)));
        }

        return request -> {
            String token = extractBearerToken(request);
            if (token == null) {
                throw new org.springframework.security.authentication.BadCredentialsException(
                        "Falta el token Bearer");
            }
            try {
                String issuer = readIssuerClaim(token);
                AuthenticationManager manager = managers.get(issuer);
                if (manager == null) {
                    throw new org.springframework.security.authentication.BadCredentialsException(
                            "Issuer no autorizado: " + issuer);
                }
                return manager;
            } catch (org.springframework.security.authentication.BadCredentialsException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new org.springframework.security.authentication.BadCredentialsException(
                        "Token JWT invalido", ex);
            }
        };
    }

    private AuthenticationManager authenticationManager(JwtDecoder decoder) {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
        provider.setJwtAuthenticationConverter(jwtAuthenticationConverter());
        return provider::authenticate;
    }

    private JwtDecoder decoder(String issuer, String audiences) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), audienceValidator(audiences));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    private OAuth2TokenValidator<Jwt> audienceValidator(String audiences) {
        List<String> expected = Arrays.stream(audiences.split(","))
                .map(String::trim).filter(a -> !a.isEmpty()).toList();
        return token -> {
            Object aud = token.getClaims().get("aud");
            boolean matches = false;
            if (aud instanceof String s) {
                matches = expected.contains(s);
            } else if (aud instanceof List<?> list) {
                matches = list.stream().anyMatch(a -> a instanceof String str && expected.contains(str));
            }
            return matches
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(
                            new OAuth2Error("invalid_token", "The required audience is missing", null));
        };
    }

    private String extractBearerToken(jakarta.servlet.http.HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private String readIssuerClaim(String token) {
        String[] parts = token.split("\\.");
        if (parts.length < 2) throw new IllegalArgumentException("JWT malformado");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]),
                java.nio.charset.StandardCharsets.UTF_8);
        try {
            Map<?, ?> claims = new com.fasterxml.jackson.databind.ObjectMapper().readValue(payload, Map.class);
            Object iss = claims.get("iss");
            if (iss == null) throw new IllegalArgumentException("claim iss ausente");
            return String.valueOf(iss);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalArgumentException("No se pudo leer el claim iss", ex);
        }
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter converter = new JwtGrantedAuthoritiesConverter();
        converter.setAuthorityPrefix("ROLE_");
        converter.setAuthoritiesClaimName("roles");
        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            java.util.Collection<org.springframework.security.core.GrantedAuthority> authorities =
                    new java.util.ArrayList<>(converter.convert(jwt));
            String issuer = String.valueOf(jwt.getClaims().get("iss"));
            if (issuer.contains("cognito-idp")) {
                authorities.clear();
                authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_customer"));
            }
            return authorities;
        });
        return jwtConverter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(o -> !o.isEmpty()).toList());
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
