package com.ecommerce.rabbitadmin.service;

import com.ecommerce.rabbitadmin.dto.QueueDefinitionRequest;
import com.ecommerce.rabbitadmin.exception.InvalidRabbitResourceException;
import com.ecommerce.rabbitadmin.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RabbitAdminServiceTest {

    private final AmqpAdmin amqpAdmin = mock(AmqpAdmin.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final RabbitManagementClient managementClient = mock(RabbitManagementClient.class);

    private final RabbitAdminService service =
            new RabbitAdminService(amqpAdmin, rabbitTemplate, managementClient);

    @Test
    void createQueue_conNombreVacio_lanzaExcepcion() {
        QueueDefinitionRequest request = new QueueDefinitionRequest();

        assertThrows(InvalidRabbitResourceException.class, () -> service.createQueue(request));
        verify(amqpAdmin, never()).declareQueue(any(Queue.class));
    }

    @Test
    void createQueue_durableYExclusiva_lanzaExcepcion() {
        QueueDefinitionRequest request = new QueueDefinitionRequest();
        request.setName("demo.queue");
        request.setDurable(true);
        request.setExclusive(true);

        assertThrows(InvalidRabbitResourceException.class, () -> service.createQueue(request));
    }

    @Test
    void createQueue_conPrefijoReservado_lanzaExcepcion() {
        QueueDefinitionRequest request = new QueueDefinitionRequest();
        request.setName("amq.reservada");

        assertThrows(InvalidRabbitResourceException.class, () -> service.createQueue(request));
    }

    @Test
    void createQueue_valida_declaraCola() {
        QueueDefinitionRequest request = new QueueDefinitionRequest();
        request.setName("demo.queue");

        service.createQueue(request);

        verify(amqpAdmin).declareQueue(any(Queue.class));
    }

    @Test
    void deleteQueue_inexistente_lanzaNotFound() {
        when(amqpAdmin.deleteQueue("no.existe")).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.deleteQueue("no.existe"));
    }
}
