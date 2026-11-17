package com.ecommerce.kafkaadmin.controller;

import com.ecommerce.kafkaadmin.dto.CreateTopicRequest;
import com.ecommerce.kafkaadmin.dto.UpdateConfigRequest;
import com.ecommerce.kafkaadmin.service.KafkaAdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/kafka")
public class KafkaAdminController {

    private final KafkaAdminService service;

    public KafkaAdminController(KafkaAdminService service) {
        this.service = service;
    }

    @GetMapping("/topics")
    public Object listTopics() {
        return service.listTopics();
    }

    @PostMapping("/topics")
    public Map<String, Object> createTopic(@Valid @RequestBody CreateTopicRequest request) {
        return service.createTopic(request.name(), request.partitions(),
                request.replicationFactor(), request.retentionMs());
    }

    @GetMapping("/topics/{name}/config")
    public Map<String, Object> getConfig(@PathVariable String name) {
        return service.getConfig(name);
    }

    @PutMapping("/topics/{name}/config")
    public Map<String, Object> updateConfig(@PathVariable String name,
                                            @Valid @RequestBody UpdateConfigRequest request) {
        return service.updateConfig(name, request.key(), request.value());
    }

    @DeleteMapping("/topics/{name}")
    public Map<String, Object> deleteTopic(@PathVariable String name) {
        return service.deleteTopic(name);
    }

    @GetMapping("/groups")
    public Object listGroups() {
        return service.listGroups();
    }
}
