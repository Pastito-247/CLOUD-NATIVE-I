package com.ecommerce.audit.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    private final List<Map<String, Object>> records = new CopyOnWriteArrayList<>();

    @KafkaListener(
            topics = {"${kafka.topics.order-events}", "${kafka.topics.payment-events}"},
            groupId = "${kafka.consumer.group.audit}",
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

            String actor = event.hasNonNull("userEmail")
                    ? event.get("userEmail").asText()
                    : (event.path("guest").asBoolean(false) ? "guest" : "unknown");

            Map<String, Object> record = Map.of(
                    "eventId", event.path("eventId").asText(""),
                    "eventType", eventType,
                    "orderId", event.path("orderId").asText(""),
                    "actor", actor,
                    "source", event.path("source").asText(topic == null ? "" : topic),
                    "occurredAt", event.path("occurredAt").asText(Instant.now().toString()),
                    "topic", topic == null ? "" : topic,
                    "partition", partition == null ? -1 : partition,
                    "offset", offset == null ? -1 : offset,
                    "payload", event.toString()
            );
            records.add(record);
            log.info("Auditoria registrada: eventId={} type={} actor={} topic={} partition={} offset={}",
                    record.get("eventId"), eventType, actor, topic, partition, offset);
        } catch (Exception ex) {
            log.error("Error procesando evento de auditoria (topic={} partition={} offset={}): {}",
                    topic, partition, offset, ex.getMessage());
            throw ex;
        }
    }

    public List<Map<String, Object>> query(String eventType, String actor, String from, String to) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> record : records) {
            if (eventType != null && !eventType.isBlank()
                    && !eventType.equals(record.get("eventType"))) continue;
            if (actor != null && !actor.isBlank()
                    && !String.valueOf(record.get("actor")).equalsIgnoreCase(actor)) continue;
            String occurred = String.valueOf(record.get("occurredAt"));
            if (from != null && !from.isBlank() && occurred.compareTo(from) < 0) continue;
            if (to != null && !to.isBlank() && occurred.compareTo(to) > 0) continue;
            result.add(record);
        }
        result.sort(Comparator.comparing(
                (Map<String, Object> r) -> String.valueOf(r.get("occurredAt"))).reversed());
        return result;
    }
}
