package com.ecommerce.report.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "report_kpi")
public class ReportKpi {

    @Id
    private Long id = 1L;

    private long totalOrders;
    private long confirmedOrders;
    private long guestOrders;
    private long authenticatedOrders;
    private long paymentsApproved;
    private long paymentsRejected;

    @Column(precision = 19, scale = 2)
    private BigDecimal revenue = BigDecimal.ZERO;

    @Column(name = "orders_by_status", columnDefinition = "TEXT")
    private String ordersByStatus = "{}";

    public ReportKpi() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(long totalOrders) {
        this.totalOrders = totalOrders;
    }

    public long getConfirmedOrders() {
        return confirmedOrders;
    }

    public void setConfirmedOrders(long confirmedOrders) {
        this.confirmedOrders = confirmedOrders;
    }

    public long getGuestOrders() {
        return guestOrders;
    }

    public void setGuestOrders(long guestOrders) {
        this.guestOrders = guestOrders;
    }

    public long getAuthenticatedOrders() {
        return authenticatedOrders;
    }

    public void setAuthenticatedOrders(long authenticatedOrders) {
        this.authenticatedOrders = authenticatedOrders;
    }

    public long getPaymentsApproved() {
        return paymentsApproved;
    }

    public void setPaymentsApproved(long paymentsApproved) {
        this.paymentsApproved = paymentsApproved;
    }

    public long getPaymentsRejected() {
        return paymentsRejected;
    }

    public void setPaymentsRejected(long paymentsRejected) {
        this.paymentsRejected = paymentsRejected;
    }

    public BigDecimal getRevenue() {
        return revenue;
    }

    public void setRevenue(BigDecimal revenue) {
        this.revenue = revenue;
    }

    public String getOrdersByStatus() {
        return ordersByStatus;
    }

    public void setOrdersByStatus(String ordersByStatus) {
        this.ordersByStatus = ordersByStatus;
    }
}
