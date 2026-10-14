import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface QueueDefinition {
  name: string;
  durable: boolean;
  exclusive: boolean;
  autoDelete: boolean;
  arguments?: Record<string, unknown>;
}

export interface ExchangeDefinition {
  name: string;
  type: string;
  durable: boolean;
  autoDelete: boolean;
  arguments?: Record<string, unknown>;
}

export interface BindingDefinition {
  exchange: string;
  queue: string;
  routingKey: string;
  arguments?: Record<string, unknown>;
}

export interface QueueInfo {
  name: string;
  durable?: boolean;
  autoDelete?: boolean;
  messages?: number;
  consumers?: number;
  state?: string;
}

export interface ExchangeInfo {
  name: string;
  type?: string;
  durable?: boolean;
  autoDelete?: boolean;
}

export interface BindingInfo {
  source: string;
  destination: string;
  destinationType?: string;
  routingKey?: string;
}

/**
 * Cliente Angular del microservicio rabbitmq-admin-service.
 * Todas las llamadas viajan con el JWT que inyecta AuthInterceptor
 * (token de la cuenta local o de Microsoft).
 */
@Injectable({ providedIn: 'root' })
export class RabbitmqAdminService {

  private readonly baseUrl = environment.rabbitUrl;

  constructor(private http: HttpClient) {}

  // Colas
  listQueues(): Observable<QueueInfo[]> {
    return this.http.get<QueueInfo[]>(`${this.baseUrl}/queues`);
  }

  createQueue(def: QueueDefinition): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/queues`, def);
  }

  deleteQueue(name: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/queues/${encodeURIComponent(name)}`);
  }

  // Exchanges
  listExchanges(): Observable<ExchangeInfo[]> {
    return this.http.get<ExchangeInfo[]>(`${this.baseUrl}/exchanges`);
  }

  createExchange(def: ExchangeDefinition): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/exchanges`, def);
  }

  deleteExchange(name: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/exchanges/${encodeURIComponent(name)}`);
  }

  // Bindings
  listBindings(): Observable<BindingInfo[]> {
    return this.http.get<BindingInfo[]>(`${this.baseUrl}/bindings`);
  }

  createBinding(def: BindingDefinition): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/bindings`, def);
  }

  deleteBinding(def: BindingDefinition): Observable<void> {
    return this.http.request<void>('delete', `${this.baseUrl}/bindings`, { body: def });
  }
}
