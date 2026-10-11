package com.ecommerce.rabbitadmin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades del microservicio administrador: acceso a la Management HTTP
 * API de RabbitMQ (se usa para listar recursos; la creacion/eliminacion se
 * hace por AMQP).
 */
@ConfigurationProperties(prefix = "rabbitmq.management")
public class RabbitAdminProperties {

    private boolean enabled = true;
    private String baseUrl = "http://localhost:15672";
    private String username = "guest";
    private String password = "guest";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
