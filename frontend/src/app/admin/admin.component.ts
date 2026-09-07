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

  constructor(private http: HttpClient, private msalService: MsalService) {}

  ngOnInit(): void {
    this.loadAdminData();
  }

  loadAdminData(): void {
    this.loading = true;
    
    // Consumir APIs directas de microservicios para pruebas locales
    this.http.get<any[]>(`${environment.productsUrl}`).subscribe(
      (data) => {
        this.products = data;
        this.loading = false;
      },
      (error) => {
        console.error('Error loading products:', error);
        this.loading = false;
      }
    );

    this.http.get<any[]>(`${environment.categoriesUrl}`).subscribe(
      (data) => {
        this.categories = data;
      },
      (error) => {
        console.error('Error loading categories:', error);
      }
    );

    this.http.get<any[]>(`${environment.usersUrl}`).subscribe(
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
    // Implementar creación de producto
    console.log('Create product');
  }

  updateProduct(product: any): void {
    // Implementar actualización de producto
    console.log('Update product', product);
  }

  deleteProduct(productId: string): void {
    // Implementar eliminación de producto
    console.log('Delete product', productId);
  }
}
