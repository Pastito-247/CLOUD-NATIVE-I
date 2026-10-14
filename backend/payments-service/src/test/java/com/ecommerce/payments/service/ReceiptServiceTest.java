package com.ecommerce.payments.service;

import com.ecommerce.payments.config.MessagingProperties;
import com.ecommerce.payments.event.OrderItemEvent;
import com.ecommerce.payments.event.PaymentRequestedEvent;
import com.ecommerce.payments.model.Payment;
import com.ecommerce.payments.model.Receipt;
import com.ecommerce.payments.repository.ReceiptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReceiptServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void generaUnPdfRealYLoPersiste() throws Exception {
        ReceiptRepository repository = mock(ReceiptRepository.class);
        when(repository.save(any(Receipt.class))).thenAnswer(invocation -> {
            Receipt receipt = invocation.getArgument(0);
            receipt.setId(42L);
            return receipt;
        });

        MessagingProperties props = new MessagingProperties();
        props.getReceipt().setStorageDir(tempDir.toString());

        ReceiptService service = new ReceiptService(repository, props, new ObjectMapper());

        Payment payment = new Payment(10L, "cliente@tuki.cl", new BigDecimal("19990"),
                "CREDIT_CARD", "Av. Siempre Viva 742", "[]");
        payment.setId(7L);

        PaymentRequestedEvent event = new PaymentRequestedEvent(7L, 10L, "cliente@tuki.cl",
                new BigDecimal("19990"), "CREDIT_CARD", "Av. Siempre Viva 742",
                List.of(new OrderItemEvent(5L, "Teclado mecanico", 2, new BigDecimal("9995"))));

        Receipt receipt = service.generateReceipt(payment, event);

        assertThat(receipt.getId()).isEqualTo(42L);
        assertThat(receipt.getPdfPath()).isNotBlank();

        Path pdf = Path.of(receipt.getPdfPath());
        assertThat(Files.exists(pdf)).isTrue();

        byte[] bytes = Files.readAllBytes(pdf);
        assertThat(bytes).isNotEmpty();
        // Firma de un PDF valido.
        assertThat(new String(bytes, 0, 5, StandardCharsets.UTF_8)).isEqualTo("%PDF-");
    }
}
