package com.ecommerce.orders.service;

import com.ecommerce.orders.client.ProductCatalogClient;
import com.ecommerce.orders.dto.GuestOrderItemRequest;
import com.ecommerce.orders.dto.GuestOrderRequest;
import com.ecommerce.orders.dto.OrderConfirmation;
import com.ecommerce.orders.event.OrderCreatedEvent;
import com.ecommerce.orders.messaging.OrderEventPublisher;
import com.ecommerce.orders.model.Order;
import com.ecommerce.orders.model.OrderItem;
import com.ecommerce.orders.repository.OrderItemRepository;
import com.ecommerce.orders.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class GuestOrderService {

    private static final Logger log = LoggerFactory.getLogger(GuestOrderService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final OrderRepository orderRepository;
    private final OrderItemRepository itemRepository;
    private final ProductCatalogClient catalogClient;
    private final OrderEventPublisher rabbitPublisher;
    private final com.ecommerce.orders.messaging.kafka.OrderEventPublisher kafkaPublisher;

    public GuestOrderService(OrderRepository orderRepository,
                             OrderItemRepository itemRepository,
                             ProductCatalogClient catalogClient,
                             OrderEventPublisher rabbitPublisher,
                             com.ecommerce.orders.messaging.kafka.OrderEventPublisher kafkaPublisher) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.catalogClient = catalogClient;
        this.rabbitPublisher = rabbitPublisher;
        this.kafkaPublisher = kafkaPublisher;
    }

    @Transactional
    public OrderConfirmation createGuestOrder(GuestOrderRequest request) {
        Order order = new Order();
        order.setGuest(true);
        order.setCustomerName(request.customerName());
        order.setCustomerEmail(request.customerEmail());
        order.setUserEmail(null);
        order.setShippingAddress(request.shippingAddress());
        order.setStatus(Order.STATUS_PENDING);
        order.setTrackingCode(generateTrackingCode());
        Order saved = orderRepository.save(order);

        BigDecimal total = BigDecimal.ZERO;
        List<OrderItem> items = new ArrayList<>();
        for (GuestOrderItemRequest itemRequest : request.items()) {
            ProductCatalogClient.ProductInfo product =
                    catalogClient.getProduct(itemRequest.productId());
            if (product == null) {
                throw new IllegalArgumentException(
                        "Producto no disponible: " + itemRequest.productId());
            }
            OrderItem item = new OrderItem(
                    product.id(), product.name(), product.price(), itemRequest.quantity());
            item.setOrder(saved);
            items.add(item);
            total = total.add(item.getSubtotal());
        }
        itemRepository.saveAll(items);
        saved.setTotal(total);
        orderRepository.save(saved);

        publishEvents(saved, items);

        return new OrderConfirmation(
                saved.getTrackingCode(), saved.getStatus(), saved.getTotal(),
                items.size(), saved.getCreatedAt());
    }

    public Optional<OrderConfirmation> findPublicByTrackingCode(String trackingCode) {
        return orderRepository.findByTrackingCode(trackingCode).map(order ->
                new OrderConfirmation(
                        order.getTrackingCode(), order.getStatus(), order.getTotal(),
                        itemRepository.findByOrder(order).size(), order.getCreatedAt()));
    }

    public List<Order> findByUserEmail(String userEmail) {
        return orderRepository.findByUserEmailIgnoreCase(userEmail);
    }

    private void publishEvents(Order order, List<OrderItem> items) {
        try {
            rabbitPublisher.publishOrderCreated(new OrderCreatedEvent(
                    order.getId(), order.getCustomerEmail(), order.getTotal(),
                    order.getShippingAddress(), order.getCreatedAt()));
        } catch (Exception ex) {
            log.error("No se pudo publicar order.created del pedido guest {}: {}",
                    order.getId(), ex.getMessage());
        }
        kafkaPublisher.publishOrderCreated(
                order.getId(), order.getTrackingCode(), order.getCustomerEmail(),
                true, order.getTotal(), order.getStatus());
    }

    private String generateTrackingCode() {
        StringBuilder sb = new StringBuilder("TUKI-");
        for (int i = 0; i < 8; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
