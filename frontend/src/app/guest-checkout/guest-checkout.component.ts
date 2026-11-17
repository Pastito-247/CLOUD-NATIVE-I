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
    this.loading = true;
    const body = {
      customerName: this.customerName,
      customerEmail: this.customerEmail,
      shippingAddress: this.shippingAddress,
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
