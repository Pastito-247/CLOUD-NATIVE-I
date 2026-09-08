import { Component, OnInit } from '@angular/core';
import { MsalService } from '@azure/msal-angular';
<<<<<<< Updated upstream
import { AuthService } from './auth/auth.service';
import { environment } from '../environments/environment';
=======
import { environment } from '../../environments/environment';
>>>>>>> Stashed changes

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {
  title = 'E-Commerce Tech';
  isLoggedIn = false;
<<<<<<< Updated upstream
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
=======
  userRoles: string[] = [];
  userEmail = '';
  isAdmin = false;

  constructor(private msalService: MsalService) {}

  ngOnInit(): void {
    this.checkLoginStatus();
  }

  checkLoginStatus(): void {
    const accounts = this.msalService.instance.getAllAccounts();
    this.isLoggedIn = accounts.length > 0;
    
    if (this.isLoggedIn) {
      const account = accounts[0];
      this.userEmail = account.username || '';
      
      // Extract roles from ID token claims
      const idTokenClaims = account.idTokenClaims as any;
      this.userRoles = idTokenClaims?.roles || [];
      this.isAdmin = this.userRoles.includes('admin');
>>>>>>> Stashed changes
    }
  }

  login(): void {
<<<<<<< Updated upstream
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
=======
    if (environment.enableAuth) {
      this.msalService.loginPopup({
        scopes: environment.scopes
      }).subscribe(
        (response) => {
          console.log('Login success', response);
          this.checkLoginStatus();
        },
        (error) => {
          console.error('Login error', error);
        }
      );
    } else {
      // For local testing without auth
      this.isLoggedIn = true;
      this.isAdmin = true;
      this.userEmail = 'test@example.com';
    }
  }

  logout(): void {
    this.msalService.logoutPopup().subscribe(
      () => {
        console.log('Logout success');
        this.isLoggedIn = false;
        this.userRoles = [];
        this.userEmail = '';
        this.isAdmin = false;
      },
      (error) => {
        console.error('Logout error', error);
      }
    );
  }

  hasRole(role: string): boolean {
    return this.userRoles.includes(role);
>>>>>>> Stashed changes
  }
}