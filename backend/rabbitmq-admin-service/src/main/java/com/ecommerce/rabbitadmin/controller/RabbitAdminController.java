package com.ecommerce.rabbitadmin.controller;

import com.ecommerce.rabbitadmin.dto.BindingDefinitionRequest;
import com.ecommerce.rabbitadmin.dto.BindingInfo;
import com.ecommerce.rabbitadmin.dto.ExchangeDefinitionRequest;
import com.ecommerce.rabbitadmin.dto.ExchangeInfo;
import com.ecommerce.rabbitadmin.dto.QueueDefinitionRequest;
import com.ecommerce.rabbitadmin.dto.QueueInfo;
import com.ecommerce.rabbitadmin.service.RabbitAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * API REST de administracion de RabbitMQ.
 *
 * <pre>
 *   POST   /api/rabbit/queues             crear cola
 *   GET    /api/rabbit/queues             listar colas
 *   GET    /api/rabbit/queues/{name}      detalle de una cola
 *   DELETE /api/rabbit/queues/{name}      eliminar cola
 *
 *   POST   /api/rabbit/exchanges          crear exchange
 *   GET    /api/rabbit/exchanges          listar exchanges
 *   DELETE /api/rabbit/exchanges/{name}   eliminar exchange
 *
 *   POST   /api/rabbit/bindings           crear binding
 *   GET    /api/rabbit/bindings           listar bindings
 *   DELETE /api/rabbit/bindings           eliminar binding
 * </pre>
 */
@RestController
@RequestMapping("/api/rabbit")
public class RabbitAdminController {

    private final RabbitAdminService rabbitAdminService;

    public RabbitAdminController(RabbitAdminService rabbitAdminService) {
        this.rabbitAdminService = rabbitAdminService;
    }

    // ==================================================================
    // Colas
    // ==================================================================

    @PostMapping("/queues")
    public ResponseEntity<Void> createQueue(@Valid @RequestBody QueueDefinitionRequest request) {
        rabbitAdminService.createQueue(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/queues")
    public ResponseEntity<List<QueueInfo>> listQueues() {
        return ResponseEntity.ok(rabbitAdminService.listQueues());
    }

    @GetMapping("/queues/{name}")
    public ResponseEntity<Map<String, Object>> getQueue(@PathVariable String name) {
        Properties properties = rabbitAdminService.getQueueProperties(name);
        Map<String, Object> body = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            body.put(key, properties.getProperty(key));
        }
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/queues/{name}")
    public ResponseEntity<Void> deleteQueue(@PathVariable String name) {
        rabbitAdminService.deleteQueue(name);
        return ResponseEntity.noContent().build();
    }

    // ==================================================================
    // Exchanges
    // ==================================================================

    @PostMapping("/exchanges")
    public ResponseEntity<Void> createExchange(@Valid @RequestBody ExchangeDefinitionRequest request) {
        rabbitAdminService.createExchange(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/exchanges")
    public ResponseEntity<List<ExchangeInfo>> listExchanges() {
        return ResponseEntity.ok(rabbitAdminService.listExchanges());
    }

    @DeleteMapping("/exchanges/{name}")
    public ResponseEntity<Void> deleteExchange(@PathVariable String name) {
        rabbitAdminService.deleteExchange(name);
        return ResponseEntity.noContent().build();
    }

    // ==================================================================
    // Bindings
    // ==================================================================

    @PostMapping("/bindings")
    public ResponseEntity<Void> createBinding(@Valid @RequestBody BindingDefinitionRequest request) {
        rabbitAdminService.createBinding(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/bindings")
    public ResponseEntity<List<BindingInfo>> listBindings() {
        return ResponseEntity.ok(rabbitAdminService.listBindings());
    }

    @DeleteMapping("/bindings")
    public ResponseEntity<Void> deleteBinding(@Valid @RequestBody BindingDefinitionRequest request) {
        rabbitAdminService.deleteBinding(request);
        return ResponseEntity.noContent().build();
    }
}
