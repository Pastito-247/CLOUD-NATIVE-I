import { Injectable } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';

/**
 * Requiere sesion activa y rol 'admin' (claim del token local o de Microsoft).
 * Si no hay sesion redirige a /login; si no es admin redirige a home.
 */
@Injectable({ providedIn: 'root' })
export class AdminRoleGuard implements CanActivate {

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  canActivate(): boolean {
    // Si auth esta deshabilitado en local, permitir acceso sin token
    if (!environment.enableAuth) {
      return true;
    }

    if (!this.authService.isLoggedIn()) {
      this.router.navigate(['/login']);
      return false;
    }

    // Verificar que el usuario tiene rol admin
    if (this.authService.isAdmin()) {
      return true;
    }

    // Sin rol admin -> redirigir a home
    this.router.navigate(['/']);
    return false;
  }
}
