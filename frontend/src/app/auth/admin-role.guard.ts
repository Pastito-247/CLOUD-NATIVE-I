import { Injectable } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { MsalService } from '@azure/msal-angular';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AdminRoleGuard implements CanActivate {

  constructor(
    private authService: AuthService,
    private msalService: MsalService,
    private router: Router
  ) {}

  canActivate(): boolean {
    // Si auth esta deshabilitado en local, permitir acceso sin token
    if (!environment.enableAuth) {
      return true;
    }

    // Si el usuario no esta logueado, MsalGuard redirige a login.
    // Este guard se encarga de verificar el rol una vez autenticado.
    if (!this.authService.isLoggedIn()) {
      return false;
    }

    // Verificar que el usuario tiene rol admin (claim 'roles' del ID token)
    if (this.authService.isAdmin()) {
      return true;
    }

    // Sin rol admin -> redirigir a home
    this.router.navigate(['/']);
    return false;
  }
}