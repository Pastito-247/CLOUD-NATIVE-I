import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { CartItem, CartService } from '../services/cart.service';
import { GuestOrderService } from '../services/guest-order.service';

@Component({
  selector: 'app-guest-checkout',
  templateUrl: './guest-checkout.component.html',
  styleUrls: ['./guest-checkout.component.css']
})
export class GuestCheckoutComponent {
  customerName = '';
  customerEmail = '';
  shippingAddress = '';
  loading = false;
  error = '';
  confirmation: any = null;

  constructor(
    public cartService: CartService,
    private guestOrders: GuestOrderService,
    private router: Router
  ) {}

  get items(): CartItem[] {
    return this.cartService.getItems();
  }

  submit(): void {
    this.error = '';
    if (this.items.length === 0) {
      this.error = 'El carrito está vacío.';
      return;
    }
    if (this.customerName.trim().length < 2) {
      this.error = 'El nombre debe tener al menos 2 caracteres.';
      return;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.customerEmail)) {
      this.error = 'Ingresa un correo válido.';
      return;
    }
    if (this.shippingAddress.trim().length < 5) {
      this.error = 'La dirección debe tener al menos 5 caracteres.';
      return;
    }
    this.loading = true;
    const body = {
      customerName: this.customerName.trim(),
      customerEmail: this.customerEmail.trim(),
      shippingAddress: this.shippingAddress.trim(),
      items: this.items.map(i => ({
        productId: i.product.id,
        quantity: i.quantity
      }))
    };
    this.guestOrders.create(body).subscribe({
      next: (result) => {
        this.confirmation = result;
        this.cartService.clearCart();
        this.loading = false;
      },
      error: (err) => {
        this.error = err.error?.error || 'No se pudo crear el pedido.';
        this.loading = false;
      }
    });
  }

  goToTrack(): void {
    this.router.navigate(['/seguir-pedido'], {
      queryParams: { code: this.confirmation?.trackingCode }
    });
  }
}
