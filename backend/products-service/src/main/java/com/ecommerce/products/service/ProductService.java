package com.ecommerce.products.service;

import com.ecommerce.products.messaging.ProductEventPublisher;
import com.ecommerce.products.event.OrderItemEvent;
import com.ecommerce.products.model.Product;
import com.ecommerce.products.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final ProductEventPublisher productEventPublisher;

    public ProductService(ProductRepository productRepository,
                          ProductEventPublisher productEventPublisher) {
        this.productRepository = productRepository;
        this.productEventPublisher = productEventPublisher;
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    public List<Product> getProductsByCategory(String category) {
        return productRepository.findByCategory(category);
    }

    public List<Product> searchProducts(String name) {
        return productRepository.findByNameContainingIgnoreCase(name);
    }

    public Product createProduct(Product product) {
        Product saved = productRepository.save(product);
        productEventPublisher.publishProductCreated(saved);
        return saved;
    }

    public Product updateProduct(Long id, Product productDetails) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        product.setName(productDetails.getName());
        product.setDescription(productDetails.getDescription());
        product.setPrice(productDetails.getPrice());
        product.setCategory(productDetails.getCategory());
        product.setStockQuantity(productDetails.getStockQuantity());
        product.setImageUrl(productDetails.getImageUrl());

        Product saved = productRepository.save(product);
        productEventPublisher.publishProductUpdated(saved);
        return saved;
    }

    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        productRepository.delete(product);
        productEventPublisher.publishProductDeleted(product);
    }

    /**
     * Descuenta el stock de los productos comprados. Se invoca desde el
     * consumidor de {@code stock.decrement.queue}. Si un producto no existe o
     * no hay stock suficiente lanza excepcion (el consumidor reintenta y, si
     * agota intentos, envia el mensaje a la DLQ).
     */
    public int decrementStock(List<OrderItemEvent> items) {
        if (items == null || items.isEmpty()) {
            log.warn("Evento de descuento de stock sin items; nada que hacer");
            return 0;
        }

        int affected = 0;
        for (OrderItemEvent item : items) {
            if (item.productId() == null || item.quantity() == null || item.quantity() <= 0) {
                throw new IllegalArgumentException("Item invalido para descuento de stock: " + item);
            }

            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Producto no encontrado para descuento de stock: " + item.productId()));

            int current = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
            if (current < item.quantity()) {
                throw new IllegalStateException(
                        "Stock insuficiente para el producto %d (disponible %d, solicitado %d)"
                                .formatted(item.productId(), current, item.quantity()));
            }

            product.setStockQuantity(current - item.quantity());
            Product saved = productRepository.save(product);
            productEventPublisher.publishProductUpdated(saved);
            affected++;
            log.info("Stock del producto {} descontado en {} (nuevo stock {})",
                    product.getId(), item.quantity(), product.getStockQuantity());
        }
        return affected;
    }
}
