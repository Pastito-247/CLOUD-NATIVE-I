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
    // Consumir APIs públicas (no requieren autenticación ni token)
    const productsPublicUrl = environment.productsPublicUrl;
    const categoriesPublicUrl = environment.categoriesPublicUrl;

    this.http.get<any[]>(productsPublicUrl).subscribe(
      (data) => {
        this.products = data;
        this.loading = false;
      },
      (error) => {
        console.error('Error loading products:', error);
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
}
