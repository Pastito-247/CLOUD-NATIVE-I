package com.ecommerce.payments.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Component
public class CustomerOrderClient {

    private static final Logger log = LoggerFactory.getLogger(CustomerOrderClient.class);

    private final RestTemplate restTemplate;

    @Value("${orders.service.url:http://localhost:8084}")
    private String ordersUrl;

    public CustomerOrderClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public boolean ownsOrder(Long orderId, String authorization) {
        if (orderId == null || authorization == null || authorization.isBlank()) {
            return false;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
            ResponseEntity<String> response = restTemplate.exchange(
                    ordersUrl + "/api/customer/orders/" + orderId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Forbidden ex) {
            return false;
        } catch (Exception ex) {
            log.warn("No se pudo verificar la orden {} del cliente: {}", orderId, ex.getMessage());
            return false;
        }
    }
}
