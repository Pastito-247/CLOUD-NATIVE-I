import { HttpClient } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { environment } from '../../environments/environment';

@Component({
  selector: 'app-reports',
  templateUrl: './reports.component.html',
  styleUrls: ['./reports.component.css']
})
export class ReportsComponent implements OnInit {
  summary: any = null;
  error = '';

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.http.get(environment.reportsUrl + '/summary').subscribe({
      next: (data) => { this.summary = data; },
      error: (err) => { this.error = 'No se pudieron cargar los reportes: ' + err.message; }
    });
  }
}
