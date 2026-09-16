import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { CartService } from '../services/cart.service';

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})
export class HomeComponent implements OnInit {

  products: any[] = [];
  categories: any[] = [];
  loading = true;
  error = '';

  searchTerm = '';
  selectedCategory = '';
  sortBy = 'name';

  selectedProduct: any = null;
  detailQuantity = 1;

  toastMessage = '';
  toastVisible = false;

  constructor(
    private http: HttpClient,
    private cartService: CartService
  ) {}

  ngOnInit(): void {
    this.loadPublicData();
  }

  loadPublicData(): void {
    const productsPublicUrl = environment.productsPublicUrl;
    const categoriesPublicUrl = environment.categoriesPublicUrl;

    this.http.get<any[]>(productsPublicUrl).subscribe(
      (data) => {
        this.products = data;
        this.loading = false;
      },
      (error) => {
        console.error('Error loading products:', error);
        this.error = 'No se pudieron cargar los productos. Verifica tu conexión e intenta de nuevo.';
        this.loading = false;
      }
    );

    this.http.get<any[]>(categoriesPublicUrl).subscribe(
      (data) => {
        this.categories = data;
      },
      (error) => {
        console.error('Error loading categories:', error);
      }
    );
  }

  get filteredProducts(): any[] {
    let list = this.products.filter((product) => {
      const term = this.searchTerm.trim().toLowerCase();

      const matchesSearch =
        !term ||
        product.name.toLowerCase().includes(term) ||
        (product.description || '').toLowerCase().includes(term) ||
        (product.category || '').toLowerCase().includes(term);

      const matchesCategory =
        !this.selectedCategory ||
        product.category === this.selectedCategory;

      return matchesSearch && matchesCategory;
    });

    switch (this.sortBy) {
      case 'name':
        list = list.sort((a, b) =>
          (a.name || '').localeCompare(b.name || '')
        );
        break;
      case 'priceAsc':
        list = list.sort((a, b) => a.price - b.price);
        break;
      case 'priceDesc':
        list = list.sort((a, b) => b.price - a.price);
        break;
    }

    return list;
  }

  selectCategory(categoryName: string): void {
    this.selectedCategory = categoryName;
  }

  clearFilters(): void {
    this.searchTerm = '';
    this.selectedCategory = '';
    this.sortBy = 'name';
  }

  openDetail(product: any): void {
    this.selectedProduct = product;
    this.detailQuantity = 1;
  }

  closeDetail(): void {
    this.selectedProduct = null;
    this.detailQuantity = 1;
  }

  addToCart(product: any, quantity = 1): void {
    this.cartService.addToCart(product, quantity);
    this.closeDetail();
    this.showToast(`"${product.name}" agregado al carrito`);
  }

  increaseDetailQty(): void {
    if (!this.selectedProduct || this.atDetailMax()) {
      return;
    }

    this.detailQuantity++;
  }

  decreaseDetailQty(): void {
    if (this.detailQuantity > 1) {
      this.detailQuantity--;
    }
  }

  atDetailMax(): boolean {
    if (!this.selectedProduct) {
      return false;
    }

    const stock = Number(this.selectedProduct.stockQuantity);

    return Number.isFinite(stock) && this.detailQuantity >= stock;
  }

  showToast(message: string): void {
    this.toastMessage = message;
    this.toastVisible = true;

    setTimeout(() => {
      this.toastVisible = false;
    }, 2600);
  }

  hasStock(product: any): boolean {
    return product.stockQuantity > 0;
  }
}