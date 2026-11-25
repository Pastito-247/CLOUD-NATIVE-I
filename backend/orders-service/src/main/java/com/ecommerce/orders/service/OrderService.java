package com.ecommerce.orders.service;

import com.ecommerce.orders.client.ProductCatalogClient;
import com.ecommerce.orders.dto.CreateOrderRequest;
import com.ecommerce.orders.dto.GuestOrderItemRequest;
import com.ecommerce.orders.event.OrderCreatedEvent;
import com.ecommerce.orders.messaging.OrderEventPublisher;
import com.ecommerce.orders.model.Order;
import com.ecommerce.orders.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderEventPublisher orderEventPublisher;

    @Autowired
    private com.ecommerce.orders.messaging.kafka.OrderEventPublisher kafkaOrderEventPublisher;

    @Autowired
    private ProductCatalogClient productCatalogClient;
    
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }
    
    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }
    
    public List<Order> getOrdersByUserEmail(String userEmail) {
        return orderRepository.findByUserEmailIgnoreCase(userEmail);
    }
    
    public List<Order> getOrdersByStatus(String status) {
        return orderRepository.findByStatus(status);
    }
    
    public Order createOrder(CreateOrderRequest request, String userEmail) {
        BigDecimal total = BigDecimal.ZERO;
        for (GuestOrderItemRequest item : request.items()) {
            ProductCatalogClient.ProductInfo product = productCatalogClient.getProduct(item.productId());
            if (product == null || product.price() == null || product.price().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Producto no disponible: " + item.productId());
            }
            total = total.add(product.price().multiply(BigDecimal.valueOf(item.quantity())));
        }

        Order order = new Order(userEmail, total, request.shippingAddress());
        order.setStatus(Order.STATUS_PENDING);
        Order saved = orderRepository.save(order);
        orderEventPublisher.publishOrderCreated(new OrderCreatedEvent(
                saved.getId(),
                saved.getUserEmail(),
                saved.getTotal(),
                saved.getShippingAddress(),
                saved.getCreatedAt()));
        kafkaOrderEventPublisher.publishOrderCreated(
                saved.getId(), null, saved.getUserEmail(), false,
                saved.getTotal(), saved.getStatus());
        return saved;
    }

    /**
     * Confirma el pedido cuando el pago se proceso correctamente. Es idempotente:
     * si ya estaba confirmado (u otro estado avanzado) no hace nada.
     */
    public void confirmOrder(Long orderId) {
        orderRepository.findById(orderId).ifPresentOrElse(order -> {
            if (Order.STATUS_PENDING.equals(order.getStatus())) {
                order.setStatus(Order.STATUS_CONFIRMED);
                orderRepository.save(order);
                kafkaOrderEventPublisher.publishOrderStatusChanged(
                        order.getId(), null, order.getUserEmail(), false,
                        order.getTotal(), order.getStatus(), "ORDER_CONFIRMED");
                log.info("Pedido {} confirmado tras el pago exitoso", orderId);
            }
        }, () -> log.warn("No se encontro el pedido {} para confirmar", orderId));
    }
    
    public Order updateOrder(Long id, Order orderDetails) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        String previousStatus = order.getStatus();

        order.setStatus(orderDetails.getStatus());
        order.setShippingAddress(orderDetails.getShippingAddress());

        Order saved = orderRepository.save(order);
        String eventType = statusEventType(saved.getStatus());

        if (eventType != null && !eventType.equals(statusEventType(previousStatus))) {
            kafkaOrderEventPublisher.publishOrderStatusChanged(
                    saved.getId(), null, saved.getUserEmail(), false,
                    saved.getTotal(), saved.getStatus(), eventType);
        }

        return saved;
    }

    private String statusEventType(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case Order.STATUS_CONFIRMED -> "ORDER_CONFIRMED";
            case Order.STATUS_SHIPPED -> "ORDER_SHIPPED";
            case Order.STATUS_DELIVERED -> "ORDER_DELIVERED";
            case Order.STATUS_CANCELLED -> "ORDER_CANCELLED";
            default -> null;
        };
    }
    
    public void deleteOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        orderRepository.delete(order);
    }
}
