package com.ecommerce.categories.config;

import com.ecommerce.categories.model.Category;
import com.ecommerce.categories.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired
    private CategoryRepository categoryRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (categoryRepository.count() > 0) {
            return;
        }

        List<Category> categories = List.of(
            new Category("Computadores", "Laptops, PCs de escritorio y monitores."),
            new Category("Celulares", "Smartphones y accesorios móviles."),
            new Category("Audio y Sonido", "Audífonos, parlantes y equipos de audio."),
            new Category("Gaming", "Consolas, controles y periféricos gamer."),
            new Category("Accesorios", "Cables, cargadores y complementos."),
            new Category("Hogar Inteligente", "Dispositivos IoT para tu casa.")
        );

        categoryRepository.saveAll(categories);
    }
}