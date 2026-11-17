import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { GuestOrderService } from '../services/guest-order.service';

@Component({
  selector: 'app-track-order',
  templateUrl: './track-order.component.html',
  styleUrls: ['./track-order.component.css']
})
export class TrackOrderComponent implements OnInit {
  code = '';
  result: any = null;
  error = '';
  loading = false;

  constructor(private route: ActivatedRoute, private guestOrders: GuestOrderService) {}

  ngOnInit(): void {
    this.code = this.route.snapshot.queryParamMap.get('code') || '';
    if (this.code) this.search();
  }

  search(): void {
    this.error = '';
    this.result = null;
    if (!this.code.trim()) {
      this.error = 'Ingresa un código de seguimiento.';
      return;
    }
    this.loading = true;
    this.guestOrders.track(this.code.trim()).subscribe({
      next: (data) => { this.result = data; this.loading = false; },
      error: () => { this.error = 'No encontramos un pedido con ese código.'; this.loading = false; }
    });
  }
}
