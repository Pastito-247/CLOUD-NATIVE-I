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
<<<<<<< Updated upstream
  usersError = '';
  userRole = '';
=======
  userRoles: string[] = [];
  isAdmin = false;
>>>>>>> Stashed changes

  constructor(
    private http: HttpClient,
    private msalService: MsalService,
    private authService: AuthService
  ) {
    this.userRole = this.authService.getRoles().join(', ');
  }

  ngOnInit(): void {
    this.checkUserRoles();
    this.loadAdminData();
  }

  checkUserRoles(): void {
    const accounts = this.msalService.instance.getAllAccounts();
    if (accounts.length > 0) {
      const account = accounts[0];
      const idTokenClaims = account.idTokenClaims as any;
      this.userRoles = idTokenClaims?.roles || [];
      this.isAdmin = this.userRoles.includes('admin');
    }
  }

  loadAdminData(): void {
    this.loading = true;
<<<<<<< Updated upstream
    this.usersError = '';

    // APIs publicas (catálogo, categorías) - no requieren token
    this.http.get<any[]>(`${environment.productsUrl}`).subscribe({
      next: (data) => {
=======
    
    // Consumir APIs privadas (requieren access token - MSAL interceptor lo agrega automáticamente)
    const productsUrl = environment.apiGatewayUrl ? 
      `${environment.apiGatewayUrl}/products` : 
      environment.productsUrl;
    
    const categoriesUrl = environment.apiGatewayUrl ? 
      `${environment.apiGatewayUrl}/categories` : 
      environment.categoriesUrl;
    
    const usersUrl = environment.apiGatewayUrl ? 
      `${environment.apiGatewayUrl}/users` : 
      environment.usersUrl;

    this.http.get<any[]>(productsUrl).subscribe(
      (data) => {
>>>>>>> Stashed changes
        this.products = data;
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading products:', error);
        this.loading = false;
      }
    });

<<<<<<< Updated upstream
    this.http.get<any[]>(`${environment.categoriesUrl}`).subscribe({
      next: (data) => {
=======
    this.http.get<any[]>(categoriesUrl).subscribe(
      (data) => {
>>>>>>> Stashed changes
        this.categories = data;
      },
      error: (error) => {
        console.error('Error loading categories:', error);
      }
    });

<<<<<<< Updated upstream
    // API PRIVADA (users) - requiere access token con scope 'access_as_user' y rol admin
    // El MsalInterceptor adjunta automaticamente el Bearer token gracias al protectedResourceMap
    this.http.get<any[]>(`${environment.usersUrl}`).subscribe({
      next: (data) => {
=======
    this.http.get<any[]>(usersUrl).subscribe(
      (data) => {
>>>>>>> Stashed changes
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
<<<<<<< Updated upstream
=======
    // Implementar creación de producto (API privada con access token)
>>>>>>> Stashed changes
    console.log('Create product');
  }

  updateProduct(product: any): void {
<<<<<<< Updated upstream
=======
    // Implementar actualización de producto (API privada con access token)
>>>>>>> Stashed changes
    console.log('Update product', product);
  }

  deleteProduct(productId: string): void {
<<<<<<< Updated upstream
=======
    // Implementar eliminación de producto (API privada con access token)
>>>>>>> Stashed changes
    console.log('Delete product', productId);
  }
}