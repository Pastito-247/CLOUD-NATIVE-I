package com.ecommerce.users.messaging;

import com.ecommerce.users.config.MessagingHeaders;
import com.ecommerce.users.config.MessagingProperties;
import com.ecommerce.users.event.UserRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Productor de eventos del dominio de usuarios.
 *
 * Encapsula el envio a RabbitMQ para que la capa de negocio (UserService) no
 * dependa de la API de mensajeria. Un fallo de mensajeria no debe romper el
 * registro del usuario.
 */
@Component
public class UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties props;

    public UserEventPublisher(RabbitTemplate rabbitTemplate, MessagingProperties props) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    /**
     * Publica el evento {@code user.registered} en el exchange fanout de
     * usuarios para disparar el envio asincrono de la bienvenida.
     */
    public void publishUserRegistered(UserRegisteredEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    props.getExchange().getUser(),
                    "",
                    event,
                    message -> {
                        message.getMessageProperties().setHeader(
                                MessagingHeaders.ORIGINAL_TIMESTAMP, LocalDateTime.now().toString());
                        return message;
                    });
            log.info("Evento user.registered publicado para el usuario {}", event.userId());
        } catch (Exception ex) {
            log.error("No se pudo publicar user.registered del usuario {}: {}",
                    event.userId(), ex.getMessage());
        }
    }

    /**
     * Reencola un mensaje en la cola indicada incrementando el contador de
     * reintentos. Se usa desde los consumidores cuando un procesamiento falla
     * y aun quedan intentos disponibles.
     */
    public void republishToQueue(String queue, Object event, int retryCount) {
        rabbitTemplate.convertAndSend("", queue, event, message -> {
            message.getMessageProperties().setHeader(MessagingHeaders.RETRY_COUNT, retryCount);
            return message;
        });
    }
}
