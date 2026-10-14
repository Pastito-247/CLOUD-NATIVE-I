package com.ecommerce.categories.service;

import com.ecommerce.categories.messaging.CategoryEventPublisher;
import com.ecommerce.categories.model.Category;
import com.ecommerce.categories.repository.CategoryRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas de la logica de negocio de categorias: persiste y delega la
 * publicacion de eventos en el publisher (config separada de la logica).
 */
class CategoryServiceTest {

    private final CategoryRepository repository = mock(CategoryRepository.class);
    private final CategoryEventPublisher publisher = mock(CategoryEventPublisher.class);

    private final CategoryService service = new CategoryService(repository, publisher);

    @Test
    void crearCategoriaGuardaYPublicaEvento() {
        Category category = new Category("Tecnologia", "Equipos");
        when(repository.save(any(Category.class))).thenReturn(category);

        Category result = service.createCategory(category);

        assertThat(result).isSameAs(category);
        verify(repository).save(category);
        verify(publisher).publishCategoryCreated(category);
    }

    @Test
    void eliminarCategoriaPublicaEvento() {
        Category category = new Category("Tecnologia", "Equipos");
        when(repository.findById(1L)).thenReturn(Optional.of(category));

        service.deleteCategory(1L);

        verify(repository).delete(category);
        verify(publisher).publishCategoryDeleted(category);
    }

    @Test
    void listarCategoriasDevuelveTodas() {
        List<Category> categories = List.of(new Category("A", "a"), new Category("B", "b"));
        when(repository.findAll()).thenReturn(categories);

        assertThat(service.getAllCategories()).hasSize(2);
        verify(repository).findAll();
        verify(publisher, never()).publishCategoryCreated(any());
    }
}
