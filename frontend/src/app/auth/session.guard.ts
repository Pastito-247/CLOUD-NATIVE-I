import { Injectable } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { PublicAuthService } from './public-auth.service';

@Injectable({ providedIn: 'root' })
export class SessionGuard implements CanActivate {
  constructor(
    private authService: AuthService,
    private publicAuth: PublicAuthService,
    private router: Router
  ) {}

  canActivate(): boolean {
    if (this.authService.isLoggedIn() || this.publicAuth.isLoggedIn()) {
      return true;
    }
    this.router.navigate(['/publico']);
    return false;
  }
}
