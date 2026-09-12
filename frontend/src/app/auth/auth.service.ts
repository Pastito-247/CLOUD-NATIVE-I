import { Injectable } from '@angular/core';
import { MsalService } from '@azure/msal-angular';
import { AccountInfo } from '@azure/msal-browser';

@Injectable({ providedIn: 'root' })
export class AuthService {

  constructor(private msalService: MsalService) {}

  isLoggedIn(): boolean {
    return this.msalService.instance.getAllAccounts().length > 0;
  }

  getAccount(): AccountInfo | null {
    const accounts = this.msalService.instance.getAllAccounts();
    return accounts.length > 0 ? accounts[0] : null;
  }

  getRoles(): string[] {
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