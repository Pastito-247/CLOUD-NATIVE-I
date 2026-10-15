package com.ecommerce.users.service;

import com.ecommerce.users.event.OrderCreatedEvent;
import com.ecommerce.users.event.PaymentRequestedEvent;
import com.ecommerce.users.event.ReceiptGeneratedEvent;
import com.ecommerce.users.event.UserRegisteredEvent;
import com.ecommerce.users.model.Notification;
import com.ecommerce.users.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Logica de negocio de notificaciones. No conoce RabbitMQ: es invocada desde
 * los consumidores.
 *
 * Para poder demostrar la ruta de error (reintentos y DLQ) en la presentacion,
 * cualquier evento cuyo correo empiece con "fail@" provoca un fallo simulado.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;

    public NotificationService(NotificationRepository notificationRepository, EmailService emailService) {
        this.notificationRepository = notificationRepository;
        this.emailService = emailService;
    }

    /**
     * Aviso de que el pago esta siendo procesado (fase 1 del flujo de pago).
     */
    public Notification sendPaymentProcessingNotification(PaymentRequestedEvent event) {
        if (event == null || event.paymentId() == null) {
            throw new IllegalArgumentException("Evento de pago invalido: sin paymentId");
        }
        failIfRequested(event.userEmail(), "notificar el pago " + event.paymentId());

        String subject = "Estamos procesando tu pago #" + event.paymentId();
        String message = "Hola, estamos procesando tu pago #%d por un total de %s. "
                .formatted(event.paymentId(), formatClp(event.amount()))
                + "Te avisaremos cuando se confirme.";

        Notification notification = new Notification(
                event.orderId(), event.userEmail(), subject, message);
        Notification saved = notificationRepository.save(notification);
        log.info("Aviso de pago en proceso {} enviado a {} (pago {})",
                saved.getId(), event.userEmail(), event.paymentId());
        return saved;
    }

    /**
     * Correo de confirmacion del pedido con la boleta adjunta (fase 2).
     */
    public Notification sendOrderConfirmation(ReceiptGeneratedEvent event) {
        if (event == null || event.receiptId() == null) {
            throw new IllegalArgumentException("Evento de boleta invalido: sin receiptId");
        }
        failIfRequested(event.userEmail(), "confirmar el pedido de la boleta " + event.receiptId());

        String subject = "Confirmacion de tu pedido #" + event.orderId();
        String message = "Hola, tu pedido #%d fue pagado y confirmado. Total: %s. "
                .formatted(event.orderId(), formatClp(event.total()))
                + "Adjuntamos tu boleta en PDF (" + event.pdfPath() + "). Gracias por comprar en Tuki-Tech.";

        // Envio real del correo al mail del usuario (con la boleta adjunta).
        // Si falla, lanza excepcion -> el consumidor reintenta y luego va a la DLQ.
        emailService.sendReceiptEmail(event);

        Notification notification = new Notification(
                event.orderId(), event.userEmail(), subject, message);
        Notification saved = notificationRepository.save(notification);
        log.info("Correo de confirmacion {} procesado para {} (boleta {})",
                saved.getId(), event.userEmail(), event.receiptId());
        return saved;
    }

    /**
     * Aviso de que el pedido fue recibido (disparado por order.created).
     */
    public Notification sendOrderReceivedNotification(OrderCreatedEvent event) {
        if (event == null || event.orderId() == null) {
            throw new IllegalArgumentException("Evento de pedido invalido: sin orderId");
        }
        failIfRequested(event.userEmail(), "avisar el pedido " + event.orderId());

        String subject = "Recibimos tu pedido #" + event.orderId();
        String message = "Hola, recibimos tu pedido #%d por un total de %s. "
                .formatted(event.orderId(), formatClp(event.total()))
                + "Lo estamos procesando; te avisaremos cuando se confirme el pago.";

        Notification notification = new Notification(
                event.orderId(), event.userEmail(), subject, message);
        Notification saved = notificationRepository.save(notification);
        log.info("Aviso de pedido recibido {} enviado a {} (pedido {})",
                saved.getId(), event.userEmail(), event.orderId());
        return saved;
    }

    /**
     * Correo de bienvenida al registrar una cuenta nueva (user.registered).
     */
    public Notification sendWelcomeNotification(UserRegisteredEvent event) {
        if (event == null || event.userId() == null) {
            throw new IllegalArgumentException("Evento de usuario invalido: sin userId");
        }
        failIfRequested(event.email(), "dar la bienvenida al usuario " + event.userId());

        String subject = "Bienvenido a Tuki-Tech";
        String message = "Hola %s, tu cuenta (%s) fue creada con rol '%s'. "
                .formatted(event.name(), event.email(), event.role())
                + "Gracias por unirte a Tuki-Tech.";

        Notification notification = new Notification(
                null, event.email(), subject, message);
        Notification saved = notificationRepository.save(notification);
        log.info("Correo de bienvenida {} enviado a {} (usuario {})",
                saved.getId(), event.email(), event.userId());
        return saved;
    }

    private String formatClp(BigDecimal value) {
        NumberFormat clp = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CL"));
        clp.setMinimumFractionDigits(0);
        clp.setMaximumFractionDigits(0);
        return "$" + clp.format(value == null ? BigDecimal.ZERO : value);
    }

    private void failIfRequested(String email, String action) {
        if (email != null && email.startsWith("fail@")) {
            throw new IllegalStateException("Fallo simulado al " + action);
        }
    }
}
