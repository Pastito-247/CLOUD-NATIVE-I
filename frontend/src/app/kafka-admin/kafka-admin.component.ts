import { Component, OnInit } from '@angular/core';
import { KafkaAdminService } from './kafka-admin.service';

@Component({
  selector: 'app-kafka-admin',
  templateUrl: './kafka-admin.component.html',
  styleUrls: ['./kafka-admin.component.css']
})
export class KafkaAdminComponent implements OnInit {
  topics: any[] = [];
  groups: any[] = [];
  loading = true;
  error = '';
  newTopic = { name: '', partitions: 3, replicationFactor: 3, retentionMs: 604800000 };
  configTopic = '';
  configKey = 'retention.ms';
  configValue = '';
  configResult: any = null;

  constructor(private kafkaAdmin: KafkaAdminService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.kafkaAdmin.listTopics().subscribe({
      next: (data) => { this.topics = data; this.loading = false; },
      error: (err) => { this.error = 'No se pudieron listar los tópicos: ' + err.message; this.loading = false; }
    });
    this.kafkaAdmin.listGroups().subscribe({
      next: (data) => { this.groups = data; },
      error: () => {}
    });
  }

  createTopic(): void {
    this.error = '';
    this.kafkaAdmin.createTopic(this.newTopic).subscribe({
      next: () => { this.newTopic.name = ''; this.load(); },
      error: (err) => { this.error = 'No se pudo crear el tópico: ' + (err.error?.message || err.message); }
    });
  }

  deleteTopic(name: string): void {
    if (!confirm(`¿Eliminar el tópico ${name}?`)) return;
    this.kafkaAdmin.deleteTopic(name).subscribe({
      next: () => this.load(),
      error: (err) => { this.error = 'No se pudo eliminar: ' + (err.error?.message || err.message); }
    });
  }

  viewConfig(name: string): void {
    this.configTopic = name;
    this.configResult = null;
    this.kafkaAdmin.getConfig(name).subscribe({
      next: (data) => { this.configResult = data; },
      error: (err) => { this.error = 'No se pudo leer la configuración: ' + err.message; }
    });
  }

  updateConfig(): void {
    this.kafkaAdmin.updateConfig(this.configTopic, { key: this.configKey, value: this.configValue }).subscribe({
      next: () => this.viewConfig(this.configTopic),
      error: (err) => { this.error = 'No se pudo modificar: ' + (err.error?.message || err.message); }
    });
  }
}
