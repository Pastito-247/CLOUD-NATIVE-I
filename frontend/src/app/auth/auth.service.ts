import { Injectable } from '@angular/core';
import { MsalService } from '@azure/msal-angular';
import { AccountInfo } from '@azure/msal-browser';
import { LocalAuthService } from './local-auth.service';

/**
 * Autenticacion unificada: cuenta local (email + contrasena) y/o Microsoft.
 *
 * - Local: la sesion y el JWT los gestiona {@link LocalAuthService}.
 * - Microsoft: la sesion la gestiona MSAL.
 *
 * El resto de la app consulta este servicio para saber si hay sesion, el
 * email y los roles, sin preocuparse por el proveedor.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {

  constructor(
    private msalService: MsalService,
    private localAuth: LocalAuthService
  ) {}

  isLocalSession(): boolean {
    return this.localAuth.isLoggedIn();
  }

  isMicrosoftLoggedIn(): boolean {
    return this.msalService.instance.getAllAccounts().length > 0;
  }

  isLoggedIn(): boolean {
    return this.isLocalSession() || this.isMicrosoftLoggedIn();
  }

  getAccount(): AccountInfo | null {
    const accounts = this.msalService.instance.getAllAccounts();
    return accounts.length > 0 ? accounts[0] : null;
  }

  getRoles(): string[] {
    if (this.isLocalSession()) {
      const role = this.localAuth.getUser()?.role;
      return role ? [role] : [];
    }
    const account = this.getAccount();
    if (!account) {
      return [];
    }
    const claims = account.idTokenClaims as any;
    if (claims && Array.isArray(claims.roles)) {
      return claims.roles;
    }
    return [];
  }

  getEmail(): string {
    if (this.isLocalSession()) {
      return this.localAuth.getUser()?.email ?? '';
    }
    return this.getAccount()?.username ?? '';
  }

  hasRole(role: string): boolean {
    return this.getRoles().includes(role);
  }

  isAdmin(): boolean {
    return this.hasRole('admin');
  }

  // Verifica el acceso al panel de admin en el frontend (control de navegacion basado en rol)
  canAccessAdmin(): boolean {
    return this.isLoggedIn() && this.isAdmin();
  }
}
