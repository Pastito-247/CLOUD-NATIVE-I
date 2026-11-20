package com.ecommerce.audit.kafka;

import com.ecommerce.audit.model.AuditRecord;
import com.ecommerce.audit.repository.AuditRecordRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    private final AuditRecordRepository repository;

    public AuditEventConsumer(AuditRecordRepository repository) {
        this.repository = repository;
    }

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

            AuditRecord record = new AuditRecord();
            record.setEventId(event.path("eventId").asText(""));
            record.setEventType(eventType);
            record.setOrderId(event.path("orderId").asText(""));
            record.setActor(actor);
            record.setSource(event.path("source").asText(topic == null ? "" : topic));
            record.setOccurredAt(event.path("occurredAt").asText(Instant.now().toString()));
            record.setTopic(topic == null ? "" : topic);
            record.setPartition(partition == null ? -1 : partition);
            record.setOffset(offset == null ? -1 : offset);
            record.setPayload(event.toString());
            repository.save(record);

            log.info("Auditoria registrada: eventId={} type={} actor={} topic={} partition={} offset={}",
                    record.getEventId(), eventType, actor, topic, partition, offset);
        } catch (Exception ex) {
            log.error("Error procesando evento de auditoria (topic={} partition={} offset={}): {}",
                    topic, partition, offset, ex.getMessage());
            throw ex;
        }
    }

    public List<Map<String, Object>> query(String eventType, String actor, String from, String to) {
        return repository.search(eventType, actor, from, to).stream()
                .map(this::toMap)
                .toList();
    }

    private Map<String, Object> toMap(AuditRecord record) {
        return Map.ofEntries(
                Map.entry("id", record.getId() == null ? -1L : record.getId()),
                Map.entry("eventId", record.getEventId() == null ? "" : record.getEventId()),
                Map.entry("eventType", record.getEventType() == null ? "" : record.getEventType()),
                Map.entry("orderId", record.getOrderId() == null ? "" : record.getOrderId()),
                Map.entry("actor", record.getActor() == null ? "" : record.getActor()),
                Map.entry("source", record.getSource() == null ? "" : record.getSource()),
                Map.entry("occurredAt", record.getOccurredAt() == null ? "" : record.getOccurredAt()),
                Map.entry("topic", record.getTopic() == null ? "" : record.getTopic()),
                Map.entry("partition", record.getPartition() == null ? -1 : record.getPartition()),
                Map.entry("offset", record.getOffset() == null ? -1L : record.getOffset()),
                Map.entry("payload", record.getPayload() == null ? "" : record.getPayload())
        );
    }
}
