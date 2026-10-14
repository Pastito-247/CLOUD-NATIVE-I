import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';

export interface LocalUser {
  userId: number;
  email: string;
  name: string;
  role: string;
}

interface LocalSession {
  accessToken: string;
  tokenType: string;
  userId: number;
  email: string;
  name: string;
  role: string;
  expiresAt: number;
}

const STORAGE_KEY = 'tuki.localSession';

/**
 * Sesion local (email + contrasena) contra users-service.
 *
 * El backend responde con un JWT propio (HS256) que el resto de los
 * microservicios aceptan. La sesion se guarda en localStorage y se expone
 * mediante un observable para que la UI reaccione al login/logout.
 */
@Injectable({ providedIn: 'root' })
export class LocalAuthService {

  private readonly sessionSubject = new BehaviorSubject<LocalSession | null>(this.readSession());

  readonly session$ = this.sessionSubject.asObservable();

  constructor(private http: HttpClient) {}

  login(email: string, password: string): Observable<any> {
    return this.http
      .post<any>(`${environment.usersPublicUrl}/login`, { email, password })
      .pipe(tap(response => this.store(response)));
  }

  logout(): void {
    localStorage.removeItem(STORAGE_KEY);
    this.sessionSubject.next(null);
  }

  isLoggedIn(): boolean {
    return this.getSession() !== null;
  }

  getToken(): string | null {
    return this.getSession()?.accessToken ?? null;
  }

  getUser(): LocalUser | null {
    const session = this.getSession();
    if (!session) {
      return null;
    }
    return {
      userId: session.userId,
      email: session.email,
      name: session.name,
      role: session.role
    };
  }

  private store(response: any): void {
    const expiresIn = Number(response?.expiresIn ?? 3600);
    const session: LocalSession = {
      accessToken: response?.accessToken,
      tokenType: response?.tokenType ?? 'Bearer',
      userId: response?.userId,
      email: response?.email,
      name: response?.name,
      role: response?.role,
      expiresAt: Date.now() + expiresIn * 1000
    };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    this.sessionSubject.next(session);
  }

  private getSession(): LocalSession | null {
    const session = this.sessionSubject.value;
    if (!session) {
      return null;
    }
    if (session.expiresAt && session.expiresAt <= Date.now()) {
      this.logout();
      return null;
    }
    return session;
  }

  private readSession(): LocalSession | null {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as LocalSession;
    } catch {
      localStorage.removeItem(STORAGE_KEY);
      return null;
    }
  }
}
