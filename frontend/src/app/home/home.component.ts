import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})
export class HomeComponent implements OnInit {
  products: any[] = [];
  categories: any[] = [];
  loading = true;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.loadPublicData();
  }

  loadPublicData(): void {
    // Consumir APIs públicas (no requieren autenticación)
    const productsUrl = environment.apiGatewayUrl ? 
      `${environment.apiGatewayUrl}/products/public` : 
      `${environment.productsUrl}/public`;
    
    const categoriesUrl = environment.apiGatewayUrl ? 
      `${environment.apiGatewayUrl}/categories/public` : 
      `${environment.categoriesUrl}/public`;

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
  }
}
