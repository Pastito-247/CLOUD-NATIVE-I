import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class GuestOrderService {
  constructor(private http: HttpClient) {}

  create(body: any): Observable<any> {
    return this.http.post(environment.publicOrdersUrl, body);
  }

  track(trackingCode: string): Observable<any> {
    return this.http.get(`${environment.publicOrdersUrl}/${encodeURIComponent(trackingCode)}`);
  }

  myOrders(): Observable<any[]> {
    return this.http.get<any[]>(environment.customerOrdersUrl);
  }
}
