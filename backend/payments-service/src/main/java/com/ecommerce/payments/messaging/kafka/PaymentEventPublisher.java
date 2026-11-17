package com.ecommerce.payments.messaging.kafka;

import com.ecommerce.payments.event.kafka.PaymentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    @Value("${kafka.topics.payment-events}")
    private String paymentEventsTopic;

    public PaymentEventPublisher(KafkaTemplate<String, PaymentEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishPaymentEvent(Long paymentId, Long orderId, String userEmail,
                                    BigDecimal amount, String status, String eventType) {
        PaymentEvent event = new PaymentEvent(
                UUID.randomUUID().toString(),
                eventType,
                paymentId,
                orderId,
                userEmail,
                amount,
                status,
                Instant.now(),
                "payments-service",
                Map.of()
        );
        publish(String.valueOf(orderId), event);
    }

    private void publish(String key, PaymentEvent event) {
        try {
            kafkaTemplate.send(paymentEventsTopic, key, event);
            log.info("Evento Kafka publicado: topic={} key={} eventId={} type={}",
                    paymentEventsTopic, key, event.eventId(), event.eventType());
        } catch (Exception ex) {
            log.error("No se pudo publicar evento Kafka (topic={}, eventId={}): {}",
                    paymentEventsTopic, event.eventId(), ex.getMessage());
        }
    }
}
