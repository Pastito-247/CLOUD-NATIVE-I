import { Component } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';

/**
 * Registro de cuentas desde la pagina.
 *
 * Llama al endpoint publico del users-service (no requiere JWT), que crea el
 * perfil y publica user.registered para generar el correo de bienvenida.
 */
@Component({
  selector: 'app-register',
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.css']
})
export class RegisterComponent {

  name = '';
  email = '';
  role = 'customer';

  loading = false;
  errorMessage = '';
  successMessage = '';

  constructor(private http: HttpClient) {}

  submit(): void {
    this.errorMessage = '';
    this.successMessage = '';

    if (!this.name.trim() || !this.email.trim()) {
      this.errorMessage = 'Nombre y email son obligatorios.';
      return;
    }

    this.loading = true;

    const body = {
      name: this.name.trim(),
      email: this.email.trim(),
      role: this.role
    };

    this.http.post<any>(`${environment.usersPublicUrl}/register`, body).subscribe({
      next: (user) => {
        this.loading = false;
        this.successMessage =
          `Cuenta creada: ${user.email}. Te enviamos un correo de bienvenida.`;
        this.name = '';
        this.email = '';
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
