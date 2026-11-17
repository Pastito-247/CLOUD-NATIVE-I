package com.ecommerce.rabbitadmin.messaging;

import com.ecommerce.rabbitadmin.config.DemoTopologyConfig;
import com.ecommerce.rabbitadmin.dto.DemoPublishRequest;
import com.ecommerce.rabbitadmin.exception.InvalidRabbitResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
public class DemoCommandPublisher {

    private static final Logger log = LoggerFactory.getLogger(DemoCommandPublisher.class);
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "commands", Set.of(
                    DemoTopologyConfig.ORDER_RK,
                    DemoTopologyConfig.PAYMENT_RK,
                    DemoTopologyConfig.RECEIPT_RK),
            "catalog", Set.of(
                    "product.created", "product.updated", "product.deleted",
                    "category.created", "category.updated", "category.deleted"));

    private final RabbitTemplate rabbitTemplate;

    public DemoCommandPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(DemoPublishRequest request) {
        String exchange = request.getExchange().trim().toLowerCase();
        String routingKey = request.getRoutingKey().trim();
        Set<String> allowedKeys = ALLOWED.get(exchange);

        if (allowedKeys == null || !allowedKeys.contains(routingKey)) {
            throw new InvalidRabbitResourceException(
                    "Combinacion exchange/routingKey no permitida: " + exchange + "/" + routingKey);
        }

        String target = "commands".equals(exchange)
                ? DemoTopologyConfig.COMMANDS_EXCHANGE
                : DemoTopologyConfig.CATALOG_EXCHANGE;

        rabbitTemplate.convertAndSend(target, routingKey, request.getBody(), message -> {
            message.getMessageProperties().setHeader("x-demo-fail", request.isFail());
            message.getMessageProperties().setHeader("x-retry-count", 0);
            return message;
        });

        log.info("Demo publicado: exchange={} routingKey={} fail={} body={}",
                target, routingKey, request.isFail(), request.getBody());
    }
}
