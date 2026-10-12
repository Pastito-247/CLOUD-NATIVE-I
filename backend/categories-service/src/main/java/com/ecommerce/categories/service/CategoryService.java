package com.ecommerce.categories.service;

import com.ecommerce.categories.messaging.CategoryEventPublisher;
import com.ecommerce.categories.model.Category;
import com.ecommerce.categories.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryEventPublisher categoryEventPublisher;

    public CategoryService(CategoryRepository categoryRepository,
                           CategoryEventPublisher categoryEventPublisher) {
        this.categoryRepository = categoryRepository;
        this.categoryEventPublisher = categoryEventPublisher;
    }

    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    public Optional<Category> getCategoryById(Long id) {
        return categoryRepository.findById(id);
    }

    public Category createCategory(Category category) {
        Category saved = categoryRepository.save(category);
        categoryEventPublisher.publishCategoryCreated(saved);
        return saved;
    }

    public Category updateCategory(Long id, Category categoryDetails) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        category.setName(categoryDetails.getName());
        category.setDescription(categoryDetails.getDescription());

        Category saved = categoryRepository.save(category);
        categoryEventPublisher.publishCategoryUpdated(saved);
        return saved;
    }

    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));
        categoryRepository.delete(category);
        categoryEventPublisher.publishCategoryDeleted(category);
    }
}
