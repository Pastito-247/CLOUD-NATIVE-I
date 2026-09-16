import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

export interface CartItem {
  product: any;
  quantity: number;
}

const CART_KEY = 'tuki-cart';

@Injectable({
  providedIn: 'root'
})
export class CartService {

  private items: CartItem[] = this.load();
  private itemsSubject = new BehaviorSubject<CartItem[]>(this.items);

  items$: Observable<CartItem[]> = this.itemsSubject.asObservable();

  getItems(): CartItem[] {
    return this.items;
  }

  addToCart(product: any, quantity = 1): void {
    const max = this.maxAvailable(product);
    const existing = this.items.find(
      item => item.product.id === product.id
    );

    if (existing) {
      const next = Math.min(max, existing.quantity + quantity);

      if (next > existing.quantity) {
        existing.quantity = next;
        this.persist();
      }
    } else if (max > 0) {
      this.items.push({
        product,
        quantity: Math.min(max, quantity)
      });
      this.persist();
    }
  }

  updateQuantity(productId: number, quantity: number): void {
    const item = this.items.find(
      i => i.product.id === productId
    );

    if (!item) {
      return;
    }

    item.quantity = Math.max(
      1,
      Math.min(this.maxAvailable(item.product), quantity)
    );

    this.persist();
  }

  getMaxForProduct(product: any): number {
    return this.maxAvailable(product);
  }

  removeFromCart(productId: number): void {
    this.items = this.items.filter(
      i => i.product.id !== productId
    );

    this.persist();
  }

  clearCart(): void {
    this.items = [];
    this.persist();
  }

  getTotal(): number {
    return this.items.reduce(
      (sum, item) => sum + item.product.price * item.quantity,
      0
    );
  }

  getCount(): number {
    return this.items.reduce(
      (sum, item) => sum + item.quantity,
      0
    );
  }

  private persist(): void {
    localStorage.setItem(CART_KEY, JSON.stringify(this.items));
    this.itemsSubject.next(this.items);
  }

  private load(): CartItem[] {
    try {
      const raw = localStorage.getItem(CART_KEY);

      return raw ? JSON.parse(raw) : [];
    } catch {
      return [];
    }
  }

  private maxAvailable(product: any): number {
    const stock = Number(product.stockQuantity);

    return Number.isFinite(stock) && stock >= 0 ? stock : Infinity;
  }
}