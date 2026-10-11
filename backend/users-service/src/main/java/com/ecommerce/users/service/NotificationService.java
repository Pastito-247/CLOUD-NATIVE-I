package com.ecommerce.users.service;

import com.ecommerce.users.event.OrderCreatedEvent;
import com.ecommerce.users.model.Notification;
import com.ecommerce.users.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Logica de negocio de notificaciones. No conoce RabbitMQ: es invocada desde
 * el consumidor.
 *
 * Para poder demostrar la ruta de error (reintentos y DLQ) en la presentacion,
 * cualquier pedido cuyo correo empiece con "fail@" provoca un fallo simulado.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public Notification sendOrderNotification(OrderCreatedEvent event) {
        if (event == null || event.orderId() == null) {
            throw new IllegalArgumentException("Evento de pedido invalido: sin orderId");
        }
        if (event.userEmail() != null && event.userEmail().startsWith("fail@")) {
            throw new IllegalStateException(
                    "Fallo simulado al notificar el pedido " + event.orderId());
        }

        String subject = "Confirmacion de tu pedido #" + event.orderId();
        String message = "Hola, recibimos tu pedido #%d por un total de $%s. Gracias por comprar en Tuki-Tech."
                .formatted(event.orderId(), event.total());

        Notification notification = new Notification(
                event.orderId(), event.userEmail(), subject, message);
        Notification saved = notificationRepository.save(notification);
        log.info("Notificacion {} enviada a {} por el pedido {}",
                saved.getId(), event.userEmail(), event.orderId());
        return saved;
    }
}
