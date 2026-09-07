import { Component } from '@angular/core';
import { MsalService } from '@azure/msal-angular';
import { AuthService } from './auth/auth.service';
import { environment } from '../environments/environment';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {
  title = 'E-Commerce Tech';
  isLoggedIn = false;
  isAdminUser = false;
  userName = '';

  constructor(
    private msalService: MsalService,
    private authService: AuthService
  ) {
    this.checkLogin();
  }

  checkLogin(): void {
    this.isLoggedIn = this.authService.isLoggedIn();
    if (this.isLoggedIn) {
      const account = this.authService.getAccount();
      this.userName = account ? (account.name || account.username) : '';
      this.isAdminUser = this.authService.isAdmin();
    } else {
      this.userName = '';
      this.isAdminUser = false;
    }
  }

  login(): void {
    if (!environment.enableAuth) {
      return;
    }
    this.msalService.loginPopup({
      scopes: [environment.apiScope]
    }).subscribe({
      next: () => this.checkLogin(),
      error: (error) => console.error('Login error', error)
    });
  }

  logout(): void {
    this.msalService.logoutPopup().subscribe({
      next: () => {
        this.isLoggedIn = false;
        this.isAdminUser = false;
        this.userName = '';
      }
    });
  }
}