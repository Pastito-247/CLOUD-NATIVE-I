import { Component } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';

/**
 * Registro de cuentas desde la pagina.
 *
 * Llama al endpoint publico del users-service (no requiere JWT), que crea el
 * perfil con contrasena (BCrypt) y publica user.registered para generar el
 * correo de bienvenida.
 */
@Component({
  selector: 'app-register',
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.css']
})
export class RegisterComponent {

  name = '';
  email = '';
  password = '';
  role = 'customer';

  loading = false;
  errorMessage = '';
  successMessage = '';

  constructor(private http: HttpClient) {}

  submit(): void {
    this.errorMessage = '';
    this.successMessage = '';

    if (!this.name.trim() || !this.email.trim()) {
      this.errorMessage = 'Completa tu nombre y tu email.';
      return;
    }

    if (!this.password || this.password.length < 6) {
      this.errorMessage = 'La contraseña debe tener al menos 6 caracteres.';
      return;
    }

    this.loading = true;

    const body = {
      name: this.name.trim(),
      email: this.email.trim(),
      password: this.password,
      role: this.role
    };

    this.http.post<any>(`${environment.usersPublicUrl}/register`, body).subscribe({
      next: () => {
        this.loading = false;
        this.successMessage =
          'Cuenta creada. Ya puedes iniciar sesión con tu email y contraseña.';
        this.name = '';
        this.email = '';
        this.password = '';
        this.role = 'customer';
      },
      error: (error) => {
        console.error('Error registrando cuenta:', error);
        this.loading = false;
        this.errorMessage =
          error?.error?.detail ||
          'No se pudo crear la cuenta. Verifica los datos e intenta de nuevo.';
      }
    });
  }
}
