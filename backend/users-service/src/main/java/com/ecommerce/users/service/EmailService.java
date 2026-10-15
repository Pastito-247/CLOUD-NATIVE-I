package com.ecommerce.users.service;

import com.ecommerce.users.event.OrderItemEvent;
import com.ecommerce.users.event.ReceiptGeneratedEvent;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Envio del correo real de confirmacion de pago con la boleta adjunta (SMTP).
 *
 * Usa el {@link JavaMailSender} autoconfigurado por Spring Boot (spring.mail.*).
 * El envio real se activa con {@code notification.mail.enabled=true} (env
 * {@code MAIL_ENABLED}); si esta deshabilitado, se omite y solo se loguea, para
 * no romper entornos sin credenciales SMTP (p. ej. tests o demo local).
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String from;
    private final String fromName;

    public EmailService(JavaMailSender mailSender,
                        @Value("${notification.mail.enabled:false}") boolean enabled,
                        @Value("${notification.mail.from:}") String from,
                        @Value("${notification.mail.from-name:Tuki-Tech}") String fromName,
                        @Value("${spring.mail.username:}") String smtpUsername) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.from = (from != null && !from.isBlank()) ? from : smtpUsername;
        this.fromName = fromName;
    }

    /**
     * Envia la confirmacion del pedido al correo del usuario, adjuntando la
     * boleta PDF generada por payments-service.
     *
     * @throws IllegalStateException si el envio falla (dispara reintento/DLQ).
     */
    public void sendReceiptEmail(ReceiptGeneratedEvent event) {
        if (!enabled) {
            log.info("Envio de correo deshabilitado (MAIL_ENABLED=false). Se omite el correo real a {}",
                    event.userEmail());
            return;
        }
        if (event.userEmail() == null || event.userEmail().isBlank()) {
            throw new IllegalArgumentException(
                    "No se puede enviar la boleta " + event.receiptId() + ": el evento no trae correo del usuario");
        }
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(from, fromName));
            helper.setTo(event.userEmail());
            helper.setSubject("Confirmacion de tu pedido #" + event.orderId());
            helper.setText(buildBody(event), true);
            attachReceipt(helper, event);
            mailSender.send(mime);
            log.info("Correo real de confirmacion enviado a {} (pedido {}, boleta {})",
                    event.userEmail(), event.orderId(), event.receiptId());
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "No se pudo enviar el correo de confirmacion a " + event.userEmail(), ex);
        }
    }

    private void attachReceipt(MimeMessageHelper helper, ReceiptGeneratedEvent event) throws Exception {
        String path = event.pdfPath();
        if (path == null || path.isBlank()) {
            log.warn("La boleta {} no trae ruta de PDF; se envia el correo sin adjunto", event.receiptId());
            return;
        }
        File file = new File(path);
        if (!file.isFile()) {
            log.warn("Boleta {} no encontrada en '{}'; se envia el correo sin adjunto", event.receiptId(), path);
            return;
        }
        helper.addAttachment(file.getName(), new FileSystemResource(file));
        log.info("Boleta adjuntada al correo: {}", file.getName());
    }

    private String buildBody(ReceiptGeneratedEvent event) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body style=\"font-family:Arial,sans-serif;color:#222\">");
        html.append("<h2>Gracias por tu compra en Tuki-Tech</h2>");
        html.append("<p>Hola, tu pedido <b>#").append(event.orderId())
                .append("</b> fue pagado y confirmado.</p>");
        html.append("<p>Total pagado: <b>$").append(event.total()).append("</b></p>");
        appendItems(html, event.items());
        html.append("<p>Adjuntamos tu boleta en PDF a este correo.</p>");
        html.append("<p style=\"color:#666;font-size:12px\">Tuki-Tech</p>");
        html.append("</body></html>");
        return html.toString();
    }

    private void appendItems(StringBuilder html, List<OrderItemEvent> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        html.append("<table cellpadding=\"6\" cellspacing=\"0\" border=\"1\" ")
                .append("style=\"border-collapse:collapse;border-color:#ddd\">");
        html.append("<tr style=\"background:#f5f5f5\"><th>Producto</th><th>Cant.</th><th>Precio</th></tr>");
        for (OrderItemEvent item : items) {
            html.append("<tr><td>").append(item.productName())
                    .append("</td><td>").append(item.quantity())
                    .append("</td><td>$").append(item.unitPrice())
                    .append("</td></tr>");
        }
        html.append("</table>");
    }
}
