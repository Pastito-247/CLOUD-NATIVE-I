package com.ecommerce.orders.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class ProductCatalogClient {

    private static final Logger log = LoggerFactory.getLogger(ProductCatalogClient.class);

    private final RestTemplate restTemplate;

    @Value("${products.service.url:http://localhost:8081}")
    private String productsUrl;

    public ProductCatalogClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ProductInfo getProduct(Long productId) {
        try {
            JsonNode body = restTemplate.getForObject(
                    productsUrl + "/api/public/products/" + productId, JsonNode.class);
            if (body == null || body.isNull()) {
                return null;
            }
            String name = body.path("name").asText("");
            JsonNode priceNode = body.path("price");
            if (priceNode.isMissingNode() || priceNode.isNull()) {
                return null;
            }
            BigDecimal price = priceNode.decimalValue();
            return new ProductInfo(productId, name, price);
        } catch (Exception ex) {
            log.warn("No se pudo consultar el producto {}: {}", productId, ex.getMessage());
            return null;
        }
    }

    public record ProductInfo(Long id, String name, BigDecimal price) {}
}
