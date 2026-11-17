package com.ecommerce.audit.controller;

import com.ecommerce.audit.kafka.AuditEventConsumer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditEventConsumer consumer;

    public AuditController(AuditEventConsumer consumer) {
        this.consumer = consumer;
    }

    @GetMapping
    public List<Map<String, Object>> query(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return consumer.query(eventType, actor, from, to);
    }
}
