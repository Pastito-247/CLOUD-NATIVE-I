import { Component } from '@angular/core';
import { MsalService } from '@azure/msal-angular';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent {
  constructor(private msalService: MsalService) {}

  login(): void {
    this.msalService.loginPopup().subscribe({
      next: (result) => {
        console.log('Login successful', result);
      },
      error: (error) => {
        console.error('Login error', error);
      }
    });
  }
}
