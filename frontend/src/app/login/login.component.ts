import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { MsalService } from '@azure/msal-angular';
import { LocalAuthService } from '../auth/local-auth.service';
import { environment } from '../../environments/environment';

/**
 * Login con dos opciones:
 *  - Cuenta local (email + contrasena) creada en /register.
 *  - Cuenta Microsoft (Azure AD) via MSAL.
 */
@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent {

  email = '';
  password = '';
  loading = false;
  errorMessage = '';

  constructor(
    private msalService: MsalService,
    private localAuth: LocalAuthService,
    private router: Router
  ) {}

  loginLocal(): void {
    this.errorMessage = '';

    if (!this.email.trim() || !this.password) {
      this.errorMessage = 'Ingresa tu email y contrasena.';
      return;
    }

    this.loading = true;

    this.localAuth.login(this.email.trim(), this.password).subscribe({
      next: () => {
        this.loading = false;
        this.router.navigate(['/']);
      },
      error: (error) => {
        this.loading = false;
        this.errorMessage =
          error?.error?.detail || 'Email o contrasena incorrectos.';
      }
    });
  }

  loginMicrosoft(): void {
    this.errorMessage = '';
    this.msalService.loginRedirect({
      scopes: environment.scopes
    });
  }
}
