import { Component, OnInit, inject, PLATFORM_ID, ChangeDetectorRef } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { PublicClientApplication, AuthenticationResult } from '@azure/msal-browser';
import { msalConfig, loginRequest } from './auth-config';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit {
  private platformId = inject(PLATFORM_ID);
  private cdr = inject(ChangeDetectorRef);
  private msalInstance!: PublicClientApplication;

  isLoggedIn = false;
  userToken: string = '';
  userName: string = '';

  async ngOnInit() {
    if (!isPlatformBrowser(this.platformId)) return;

    this.msalInstance = new PublicClientApplication(msalConfig);
    await this.msalInstance.initialize();

    try {
      const response = await this.msalInstance.handleRedirectPromise();
      
      if (response) {
        this.setSession(response);
        return;
      }

      const accounts = this.msalInstance.getAllAccounts();
      if (accounts.length > 0) {
        this.msalInstance.setActiveAccount(accounts[0]);
        this.userName = accounts[0].username;
        this.isLoggedIn = true;
        await this.acquireTokenSilently();
      }
    } catch (error) {
      console.error('Error durante la autenticación:', error);
    } finally {
      this.cdr.detectChanges();
    }
  }

  async login() {
    if (!this.msalInstance) return;
    await this.msalInstance.loginRedirect(loginRequest);
  }

  async logout() {
    if (!this.msalInstance) return;
    await this.msalInstance.logoutRedirect();
  }

  private setSession(result: AuthenticationResult) {
    this.msalInstance.setActiveAccount(result.account);
    this.isLoggedIn = true;
    this.userName = result.account.username;
    this.userToken = result.idToken || result.accessToken;
    this.cdr.detectChanges();
  }

  private async acquireTokenSilently() {
    const activeAccount = this.msalInstance.getActiveAccount();
    if (!activeAccount) return;

    try {
      const result = await this.msalInstance.acquireTokenSilent({
        ...loginRequest,
        account: activeAccount
      });
      this.userToken = result.idToken || result.accessToken;
    } catch (e) {
      console.warn('Renovación silenciosa no requerida o fallida:', e);
    } finally {
      this.cdr.detectChanges();
    }
  }
}