package com.ecommerce.payments.controller;

import com.ecommerce.payments.model.Receipt;
import com.ecommerce.payments.service.ReceiptService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Consulta y descarga de boletas (PDF) generadas por el flujo de pago.
 */
@RestController
@RequestMapping("/api/receipts")
public class ReceiptController {

    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Receipt> getReceipt(@PathVariable Long id) {
        return ResponseEntity.ok(receiptService.getReceipt(id));
    }

    @GetMapping("/payment/{paymentId}")
    public ResponseEntity<Receipt> getReceiptByPayment(@PathVariable Long paymentId) {
        return receiptService.getByPaymentId(paymentId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<Resource> downloadPdf(@PathVariable Long id) {
        byte[] pdf = receiptService.loadPdf(id)
                .orElseThrow(() -> new IllegalStateException("Boleta sin PDF disponible: " + id));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=boleta-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(new ByteArrayResource(pdf));
    }
}
