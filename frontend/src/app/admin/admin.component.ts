import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MsalService } from '@azure/msal-angular';
import { environment } from '../../environments/environment';

@Component({
  selector: 'app-admin',
  templateUrl: './admin.component.html',
  styleUrls: ['./admin.component.css']
})
export class AdminComponent implements OnInit {

  products: any[] = [];
  categories: any[] = [];
  users: any[] = [];

  activeTab = 'products';
  loading = false;

  userRoles: string[] = [];
  isAdmin = false;

  // PRODUCTOS
  showProductForm = false;
  editingProduct = false;

  productForm = {
    id: null as number | null,
    name: '',
    description: '',
    price: 0,
    category: '',
    stockQuantity: 0,
    imageUrl: ''
  };

  // CATEGORÍAS
  showCategoryForm = false;
  editingCategory = false;

  categoryForm = {
    id: null as number | null,
    name: '',
    description: ''
  };

  errorMessage = '';
  successMessage = '';

  constructor(
    private http: HttpClient,
    private msalService: MsalService
  ) {}

  ngOnInit(): void {
    this.checkUserRoles();
    this.loadAdminData();
  }

  checkUserRoles(): void {
    const accounts = this.msalService.instance.getAllAccounts();

    if (accounts.length > 0) {
      const account =
        this.msalService.instance.getActiveAccount() || accounts[0];

      const idTokenClaims = account.idTokenClaims as any;

      this.userRoles = idTokenClaims?.roles || [];
      this.isAdmin = this.userRoles.includes('admin');
    }
  }

  loadAdminData(): void {
    this.loading = true;
    this.errorMessage = '';

    // Productos
    this.http.get<any[]>(environment.productsUrl).subscribe({
      next: (data) => {
        this.products = data;
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading products:', error);
        this.errorMessage = 'No se pudieron cargar los productos.';
        this.loading = false;
      }
    });

    // Categorías
    this.http.get<any[]>(environment.categoriesUrl).subscribe({
      next: (data) => {
        this.categories = data;
      },
      error: (error) => {
        console.error('Error loading categories:', error);
      }
    });

    // Usuarios
    this.http.get<any[]>(environment.usersUrl).subscribe({
      next: (data) => {
        this.users = data;
      },
      error: (error) => {
        console.error('Error loading users:', error);
      }
    });
  }

  setActiveTab(tab: string): void {
    this.activeTab = tab;
    this.clearMessages();
  }

  // =========================================================
  // PRODUCTOS
  // =========================================================

  createProduct(): void {
    this.editingProduct = false;

    this.productForm = {
      id: null,
      name: '',
      description: '',
      price: 0,
      category: '',
      stockQuantity: 0,
      imageUrl: ''
    };

    this.showProductForm = true;
    this.clearMessages();
  }

  updateProduct(product: any): void {
    this.editingProduct = true;

    this.productForm = {
      id: product.id,
      name: product.name || '',
      description: product.description || '',
      price: product.price || 0,
      category: product.category || '',
      stockQuantity: product.stockQuantity || 0,
      imageUrl: product.imageUrl || ''
    };

    this.showProductForm = true;
    this.clearMessages();
  }

  saveProduct(): void {
    this.clearMessages();

    if (!this.productForm.name.trim()) {
      this.errorMessage = 'El nombre del producto es obligatorio.';
      return;
    }

    if (!this.productForm.category) {
      this.errorMessage = 'Debes seleccionar una categoría.';
      return;
    }

    if (this.productForm.price <= 0) {
      this.errorMessage = 'El precio debe ser mayor a 0.';
      return;
    }

    if (this.productForm.stockQuantity < 0) {
      this.errorMessage = 'El stock no puede ser negativo.';
      return;
    }

    const product = {
      name: this.productForm.name.trim(),
      description: this.productForm.description.trim(),
      price: this.productForm.price,
      category: this.productForm.category,
      stockQuantity: this.productForm.stockQuantity,
      imageUrl: this.productForm.imageUrl.trim()
    };

    // EDITAR PRODUCTO
    if (this.editingProduct && this.productForm.id !== null) {

      this.http.put<any>(
        `${environment.productsUrl}/${this.productForm.id}`,
        product
      ).subscribe({
        next: (updatedProduct) => {

          const index = this.products.findIndex(
            p => p.id === this.productForm.id
          );

          if (index !== -1) {
            this.products[index] = updatedProduct;
          }

          this.showProductForm = false;
          this.successMessage =
            'Producto actualizado correctamente.';
        },

        error: (error) => {
          console.error('Error updating product:', error);
          this.errorMessage =
            'No se pudo actualizar el producto.';
        }
      });

    } else {

      // CREAR PRODUCTO
      this.http.post<any>(
        environment.productsUrl,
        product
      ).subscribe({
        next: (createdProduct) => {

          this.products.push(createdProduct);

          this.showProductForm = false;
          this.successMessage =
            'Producto creado correctamente.';
        },

        error: (error) => {
          console.error('Error creating product:', error);
          this.errorMessage =
            'No se pudo crear el producto.';
        }
      });
    }
  }

  deleteProduct(productId: number): void {
    this.clearMessages();

    const confirmed = confirm(
      '¿Estás seguro de que quieres eliminar este producto?'
    );

    if (!confirmed) {
      return;
    }

    this.http.delete(
      `${environment.productsUrl}/${productId}`
    ).subscribe({
      next: () => {

        this.products = this.products.filter(
          product => product.id !== productId
        );

        this.successMessage =
          'Producto eliminado correctamente.';
      },

      error: (error) => {
        console.error('Error deleting product:', error);
        this.errorMessage =
          'No se pudo eliminar el producto.';
      }
    });
  }

  cancelProductForm(): void {
    this.showProductForm = false;
    this.clearMessages();
  }

  // =========================================================
  // CATEGORÍAS
  // =========================================================

  createCategory(): void {
    this.editingCategory = false;

    this.categoryForm = {
      id: null,
      name: '',
      description: ''
    };

    this.showCategoryForm = true;
    this.clearMessages();
  }

  updateCategory(category: any): void {
    this.editingCategory = true;

    this.categoryForm = {
      id: category.id,
      name: category.name || '',
      description: category.description || ''
    };

    this.showCategoryForm = true;
    this.clearMessages();
  }

  saveCategory(): void {
    this.clearMessages();

    if (!this.categoryForm.name.trim()) {
      this.errorMessage =
        'El nombre de la categoría es obligatorio.';
      return;
    }

    const category = {
      name: this.categoryForm.name.trim(),
      description: this.categoryForm.description.trim()
    };

    // EDITAR CATEGORÍA
    if (
      this.editingCategory &&
      this.categoryForm.id !== null
    ) {

      this.http.put<any>(
        `${environment.categoriesUrl}/${this.categoryForm.id}`,
        category
      ).subscribe({
        next: (updatedCategory) => {

          const index = this.categories.findIndex(
            c => c.id === this.categoryForm.id
          );

          if (index !== -1) {
            this.categories[index] = updatedCategory;
          }

          /*
           * Si había productos usando la categoría anterior,
           * actualizamos también el nombre en la lista local.
           */
          const oldCategoryName = this.categoryForm.name;

          this.products = this.products.map(product => {
            if (product.category === oldCategoryName) {
              return {
                ...product,
                category: updatedCategory.name
              };
            }

            return product;
          });

          this.showCategoryForm = false;
          this.successMessage =
            'Categoría actualizada correctamente.';
        },

        error: (error) => {
          console.error('Error updating category:', error);

          if (error.status === 409) {
            this.errorMessage =
              'Ya existe una categoría con ese nombre.';
          } else {
            this.errorMessage =
              'No se pudo actualizar la categoría.';
          }
        }
      });

    } else {

      // CREAR CATEGORÍA
      this.http.post<any>(
        environment.categoriesUrl,
        category
      ).subscribe({
        next: (createdCategory) => {

          this.categories.push(createdCategory);

          this.showCategoryForm = false;
          this.successMessage =
            'Categoría creada correctamente.';
        },

        error: (error) => {
          console.error('Error creating category:', error);

          if (error.status === 409) {
            this.errorMessage =
              'Ya existe una categoría con ese nombre.';
          } else {
            this.errorMessage =
              'No se pudo crear la categoría.';
          }
        }
      });
    }
  }

  deleteCategory(categoryId: number): void {
    this.clearMessages();

    const category = this.categories.find(
      c => c.id === categoryId
    );

    const confirmed = confirm(
      `¿Estás seguro de que quieres eliminar la categoría "${category?.name}"?`
    );

    if (!confirmed) {
      return;
    }

    this.http.delete(
      `${environment.categoriesUrl}/${categoryId}`
    ).subscribe({
      next: () => {

        this.categories = this.categories.filter(
          c => c.id !== categoryId
        );

        this.successMessage =
          'Categoría eliminada correctamente.';
      },

      error: (error) => {
        console.error('Error deleting category:', error);

        this.errorMessage =
          'No se pudo eliminar la categoría.';
      }
    });
  }

  cancelCategoryForm(): void {
    this.showCategoryForm = false;
    this.clearMessages();
  }

  // =========================================================
  // MENSAJES
  // =========================================================

  clearMessages(): void {
    this.errorMessage = '';
    this.successMessage = '';
  }
}
