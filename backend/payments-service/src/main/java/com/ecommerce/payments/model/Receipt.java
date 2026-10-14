package com.ecommerce.payments.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Boleta generada (PDF) tras procesar un pago simulado.
 */
@Entity
@Table(name = "receipts")
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    /** Ruta del archivo PDF generado en disco. */
    @Column(name = "pdf_path")
    private String pdfPath;

    @Column(name = "items_json", columnDefinition = "TEXT")
    private String itemsJson;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Receipt() {
    }

    public Receipt(Long paymentId, Long orderId, String userEmail, BigDecimal total,
                   String pdfPath, String itemsJson) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.userEmail = userEmail;
        this.total = total;
        this.pdfPath = pdfPath;
        this.itemsJson = itemsJson;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(Long paymentId) {
        this.paymentId = paymentId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public String getPdfPath() {
        return pdfPath;
    }

    public void setPdfPath(String pdfPath) {
        this.pdfPath = pdfPath;
    }

    public String getItemsJson() {
        return itemsJson;
    }

    public void setItemsJson(String itemsJson) {
        this.itemsJson = itemsJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
