import { Component, OnInit } from '@angular/core';
import {
  BindingDefinition,
  BindingInfo,
  ExchangeDefinition,
  ExchangeInfo,
  QueueDefinition,
  QueueInfo,
  RabbitmqAdminService
} from './rabbitmq-admin.service';

@Component({
  selector: 'app-rabbitmq-admin',
  templateUrl: './rabbitmq-admin.component.html',
  styleUrls: ['./rabbitmq-admin.component.css']
})
export class RabbitmqAdminComponent implements OnInit {

  activeTab = 'queues';

  queues: QueueInfo[] = [];
  exchanges: ExchangeInfo[] = [];
  bindings: BindingInfo[] = [];

  loadingQueues = false;
  loadingExchanges = false;
  loadingBindings = false;

  errorMessage = '';
  successMessage = '';

  queueForm: QueueDefinition = {
    name: '',
    durable: true,
    exclusive: false,
    autoDelete: false
  };

  exchangeForm: ExchangeDefinition = {
    name: '',
    type: 'topic',
    durable: true,
    autoDelete: false
  };

  bindingForm: BindingDefinition = {
    exchange: '',
    queue: '',
    routingKey: ''
  };

  readonly exchangeTypes = ['direct', 'topic', 'fanout', 'headers'];

  constructor(private rabbitAdmin: RabbitmqAdminService) {}

  ngOnInit(): void {
    this.loadQueues();
    this.loadExchanges();
    this.loadBindings();
  }

  setActiveTab(tab: string): void {
    this.activeTab = tab;
    this.clearMessages();
  }

  // =========================================================
  // COLAS
  // =========================================================

  loadQueues(): void {
    this.loadingQueues = true;
    this.rabbitAdmin.listQueues().subscribe({
      next: (data) => {
        this.queues = data || [];
        this.loadingQueues = false;
      },
      error: (error) => {
        console.error('Error loading queues:', error);
        this.loadingQueues = false;
      }
    });
  }

  createQueue(): void {
    this.clearMessages();

    if (!this.queueForm.name || !this.queueForm.name.trim()) {
      this.errorMessage = 'El nombre de la cola es obligatorio.';
      return;
    }
    if (this.queueForm.durable && this.queueForm.exclusive) {
      this.errorMessage = 'Una cola no puede ser durable y exclusiva a la vez.';
      return;
    }

    const payload: QueueDefinition = {
      name: this.queueForm.name.trim(),
      durable: this.queueForm.durable,
      exclusive: this.queueForm.exclusive,
      autoDelete: this.queueForm.autoDelete
    };

    this.rabbitAdmin.createQueue(payload).subscribe({
      next: () => {
        this.successMessage = `Cola "${payload.name}" creada correctamente.`;
        this.queueForm = { name: '', durable: true, exclusive: false, autoDelete: false };
        this.loadQueues();
      },
      error: (error) => {
        console.error('Error creating queue:', error);
        this.errorMessage = error?.error?.detail || 'No se pudo crear la cola.';
      }
    });
  }

  deleteQueue(queue: QueueInfo): void {
    this.clearMessages();
    if (!confirm(`¿Eliminar la cola "${queue.name}"?`)) {
      return;
    }
    this.rabbitAdmin.deleteQueue(queue.name).subscribe({
      next: () => {
        this.successMessage = `Cola "${queue.name}" eliminada.`;
        this.queues = this.queues.filter(q => q.name !== queue.name);
      },
      error: (error) => {
        console.error('Error deleting queue:', error);
        this.errorMessage = error?.error?.detail || 'No se pudo eliminar la cola.';
      }
    });
  }

  // =========================================================
  // EXCHANGES
  // =========================================================

  loadExchanges(): void {
    this.loadingExchanges = true;
    this.rabbitAdmin.listExchanges().subscribe({
      next: (data) => {
        this.exchanges = data || [];
        this.loadingExchanges = false;
      },
      error: (error) => {
        console.error('Error loading exchanges:', error);
        this.loadingExchanges = false;
      }
    });
  }

  createExchange(): void {
    this.clearMessages();

    if (!this.exchangeForm.name || !this.exchangeForm.name.trim()) {
      this.errorMessage = 'El nombre del exchange es obligatorio.';
      return;
    }
    if (!this.exchangeForm.type) {
      this.errorMessage = 'Debes seleccionar un tipo de exchange.';
      return;
    }

    const payload: ExchangeDefinition = {
      name: this.exchangeForm.name.trim(),
      type: this.exchangeForm.type,
      durable: this.exchangeForm.durable,
      autoDelete: this.exchangeForm.autoDelete
    };

    this.rabbitAdmin.createExchange(payload).subscribe({
      next: () => {
        this.successMessage = `Exchange "${payload.name}" creado correctamente.`;
        this.exchangeForm = { name: '', type: 'topic', durable: true, autoDelete: false };
        this.loadExchanges();
      },
      error: (error) => {
        console.error('Error creating exchange:', error);
        this.errorMessage = error?.error?.detail || 'No se pudo crear el exchange.';
      }
    });
  }

  deleteExchange(exchange: ExchangeInfo): void {
    this.clearMessages();
    if (!confirm(`¿Eliminar el exchange "${exchange.name}"?`)) {
      return;
    }
    this.rabbitAdmin.deleteExchange(exchange.name).subscribe({
      next: () => {
        this.successMessage = `Exchange "${exchange.name}" eliminado.`;
        this.exchanges = this.exchanges.filter(e => e.name !== exchange.name);
      },
      error: (error) => {
        console.error('Error deleting exchange:', error);
        this.errorMessage = error?.error?.detail || 'No se pudo eliminar el exchange.';
      }
    });
  }

  // =========================================================
  // BINDINGS
  // =========================================================

  loadBindings(): void {
    this.loadingBindings = true;
    this.rabbitAdmin.listBindings().subscribe({
      next: (data) => {
        this.bindings = data || [];
        this.loadingBindings = false;
      },
      error: (error) => {
        console.error('Error loading bindings:', error);
        this.loadingBindings = false;
      }
    });
  }

  createBinding(): void {
    this.clearMessages();

    if (!this.bindingForm.exchange || !this.bindingForm.exchange.trim()) {
      this.errorMessage = 'El exchange es obligatorio.';
      return;
    }
    if (!this.bindingForm.queue || !this.bindingForm.queue.trim()) {
      this.errorMessage = 'La cola es obligatoria.';
      return;
    }

    const payload: BindingDefinition = {
      exchange: this.bindingForm.exchange.trim(),
      queue: this.bindingForm.queue.trim(),
      routingKey: this.bindingForm.routingKey ? this.bindingForm.routingKey.trim() : ''
    };

    this.rabbitAdmin.createBinding(payload).subscribe({
      next: () => {
        this.successMessage = 'Binding creado correctamente.';
        this.bindingForm = { exchange: '', queue: '', routingKey: '' };
        this.loadBindings();
      },
      error: (error) => {
        console.error('Error creating binding:', error);
        this.errorMessage = error?.error?.detail || 'No se pudo crear el binding.';
      }
    });
  }

  deleteBinding(binding: BindingInfo): void {
    this.clearMessages();
    const label = `${binding.source} → ${binding.destination} (${binding.routingKey || ''})`;
    if (!confirm(`¿Eliminar el binding ${label}?`)) {
      return;
    }

    const payload: BindingDefinition = {
      exchange: binding.source,
      queue: binding.destination,
      routingKey: binding.routingKey || ''
    };

    this.rabbitAdmin.deleteBinding(payload).subscribe({
      next: () => {
        this.successMessage = 'Binding eliminado.';
        this.loadBindings();
      },
      error: (error) => {
        console.error('Error deleting binding:', error);
        this.errorMessage = error?.error?.detail || 'No se pudo eliminar el binding.';
      }
    });
  }

  // =========================================================
  // MENSAJES
  // =========================================================

  clearMessages(): void {
    this.errorMessage = '';
    this.successMessage = '';
  }
}
