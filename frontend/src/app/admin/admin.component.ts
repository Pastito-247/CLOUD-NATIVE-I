import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MsalService } from '@azure/msal-angular';
import { AuthService } from '../auth/auth.service';
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
  usersError = '';
  userRole = '';

  constructor(
    private http: HttpClient,
    private msalService: MsalService,
    private authService: AuthService
  ) {
    this.userRole = this.authService.getRoles().join(', ');
  }

  ngOnInit(): void {
    this.loadAdminData();
  }

  loadAdminData(): void {
    this.loading = true;
    this.usersError = '';

    // APIs publicas (catálogo, categorías) - no requieren token
    this.http.get<any[]>(`${environment.productsUrl}`).subscribe({
      next: (data) => {
        this.products = data;
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading products:', error);
        this.loading = false;
      }
    });

    this.http.get<any[]>(`${environment.categoriesUrl}`).subscribe({
      next: (data) => {
        this.categories = data;
      },
      error: (error) => {
        console.error('Error loading categories:', error);
      }
    });

    // API PRIVADA (users) - requiere access token con scope 'access_as_user' y rol admin
    // El MsalInterceptor adjunta automaticamente el Bearer token gracias al protectedResourceMap
    this.http.get<any[]>(`${environment.usersUrl}`).subscribe({
      next: (data) => {
        this.users = data;
      },
      error: (error) => {
        console.error('Error loading users:', error);
        if (error.status === 401) {
          this.usersError = 'Token no valido o expirado. Intente iniciar sesion de nuevo.';
        } else if (error.status === 403) {
          this.usersError = 'Acceso denegado. Se requiere rol de administrador.';
        } else {
          this.usersError = 'Error al cargar usuarios: ' + (error.message || 'Error desconocido');
        }
      }
    });
  }

  setActiveTab(tab: string): void {
    this.activeTab = tab;
  }

  createProduct(): void {
    console.log('Create product');
  }

  updateProduct(product: any): void {
    console.log('Update product', product);
  }

  deleteProduct(productId: string): void {
    console.log('Delete product', productId);
  }
}