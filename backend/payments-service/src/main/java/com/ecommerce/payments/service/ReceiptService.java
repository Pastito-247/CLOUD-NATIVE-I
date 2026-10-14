package com.ecommerce.payments.service;

import com.ecommerce.payments.config.MessagingProperties;
import com.ecommerce.payments.event.OrderItemEvent;
import com.ecommerce.payments.event.PaymentRequestedEvent;
import com.ecommerce.payments.model.Payment;
import com.ecommerce.payments.model.Receipt;
import com.ecommerce.payments.repository.ReceiptRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Generacion de la boleta en PDF (OpenPDF) y su persistencia. No conoce
 * RabbitMQ: es invocada desde el consumidor de pantalla.
 */
@Service
public class ReceiptService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptService.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ReceiptRepository receiptRepository;
    private final MessagingProperties props;
    private final ObjectMapper objectMapper;

    public ReceiptService(ReceiptRepository receiptRepository,
                          MessagingProperties props,
                          ObjectMapper objectMapper) {
        this.receiptRepository = receiptRepository;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /**
     * Genera el PDF de la boleta, lo guarda en disco y persiste el registro.
     */
    public Receipt generateReceipt(Payment payment, PaymentRequestedEvent event) {
        byte[] pdf = buildPdf(payment, event);

        String pdfPath;
        try {
            Path dir = Path.of(props.getReceipt().getStorageDir());
            Files.createDirectories(dir);
            Path file = dir.resolve("boleta-pago-" + payment.getId() + ".pdf");
            Files.write(file, pdf);
            pdfPath = file.toAbsolutePath().toString();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo guardar la boleta en disco: " + ex.getMessage(), ex);
        }

        Receipt receipt = new Receipt(
                payment.getId(),
                payment.getOrderId(),
                payment.getUserEmail(),
                payment.getAmount(),
                pdfPath,
                serializeItems(event.items()));
        Receipt saved = receiptRepository.save(receipt);
        log.info("Boleta {} generada para el pago {} ({} bytes)", saved.getId(), payment.getId(), pdf.length);
        return saved;
    }

    /**
     * Recupera el PDF de una boleta desde disco.
     */
    public Optional<byte[]> loadPdf(Long receiptId) {
        return receiptRepository.findById(receiptId)
                .map(Receipt::getPdfPath)
                .filter(path -> path != null && !path.isBlank())
                .map(Path::of)
                .filter(Files::exists)
                .map(path -> {
                    try {
                        return Files.readAllBytes(path);
                    } catch (IOException ex) {
                        throw new IllegalStateException("No se pudo leer la boleta " + receiptId, ex);
                    }
                });
    }

    public Optional<Receipt> getByPaymentId(Long paymentId) {
        return receiptRepository.findByPaymentId(paymentId);
    }

    public Receipt getReceipt(Long id) {
        return receiptRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Boleta no encontrada: " + id));
    }

    private byte[] buildPdf(Payment payment, PaymentRequestedEvent event) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 48, 48, 48, 48);
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font title = FontFactory.getFont(FontFactory.HELVETICA, 18, Font.BOLD);
            Font bold = FontFactory.getFont(FontFactory.HELVETICA, 11, Font.BOLD);
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 11, Font.NORMAL);

            Paragraph header = new Paragraph("TUKI-TECH", title);
            header.setAlignment(Element.ALIGN_CENTER);
            document.add(header);

            Paragraph sub = new Paragraph("BOLETA DE PAGO", bold);
            sub.setAlignment(Element.ALIGN_CENTER);
            sub.setSpacingAfter(16f);
            document.add(sub);

            document.add(new Paragraph("Boleta N\u00b0: " + payment.getId(), normal));
            document.add(new Paragraph("Pago N\u00b0: " + payment.getId(), normal));
            if (payment.getOrderId() != null) {
                document.add(new Paragraph("Pedido N\u00b0: " + payment.getOrderId(), normal));
            }
            document.add(new Paragraph("Cliente: " + payment.getUserEmail(), normal));
            document.add(new Paragraph("Medio de pago: " + payment.getMethod(), normal));
            if (payment.getShippingAddress() != null && !payment.getShippingAddress().isBlank()) {
                document.add(new Paragraph("Direccion de envio: " + payment.getShippingAddress(), normal));
            }
            document.add(new Paragraph("Emitida: " + LocalDateTime.now().format(FORMATTER), normal));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.addCell(cell("Producto", bold));
            table.addCell(cell("Cant.", bold));
            table.addCell(cell("Precio", bold));
            table.addCell(cell("Subtotal", bold));

            List<OrderItemEvent> items = event.items() == null ? List.of() : event.items();
            for (OrderItemEvent item : items) {
                table.addCell(cell(item.productName() != null ? item.productName() : ("Producto " + item.productId()), normal));
                table.addCell(cell(String.valueOf(item.quantity()), normal));
                table.addCell(cell(formatMoney(item.unitPrice()), normal));
                table.addCell(cell(formatMoney(subtotal(item)), normal));
            }
            document.add(table);

            document.add(new Paragraph(" "));
            Paragraph total = new Paragraph("TOTAL: " + formatMoney(payment.getAmount()), bold);
            total.setAlignment(Element.ALIGN_RIGHT);
            document.add(total);

            Paragraph footer = new Paragraph("Pago simulado con fines academicos. Gracias por comprar en Tuki-Tech.", normal);
            footer.setSpacingBefore(24f);
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            document.close();
        } catch (DocumentException ex) {
            throw new IllegalStateException("No se pudo generar el PDF de la boleta: " + ex.getMessage(), ex);
        }
        return out.toByteArray();
    }

    private com.lowagie.text.pdf.PdfPCell cell(String text, Font font) {
        com.lowagie.text.pdf.PdfPCell cell = new com.lowagie.text.pdf.PdfPCell(new Paragraph(text, font));
        cell.setPadding(4f);
        return cell;
    }

    private BigDecimal subtotal(OrderItemEvent item) {
        if (item.unitPrice() == null || item.quantity() == null) {
            return BigDecimal.ZERO;
        }
        return item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()));
    }

    private String formatMoney(BigDecimal value) {
        return "$" + (value == null ? "0" : value.toPlainString());
    }

    private String serializeItems(List<OrderItemEvent> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException ex) {
            log.warn("No se pudieron serializar los items de la boleta: {}", ex.getMessage());
            return "[]";
        }
    }
}
