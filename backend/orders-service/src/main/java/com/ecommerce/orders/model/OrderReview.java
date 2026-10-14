package com.ecommerce.orders.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pedido pendiente de revision por el staff/administrador.
 *
 * Se crea de forma asincrona a partir del evento de boleta generada
 * ({@code order.review.queue}) una vez que el pago fue procesado.
 */
@Entity
@Table(name = "order_reviews")
public class OrderReview {

    public static final String STATUS_PENDING = "PENDING_REVIEW";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "receipt_id")
    private Long receiptId;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Column(nullable = false)
    private String status = STATUS_PENDING;

    /** Items comprados serializados como JSON. */
    @Column(name = "items_json", columnDefinition = "TEXT")
    private String itemsJson;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public OrderReview() {
    }

    public OrderReview(Long orderId, Long paymentId, Long receiptId, String userEmail,
                       BigDecimal total, String itemsJson) {
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.receiptId = receiptId;
        this.userEmail = userEmail;
        this.total = total;
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

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(Long paymentId) {
        this.paymentId = paymentId;
    }

    public Long getReceiptId() {
        return receiptId;
    }

    public void setReceiptId(Long receiptId) {
        this.receiptId = receiptId;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
