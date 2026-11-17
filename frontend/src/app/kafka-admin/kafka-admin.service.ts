import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class KafkaAdminService {
  private baseUrl = environment.kafkaAdminUrl;

  constructor(private http: HttpClient) {}

  listTopics(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/topics`);
  }

  createTopic(body: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/topics`, body);
  }

  getConfig(name: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/topics/${encodeURIComponent(name)}/config`);
  }

  updateConfig(name: string, body: any): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/topics/${encodeURIComponent(name)}/config`, body);
  }

  deleteTopic(name: string): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/topics/${encodeURIComponent(name)}`);
  }

  listGroups(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/groups`);
  }
}
