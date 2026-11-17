import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';

@Component({
  selector: 'app-my-orders',
  templateUrl: './my-orders.component.html',
  styleUrls: ['./my-orders.component.css']
})
export class MyOrdersComponent implements OnInit, OnDestroy {

  orders: any[] = [];
  loading = true;
  error = '';

  private readonly statusLabels: Record<string, string> = {
    PENDING: 'Pendiente',
    CONFIRMED: 'Confirmada',
    SHIPPED: 'Enviada',
    DELIVERED: 'Entregada',
    CANCELLED: 'Cancelada'
  };

  private pollHandle: ReturnType<typeof setTimeout> | null = null;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.loadOrders();
  }

  ngOnDestroy(): void {
    this.clearPoll();
  }

  get hasPendingOrders(): boolean {
    return this.orders.some(order => order.status === 'PENDING');
  }

  refresh(): void {
    this.clearPoll();
    this.loadOrders();
  }

  loadOrders(silent = false): void {
    if (!silent) {
      this.loading = true;
      this.error = '';
    }

    this.http.get<any[]>(
      `${environment.ordersUrl}/my-orders`
    ).subscribe({
      next: (data) => {
        this.orders = data;
        this.loading = false;
        this.scheduleRefresh();
      },
      error: (error) => {
        console.error('Error loading my orders:', error);
        if (!silent) {
          this.error = 'No se pudieron cargar tus órdenes. Intenta de nuevo.';
          this.loading = false;
        }
      }
    });
  }

  // Mientras existan pedidos pendientes (el pago sigue procesandose en el
  // backend) se refetchea el estado hasta que se confirme o falle.
  private scheduleRefresh(): void {
    this.clearPoll();
    if (this.hasPendingOrders) {
      this.pollHandle = setTimeout(() => this.loadOrders(true), 6000);
    }
  }

  private clearPoll(): void {
    if (this.pollHandle) {
      clearTimeout(this.pollHandle);
      this.pollHandle = null;
    }
  }

  statusLabel(status: string): string {
    return this.statusLabels[status] || status;
  }

  statusClass(status: string): string {
    const map: Record<string, string> = {
      PENDING: 'status-pending',
      CONFIRMED: 'status-confirmed',
      SHIPPED: 'status-shipped',
      DELIVERED: 'status-delivered',
      CANCELLED: 'status-cancelled'
    };

    return map[status] || 'status-pending';
  }
}