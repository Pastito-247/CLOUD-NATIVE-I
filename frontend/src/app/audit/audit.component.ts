import { HttpClient } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { environment } from '../../environments/environment';

@Component({
  selector: 'app-audit',
  templateUrl: './audit.component.html',
  styleUrls: ['./audit.component.css']
})
export class AuditComponent implements OnInit {
  records: any[] = [];
  filters = { eventType: '', actor: '', from: '', to: '' };
  error = '';

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.query();
  }

  query(): void {
    let url = environment.auditUrl;
    const params: string[] = [];
    Object.entries(this.filters).forEach(([key, value]) => {
      if (value) params.push(`${key}=${encodeURIComponent(value)}`);
    });
    if (params.length) url += '?' + params.join('&');
    this.http.get<any[]>(url).subscribe({
      next: (data) => { this.records = data; },
      error: (err) => { this.error = 'No se pudo cargar la auditoría: ' + err.message; }
    });
  }
}
