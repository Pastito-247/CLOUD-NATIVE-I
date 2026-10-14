import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import {
  MsalBroadcastService,
  MsalService
} from '@azure/msal-angular';

import {
  AuthenticationResult,
  EventMessage,
  EventType,
  InteractionStatus
} from '@azure/msal-browser';

import { Subject } from 'rxjs';
import { filter, takeUntil } from 'rxjs/operators';

import { environment } from '../environments/environment';
import { LocalAuthService } from './auth/local-auth.service';
import { CartItem, CartService } from './services/cart.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit, OnDestroy {

  title = 'Tuki-Tech';

  isLoggedIn = false;
  userRoles: string[] = [];
  userEmail = '';
  isAdmin = false;

  // Carrito
  cartItems: CartItem[] = [];
  cartCount = 0;
  cartOpen = false;
  shippingAddress = '';
  paymentMethod = 'CARD';
  cartMessage = '';
  cartMessageIsError = false;
  checkoutLoading = false;
  menuOpen = false;
  currentYear = new Date().getFullYear();

  // Número en formato internacional (sin '+') para el enlace de WhatsApp.
  // Ej: '573001234567'. CAMBIA aquí tu número real.
  whatsappNumber = '573001234567';
  whatsappMessage = 'Hola Tuki-Tech! Tengo una consulta';

  get whatsappUrl(): string {
    return `https://wa.me/${this.whatsappNumber}?text=${encodeURIComponent(this.whatsappMessage)}`;
  }

  get cartEmpty(): boolean {
    return this.cartItems.length === 0;
  }

  private readonly destroying$ = new Subject<void>();

  constructor(
    private msalService: MsalService,
    private msalBroadcastService: MsalBroadcastService,
    private cartService: CartService,
    private http: HttpClient,
    private localAuth: LocalAuthService,
    private router: Router
  ) {}

  ngOnInit(): void {

    // Procesar el resultado del loginRedirect
    this.msalService.handleRedirectObservable().subscribe({
      next: (result: AuthenticationResult | null) => {

        if (result?.account) {
          this.msalService.instance.setActiveAccount(result.account);
        }

        this.checkLoginStatus();
      },

      error: (error) => {
        console.error('MSAL redirect error:', error);
      }
    });

    // Escuchar cambios de autenticación
    this.msalBroadcastService.msalSubject$
      .pipe(
        filter(
          (msg: EventMessage) =>
            msg.eventType === EventType.LOGIN_SUCCESS ||
            msg.eventType === EventType.ACCOUNT_ADDED ||
            msg.eventType === EventType.ACCOUNT_REMOVED ||
            msg.eventType === EventType.LOGOUT_SUCCESS
        ),
        takeUntil(this.destroying$)
      )
      .subscribe((msg: EventMessage) => {

        if (msg.eventType === EventType.LOGIN_SUCCESS) {
          const result = msg.payload as AuthenticationResult;

          if (result.account) {
            this.msalService.instance.setActiveAccount(result.account);
          }
        }

        this.checkLoginStatus();
      });

    // Esperar a que MSAL termine cualquier interacción
    this.msalBroadcastService.inProgress$
      .pipe(
        filter(
          (status: InteractionStatus) =>
            status === InteractionStatus.None
        ),
        takeUntil(this.destroying$)
      )
      .subscribe(() => {
        this.checkLoginStatus();
      });

    // Sesion local (login con email + contrasena): reaccionar a login/logout
    this.localAuth.session$
      .pipe(takeUntil(this.destroying$))
      .subscribe(() => this.checkLoginStatus());

    // Escuchar cambios del carrito
    this.cartService.items$
      .pipe(takeUntil(this.destroying$))
      .subscribe((items) => {
        this.cartItems = items;
        this.cartCount = items.reduce(
          (sum, item) => sum + item.quantity,
          0
        );
      });
  }

  checkLoginStatus(): void {

    // 1) Cuenta local (email + contrasena) tiene prioridad.
    const localUser = this.localAuth.getUser();
    if (this.localAuth.isLoggedIn() && localUser) {
      this.isLoggedIn = true;
      this.userEmail = localUser.email;
      this.userRoles = localUser.role ? [localUser.role] : [];
      this.isAdmin = this.userRoles.includes('admin');
      return;
    }

    // 2) Cuenta Microsoft (MSAL).
    const accounts = this.msalService.instance.getAllAccounts();

    if (accounts.length === 0) {
      this.isLoggedIn = false;
      this.userRoles = [];
      this.userEmail = '';
      this.isAdmin = false;
      return;
    }

    this.isLoggedIn = true;

    let activeAccount =
      this.msalService.instance.getActiveAccount();

    if (!activeAccount) {
      activeAccount = accounts[0];
      this.msalService.instance.setActiveAccount(activeAccount);
    }

    this.userEmail = activeAccount.username || '';

    const idTokenClaims = activeAccount.idTokenClaims as any;

    this.userRoles = Array.isArray(idTokenClaims?.roles)
      ? idTokenClaims.roles
      : [];

    this.isAdmin = this.userRoles.includes('admin');
  }

  login(): void {
    // La pagina de login ofrece cuenta local o Microsoft.
    this.router.navigate(['/login']);
  }

  logout(): void {

    // Cerrar la sesion local (si existe)
    this.localAuth.logout();

    // Cerrar sesion Microsoft (si existe)
    if (this.msalService.instance.getAllAccounts().length > 0) {
      this.msalService.logoutRedirect({
        postLogoutRedirectUri:
          environment.msalConfig.auth.postLogoutRedirectUri
      });
      return;
    }

    this.checkLoginStatus();
    this.router.navigate(['/']);
  }

  hasRole(role: string): boolean {
    return this.userRoles.includes(role);
  }

  // =========================================================
  // CARRITO
  // =========================================================

  toggleCart(): void {
    this.cartOpen = !this.cartOpen;
    this.clearCartMessage();
  }

  closeCart(): void {
    this.cartOpen = false;
  }

  increaseQty(item: CartItem): void {
    if (this.atMaxStock(item)) {
      return;
    }

    this.cartService.updateQuantity(
      item.product.id,
      item.quantity + 1
    );
  }

  atMaxStock(item: CartItem): boolean {
    const stock = Number(item.product.stockQuantity);

    return Number.isFinite(stock) && item.quantity >= stock;
  }

  decreaseQty(item: CartItem): void {
    if (item.quantity > 1) {
      this.cartService.updateQuantity(
        item.product.id,
        item.quantity - 1
      );
    } else {
      this.cartService.removeFromCart(item.product.id);
    }
  }

  removeItem(item: CartItem): void {
    this.cartService.removeFromCart(item.product.id);
  }

  getCartTotal(): number {
    return this.cartService.getTotal();
  }

  checkout(): void {
    this.clearCartMessage();

    if (!this.isLoggedIn) {
      this.login();
      return;
    }

    if (!this.shippingAddress.trim()) {
      this.cartMessage = 'Ingresa una dirección de envío.';
      this.cartMessageIsError = true;
      return;
    }

    if (this.cartItems.length === 0) {
      this.cartMessage = 'Tu carrito está vacío.';
      this.cartMessageIsError = true;
      return;
    }

    this.checkoutLoading = true;

    const items = this.cartService.getItems().map(item => ({
      productId: item.product.id,
      productName: item.product.name,
      quantity: item.quantity,
      unitPrice: item.product.price
    }));
    const total = this.getCartTotal();

    const order = {
      userEmail: this.userEmail,
      total: total,
      shippingAddress: this.shippingAddress.trim(),
      status: 'PENDING'
    };

    // 1) Crea el pedido en orders-service (publica order.created -> users).
    this.http.post<any>(environment.ordersUrl, order).subscribe({
      next: (createdOrder) => {
        // 2) Registra el pago en payments-service (dispara el flujo asincrono
        //    payment.requested -> notificacion + simulacion/boleta).
        this.payOrder(createdOrder?.id, total, items);
      },
      error: (error) => {
        console.error('Error creating order:', error);
        this.checkoutLoading = false;
        this.cartMessage = 'No se pudo realizar la compra. Intenta de nuevo.';
        this.cartMessageIsError = true;
      }
    });
  }

  private payOrder(orderId: number, total: number, items: any[]): void {
    const payment = {
      orderId: orderId,
      userEmail: this.userEmail,
      amount: total,
      method: this.paymentMethod,
      shippingAddress: this.shippingAddress.trim(),
      items: items
    };

    this.http.post(environment.paymentsUrl, payment).subscribe({
      next: () => {
        this.cartService.clearCart();
        this.shippingAddress = '';
        this.checkoutLoading = false;
        this.cartMessage = '¡Compra realizada! Estamos procesando tu pago.';
        this.cartMessageIsError = false;
      },
      error: (error) => {
        console.error('Error creating payment:', error);
        this.checkoutLoading = false;
        this.cartMessage =
          'Tu pedido fue creado, pero el pago no pudo iniciarse. Revísalo en "Mis compras".';
        this.cartMessageIsError = true;
      }
    });
  }

  private clearCartMessage(): void {
    this.cartMessage = '';
    this.cartMessageIsError = false;
  }

  ngOnDestroy(): void {
    this.destroying$.next();
    this.destroying$.complete();
  }
}