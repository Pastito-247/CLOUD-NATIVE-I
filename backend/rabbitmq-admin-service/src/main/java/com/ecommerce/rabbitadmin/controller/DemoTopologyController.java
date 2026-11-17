package com.ecommerce.rabbitadmin.controller;

import com.ecommerce.rabbitadmin.config.DemoTopologyConfig;
import com.ecommerce.rabbitadmin.dto.DemoPublishRequest;
import com.ecommerce.rabbitadmin.messaging.DemoCommandPublisher;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/rabbit/demo")
public class DemoTopologyController {

    private final DemoCommandPublisher publisher;

    public DemoTopologyController(DemoCommandPublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping("/publish")
    public ResponseEntity<Map<String, Object>> publish(@Valid @RequestBody DemoPublishRequest request) {
        publisher.publish(request);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("published", true);
        response.put("exchange", request.getExchange());
        response.put("routingKey", request.getRoutingKey());
        response.put("fail", request.isFail());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/topology")
    public Map<String, Object> topology() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("commandsExchange", DemoTopologyConfig.COMMANDS_EXCHANGE);
        response.put("catalogExchange", DemoTopologyConfig.CATALOG_EXCHANGE);
        response.put("dlx", DemoTopologyConfig.DLX_EXCHANGE);
        response.put("commandQueues", new String[]{
                DemoTopologyConfig.ORDER_QUEUE,
                DemoTopologyConfig.PAYMENT_QUEUE,
                DemoTopologyConfig.RECEIPT_QUEUE
        });
        response.put("catalogQueue", DemoTopologyConfig.CATALOG_QUEUE);
        response.put("dlqs", new String[]{
                DemoTopologyConfig.ORDER_DLQ,
                DemoTopologyConfig.PAYMENT_DLQ,
                DemoTopologyConfig.RECEIPT_DLQ,
                DemoTopologyConfig.CATALOG_DLQ
        });
        return response;
    }
}
