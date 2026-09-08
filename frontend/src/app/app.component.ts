import { Component, OnInit } from '@angular/core';
import { MsalService } from '@azure/msal-angular';
import { environment } from '../environments/environment';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {
  title = 'Tuki-Tech';
  isLoggedIn = false;
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
    }
  }

  login(): void {
    this.msalService.loginPopup({
      scopes: ['openid', 'profile']
    }).subscribe(
      (response) => {
        console.log('Login success', response);
        this.checkLoginStatus();
      },
      (error) => {
        console.error('Login error', error);
      }
    );
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
  }
}