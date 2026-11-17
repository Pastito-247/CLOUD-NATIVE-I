package com.ecommerce.report.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class ReportEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReportEventConsumer.class);

    private final AtomicInteger totalOrders = new AtomicInteger();
    private final AtomicInteger confirmedOrders = new AtomicInteger();
    private final AtomicInteger guestOrders = new AtomicInteger();
    private final AtomicInteger authOrders = new AtomicInteger();
    private final AtomicInteger paymentsApproved = new AtomicInteger();
    private final AtomicInteger paymentsRejected = new AtomicInteger();
    private final AtomicReference<BigDecimal> revenue = new AtomicReference<>(BigDecimal.ZERO);
    private final Map<String, AtomicInteger> ordersByStatus = new ConcurrentHashMap<>();

    @KafkaListener(
            topics = {"${kafka.topics.order-events}", "${kafka.topics.payment-events}"},
            groupId = "${kafka.consumer.group.report}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            JsonNode event,
            @Header(value = "kafka_receivedTopic", required = false) String topic,
            @Header(value = "kafka_receivedPartitionId", required = false) Integer partition,
            @Header(value = "kafka_receivedOffset", required = false) Long offset) {
        try {
            if (event == null || !event.isObject()) {
                throw new IllegalArgumentException("Evento no es un objeto JSON");
            }
            String eventType = event.path("eventType").asText(null);
            if (eventType == null || eventType.isBlank()) {
                throw new IllegalArgumentException("eventType ausente");
            }
            log.info("Evento recibido: topic={} partition={} offset={} type={}",
                    topic, partition, offset, eventType);

            switch (eventType) {
                case "ORDER_CREATED" -> {
                    totalOrders.incrementAndGet();
                    if (event.path("guest").asBoolean(false)) {
                        guestOrders.incrementAndGet();
                    } else {
                        authOrders.incrementAndGet();
                    }
                    incrementStatus(event.path("status").asText("PENDING"));
                }
                case "ORDER_CONFIRMED", "ORDER_SHIPPED", "ORDER_DELIVERED", "ORDER_CANCELLED" ->
                        incrementStatus(event.path("status").asText(eventType));
                case "PAYMENT_APPROVED" -> {
                    paymentsApproved.incrementAndGet();
                    confirmedOrders.incrementAndGet();
                    addRevenue(event.path("amount").decimalValue());
                }
                case "PAYMENT_REJECTED" -> paymentsRejected.incrementAndGet();
                default -> log.warn("Tipo de evento desconocido: {}", eventType);
            }
        } catch (Exception ex) {
            log.error("Error procesando evento (topic={} partition={} offset={}): {}",
                    topic, partition, offset, ex.getMessage());
            throw ex;
        }
    }

    private void incrementStatus(String status) {
        ordersByStatus.computeIfAbsent(status, k -> new AtomicInteger()).incrementAndGet();
    }

    private void addRevenue(BigDecimal amount) {
        if (amount != null) {
            revenue.updateAndGet(current -> current.add(amount));
        }
    }

    public Map<String, Object> summary() {
        return Map.of(
                "totalOrders", totalOrders.get(),
                "confirmedOrders", confirmedOrders.get(),
                "guestOrders", guestOrders.get(),
                "authenticatedOrders", authOrders.get(),
                "paymentsApproved", paymentsApproved.get(),
                "paymentsRejected", paymentsRejected.get(),
                "revenue", revenue.get(),
                "averageTicket", totalOrders.get() == 0
                        ? BigDecimal.ZERO
                        : revenue.get().divide(BigDecimal.valueOf(totalOrders.get()), 2, java.math.RoundingMode.HALF_UP),
                "ordersByStatus", new java.util.HashMap<>(ordersByStatus)
        );
    }
}
