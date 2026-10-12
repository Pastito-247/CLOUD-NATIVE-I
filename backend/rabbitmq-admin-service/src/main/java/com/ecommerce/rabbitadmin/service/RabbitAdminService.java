package com.ecommerce.rabbitadmin.service;

import com.ecommerce.rabbitadmin.dto.BindingDefinitionRequest;
import com.ecommerce.rabbitadmin.dto.BindingInfo;
import com.ecommerce.rabbitadmin.dto.ExchangeDefinitionRequest;
import com.ecommerce.rabbitadmin.dto.ExchangeInfo;
import com.ecommerce.rabbitadmin.dto.QueueDefinitionRequest;
import com.ecommerce.rabbitadmin.dto.QueueInfo;
import com.ecommerce.rabbitadmin.exception.InvalidRabbitResourceException;
import com.ecommerce.rabbitadmin.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.HeadersExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Servicio dedicado a la administracion de colas, exchanges y bindings.
 *
 * Encapsula toda la interaccion con RabbitMQ (AMQP via {@link AmqpAdmin} y el
 * canal del {@link RabbitTemplate}); los controladores solo llaman metodos de
 * alto nivel y no conocen la API de RabbitMQ.
 */
@Service
public class RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);
    private static final Set<String> RESERVED_PREFIXES = Set.of("amq.");

    private final AmqpAdmin amqpAdmin;
    private final RabbitTemplate rabbitTemplate;
    private final RabbitManagementClient managementClient;

    public RabbitAdminService(AmqpAdmin amqpAdmin,
                              RabbitTemplate rabbitTemplate,
                              RabbitManagementClient managementClient) {
        this.amqpAdmin = amqpAdmin;
        this.rabbitTemplate = rabbitTemplate;
        this.managementClient = managementClient;
    }

    // ==================================================================
    // Colas
    // ==================================================================

    public void createQueue(QueueDefinitionRequest request) {
        validateName(request.getName());
        if (request.isDurable() && request.isExclusive()) {
            throw new InvalidRabbitResourceException(
                    "Una cola no puede ser durable y exclusiva a la vez");
        }
        Queue queue = new Queue(
                request.getName(),
                request.isDurable(),
                request.isExclusive(),
                request.isAutoDelete(),
                request.getArguments());

        amqpAdmin.declareQueue(queue);
        log.info("Cola '{}' creada (durable={}, exclusive={}, autoDelete={})",
                request.getName(), request.isDurable(), request.isExclusive(), request.isAutoDelete());
    }

    public void deleteQueue(String name) {
        validateName(name);
        if (!amqpAdmin.deleteQueue(name)) {
            throw new ResourceNotFoundException("No existe la cola '" + name + "'");
        }
        log.info("Cola '{}' eliminada", name);
    }

    public List<QueueInfo> listQueues() {
        return managementClient.listQueues();
    }

    public Properties getQueueProperties(String name) {
        validateName(name);
        Properties properties = amqpAdmin.getQueueProperties(name);
        if (properties == null) {
            throw new ResourceNotFoundException("No existe la cola '" + name + "'");
        }
        return properties;
    }

    // ==================================================================
    // Exchanges
    // ==================================================================

    public void createExchange(ExchangeDefinitionRequest request) {
        validateName(request.getName());
        Exchange exchange = buildExchange(request);
        amqpAdmin.declareExchange(exchange);
        log.info("Exchange '{}' de tipo '{}' creado", request.getName(), request.getType());
    }

    public void deleteExchange(String name) {
        validateName(name);
        amqpAdmin.deleteExchange(name);
        log.info("Exchange '{}' eliminado", name);
    }

    public List<ExchangeInfo> listExchanges() {
        return managementClient.listExchanges();
    }

    // ==================================================================
    // Bindings
    // ==================================================================

    public void createBinding(BindingDefinitionRequest request) {
        validateName(request.getExchange());
        validateName(request.getQueue());
        // Se usa el canal AMQP directamente para no re-declarar el exchange
        // (su tipo real lo conoce el broker, no este servicio).
        rabbitTemplate.execute(channel -> {
            channel.queueBind(
                    request.getQueue(),
                    request.getExchange(),
                    request.getRoutingKey(),
                    request.getArguments());
            return null;
        });
        log.info("Binding creado: exchange '{}' -> cola '{}' (routingKey='{}')",
                request.getExchange(), request.getQueue(), request.getRoutingKey());
    }

    public void deleteBinding(BindingDefinitionRequest request) {
        validateName(request.getExchange());
        validateName(request.getQueue());
        rabbitTemplate.execute(channel -> {
            channel.queueUnbind(
                    request.getQueue(),
                    request.getExchange(),
                    request.getRoutingKey(),
                    request.getArguments());
            return null;
        });
        log.info("Binding eliminado: exchange '{}' -> cola '{}' (routingKey='{}')",
                request.getExchange(), request.getQueue(), request.getRoutingKey());
    }

    public List<BindingInfo> listBindings() {
        return managementClient.listBindings();
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private Exchange buildExchange(ExchangeDefinitionRequest request) {
        return switch (request.getType()) {
            case "direct" -> new DirectExchange(request.getName(), request.isDurable(), request.isAutoDelete(), request.getArguments());
            case "topic" -> new TopicExchange(request.getName(), request.isDurable(), request.isAutoDelete(), request.getArguments());
            case "fanout" -> new FanoutExchange(request.getName(), request.isDurable(), request.isAutoDelete(), request.getArguments());
            case "headers" -> new HeadersExchange(request.getName(), request.isDurable(), request.isAutoDelete(), request.getArguments());
            default -> throw new InvalidRabbitResourceException(
                    "Tipo de exchange no soportado: " + request.getType());
        };
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidRabbitResourceException("El nombre no puede estar vacio");
        }
        String lower = name.toLowerCase();
        for (String prefix : RESERVED_PREFIXES) {
            if (lower.startsWith(prefix)) {
                throw new InvalidRabbitResourceException(
                        "El prefijo '" + prefix + "' esta reservado por RabbitMQ");
            }
        }
    }
}
