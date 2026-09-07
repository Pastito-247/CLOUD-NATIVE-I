import { Component } from '@angular/core';
import { MsalService } from '@azure/msal-angular';
import { environment } from '../../environments/environment';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent {
  errorMessage = '';

  constructor(private msalService: MsalService) {}

  login(): void {
    this.msalService.loginPopup({
      scopes: [environment.apiScope]
    }).subscribe({
      next: (result) => {
        console.log('Login successful', result);
      },
      error: (error) => {
        console.error('Login error', error);
        this.errorMessage = error.message || 'Error al iniciar sesion';
      }
    });
  }
}