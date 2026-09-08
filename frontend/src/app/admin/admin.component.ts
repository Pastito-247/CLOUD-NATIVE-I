import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MsalService } from '@azure/msal-angular';
import { environment } from '../environments/environment';

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
      const account = accounts[0];
      const idTokenClaims = account.idTokenClaims as any;
      this.userRoles = idTokenClaims?.roles || [];
      this.isAdmin = this.userRoles.includes('admin');
    }
  }

  loadAdminData(): void {
    this.loading = true;
    
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
        this.products = data;
        this.loading = false;
      },
      (error) => {
        console.error('Error loading products:', error);
        this.loading = false;
      }
    );

    this.http.get<any[]>(categoriesUrl).subscribe(
      (data) => {
        this.categories = data;
      },
      (error) => {
        console.error('Error loading categories:', error);
      }
    );

    this.http.get<any[]>(usersUrl).subscribe(
      (data) => {
        this.users = data;
      },
      (error) => {
        console.error('Error loading users:', error);
      }
    );
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