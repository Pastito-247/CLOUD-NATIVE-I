package com.ecommerce.rabbitadmin.controller;

import com.ecommerce.rabbitadmin.config.SecurityConfig;
import com.ecommerce.rabbitadmin.service.RabbitAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica la regla de autorizacion de la API de administracion:
 *   - sin token -> 401,
 *   - token sin rol admin -> 403,
 *   - token con rol admin -> 2xx.
 *
 * Se importa el {@link SecurityConfig} real y se mockea el {@link JwtDecoder}
 * para no depender de la red (JWKS de Azure AD).
 */
@WebMvcTest(controllers = RabbitAdminController.class)
@Import(SecurityConfig.class)
class RabbitAdminControllerSecurityTest {

    private static final String BODY = "{\"name\":\"demo.queue\",\"durable\":true}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RabbitAdminService rabbitAdminService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void sinToken_devuelve401() throws Exception {
        mockMvc.perform(post("/api/rabbit/queues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSinRolAdmin_devuelve403() throws Exception {
        when(jwtDecoder.decode("user-token")).thenReturn(jwtWithRole("customer"));

        mockMvc.perform(post("/api/rabbit/queues")
                        .header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenAdmin_puedeCrearCola() throws Exception {
        when(jwtDecoder.decode("admin-token")).thenReturn(jwtWithRole("admin"));

        mockMvc.perform(post("/api/rabbit/queues")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void tokenAdmin_puedeListarColas() throws Exception {
        when(jwtDecoder.decode("admin-token")).thenReturn(jwtWithRole("admin"));

        mockMvc.perform(get("/api/rabbit/queues")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    private Jwt jwtWithRole(String role) {
        return Jwt.withTokenValue(role + "-token")
                .header("alg", "none")
                .claim("sub", role + "-user")
                .claim("roles", List.of(role))
                .claim("aud", List.of("api://tuki-tech-api"))
                .build();
    }
}
