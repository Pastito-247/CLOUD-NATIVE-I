import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { PublicAuthService } from '../auth/public-auth.service';

@Component({
  selector: 'app-public-auth',
  templateUrl: './public-auth.component.html',
  styleUrls: ['./public-auth.component.css']
})
export class PublicAuthComponent {
  mode: 'login' | 'register' | 'confirm' | 'forgot' | 'forgot-confirm' = 'login';
  email = '';
  password = '';
  name = '';
  code = '';
  newPassword = '';
  message = '';
  error = '';
  loading = false;

  constructor(public publicAuth: PublicAuthService, private router: Router) {}

  submitRegister(): void {
    this.loading = true;
    this.error = '';
    this.publicAuth.signUp(this.email, this.password, this.name).then(() => {
      this.mode = 'confirm';
      this.message = 'Revisa tu correo y usa el código de confirmación.';
      this.loading = false;
    }).catch(err => {
      this.error = err.message || 'No se pudo registrar';
      this.loading = false;
    });
  }

  submitConfirm(): void {
    this.loading = true;
    this.error = '';
    this.publicAuth.confirmSignUp(this.email, this.code).then(() => {
      this.message = 'Cuenta confirmada. Ya puedes iniciar sesión.';
      this.mode = 'login';
      this.loading = false;
    }).catch(err => {
      this.error = err.message || 'Código inválido';
      this.loading = false;
    });
  }

  submitLogin(): void {
    this.loading = true;
    this.error = '';
    this.publicAuth.signIn(this.email, this.password).then(() => {
      this.loading = false;
      this.router.navigate(['/my-orders']);
    }).catch(err => {
      this.error = err.message || 'No se pudo iniciar sesión';
      this.loading = false;
    });
  }

  submitForgot(): void {
    this.loading = true;
    this.error = '';
    this.publicAuth.forgotPassword(this.email).then(() => {
      this.mode = 'forgot-confirm';
      this.message = 'Enviamos un código a tu correo.';
      this.loading = false;
    }).catch(err => {
      this.error = err.message || 'No se pudo solicitar el código';
      this.loading = false;
    });
  }

  submitForgotConfirm(): void {
    this.loading = true;
    this.error = '';
    this.publicAuth.confirmForgotPassword(this.email, this.code, this.newPassword).then(() => {
      this.message = 'Contraseña actualizada. Inicia sesión.';
      this.mode = 'login';
      this.loading = false;
    }).catch(err => {
      this.error = err.message || 'No se pudo cambiar la contraseña';
      this.loading = false;
    });
  }
}
