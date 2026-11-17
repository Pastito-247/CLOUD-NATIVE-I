package com.ecommerce.report.controller;

import com.ecommerce.report.kafka.ReportEventConsumer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportEventConsumer consumer;

    public ReportController(ReportEventConsumer consumer) {
        this.consumer = consumer;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        return consumer.summary();
    }

    @GetMapping("/orders-by-status")
    public Map<String, Object> ordersByStatus() {
        Object value = consumer.summary().get("ordersByStatus");
        return Map.of("ordersByStatus", value);
    }
}
