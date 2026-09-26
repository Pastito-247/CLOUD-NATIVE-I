package com.ecommerce.products.config;

import com.ecommerce.products.model.Product;
import com.ecommerce.products.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired
    private ProductRepository productRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (productRepository.count() > 0) {
            return;
        }

        List<Product> products = List.of(
            new Product("Laptop Lenovo IdeaPad 3", "Core i5, 16GB RAM, SSD 512GB, pantalla 15.6\" Full HD.", new BigDecimal("2599000"), "Computadores"),
            new Product("PC de Escritorio Gaming Core i7", "Core i7, RTX 4060, 32GB RAM, SSD 1TB. Ideal para gaming.", new BigDecimal("4199000"), "Computadores"),
            new Product("Monitor Samsung 24\" Full HD", "Panel IPS de 24 pulgadas, 75Hz, gran ángulo de visión.", new BigDecimal("749000"), "Computadores"),
            new Product("Samsung Galaxy A54 5G", "8GB RAM, 128GB, cámara triple de 50MP y batería 5000mAh.", new BigDecimal("1599000"), "Celulares"),
            new Product("Xiaomi Redmi Note 12", "6GB RAM, 128GB, AMOLED 120Hz y carga rápida 33W.", new BigDecimal("899000"), "Celulares"),
            new Product("iPhone 13 128GB", "Chip A15 Bionic, 128GB y cámara dual de 12MP.", new BigDecimal("3299000"), "Celulares"),
            new Product("Huawei P30 Lite", "4GB RAM, 128GB, gran rendimiento a precio accesible.", new BigDecimal("699000"), "Celulares"),
            new Product("Audífonos JBL Tune 510BT", "Bluetooth, graves potentes y hasta 40h de batería.", new BigDecimal("249000"), "Audio y Sonido"),
            new Product("Audífonos Sony WH-CH520", "Diadema inalámbrica con cancelación de ruido HD.", new BigDecimal("289000"), "Audio y Sonido"),
            new Product("Parlante Bluetooth JBL Clip 5", "Portátil, resistente al agua IP67 y 15h de batería.", new BigDecimal("299000"), "Audio y Sonido"),
            new Product("Consola Xbox Series S", "512GB, 1440p/120fps y Game Pass incluido.", new BigDecimal("1499000"), "Gaming"),
            new Product("Control Inalámbrico PS5 DualSense", "Adaptive triggers y retroalimentación háptica.", new BigDecimal("349000"), "Gaming"),
            new Product("Teclado Mecánico Logitech G413", "Switches mecánicos, retroiluminación RGB y aluminio.", new BigDecimal("429000"), "Gaming"),
            new Product("Cargador Rápido 65W GaN", "Carga USB-C/PD compatible con laptop y celular.", new BigDecimal("69000"), "Accesorios"),
            new Product("Cable USB-C Trenzado 1m", "Carga rápida y transferencia de datos a alta velocidad.", new BigDecimal("19000"), "Accesorios"),
            new Product("Base Refrigeradora para Laptop", "Doble ventilador adjustable, ideal para uso prolongado.", new BigDecimal("129000"), "Accesorios"),
            new Product("Foco Wi-Fi Smart RGB", "Control por app y voz, 16 millones de colores.", new BigDecimal("49900"), "Hogar Inteligente"),
            new Product("Cámara Xiaomi Smart 360º", "Full HD, visión nocturna y detección de movimiento.", new BigDecimal("279000"), "Hogar Inteligente"),
            new Product("Enchufe Inteligente Wi-Fi", "Control remoto de tus electrodomésticos desde el celular.", new BigDecimal("59900"), "Hogar Inteligente")
        );

        int[] stock = { 12, 5, 18, 20, 25, 8, 15, 30, 22, 14, 10, 16, 19, 50, 60, 11, 40, 13, 35 };

        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            product.setStockQuantity(stock[i]);
            product.setImageUrl("https://picsum.photos/seed/tuki-" + (i + 1) + "/400/300");
        }

        productRepository.saveAll(products);
    }
}