package com.ecommerce.orders.service;

import com.ecommerce.orders.event.OrderCreatedEvent;
import com.ecommerce.orders.messaging.OrderEventPublisher;
import com.ecommerce.orders.model.Order;
import com.ecommerce.orders.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderEventPublisher orderEventPublisher;
    
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
    
    public Order createOrder(Order order) {
        // El estado inicial lo define el backend: un pedido nace PENDING y solo
        // se confirma cuando el pago se procesa correctamente (receipt.generated).
        order.setStatus(Order.STATUS_PENDING);
        Order saved = orderRepository.save(order);
        orderEventPublisher.publishOrderCreated(new OrderCreatedEvent(
                saved.getId(),
                saved.getUserEmail(),
                saved.getTotal(),
                saved.getShippingAddress(),
                saved.getCreatedAt()));
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
                log.info("Pedido {} confirmado tras el pago exitoso", orderId);
            }
        }, () -> log.warn("No se encontro el pedido {} para confirmar", orderId));
    }
    
    public Order updateOrder(Long id, Order orderDetails) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        
        order.setStatus(orderDetails.getStatus());
        order.setShippingAddress(orderDetails.getShippingAddress());
        
        return orderRepository.save(order);
    }
    
    public void deleteOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        orderRepository.delete(order);
    }
}
