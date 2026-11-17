package com.ecommerce.orders.messaging.kafka;

import com.ecommerce.orders.event.kafka.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @Value("${kafka.topics.order-events}")
    private String orderEventsTopic;

    public OrderEventPublisher(KafkaTemplate<String, OrderEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderCreated(Long orderId, String trackingCode, String userEmail,
                                    boolean guest, java.math.BigDecimal total, String status) {
        OrderEvent event = new OrderEvent(
                UUID.randomUUID().toString(),
                "ORDER_CREATED",
                orderId,
                trackingCode,
                userEmail,
                guest,
                total,
                status,
                Instant.now(),
                "orders-service",
                Map.of()
        );
        publish(orderId == null ? "guest" : orderId.toString(), event);
    }

    public void publishOrderStatusChanged(Long orderId, String trackingCode, String userEmail,
                                          boolean guest, java.math.BigDecimal total,
                                          String status, String eventType) {
        OrderEvent event = new OrderEvent(
                UUID.randomUUID().toString(),
                eventType,
                orderId,
                trackingCode,
                userEmail,
                guest,
                total,
                status,
                Instant.now(),
                "orders-service",
                Map.of()
        );
        publish(orderId == null ? "guest" : orderId.toString(), event);
    }

    private void publish(String key, OrderEvent event) {
        try {
            kafkaTemplate.send(orderEventsTopic, key, event);
            log.info("Evento Kafka publicado: topic={} key={} eventId={} type={}",
                    orderEventsTopic, key, event.eventId(), event.eventType());
        } catch (Exception ex) {
            log.error("No se pudo publicar evento Kafka (topic={}, eventId={}): {}",
                    orderEventsTopic, event.eventId(), ex.getMessage());
        }
    }
}
