import { Component, OnDestroy, OnInit } from '@angular/core';
import {
  MsalBroadcastService,
  MsalService
} from '@azure/msal-angular';

import {
  AuthenticationResult,
  EventMessage,
  EventType,
  InteractionStatus
} from '@azure/msal-browser';

import { Subject } from 'rxjs';
import { filter, takeUntil } from 'rxjs/operators';

import { environment } from '../environments/environment';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit, OnDestroy {

  title = 'Tuki-Tech';

  isLoggedIn = false;
  userRoles: string[] = [];
  userEmail = '';
  isAdmin = false;

  private readonly destroying$ = new Subject<void>();

  constructor(
    private msalService: MsalService,
    private msalBroadcastService: MsalBroadcastService
  ) {}

  ngOnInit(): void {

    // Procesar el resultado del loginRedirect
    this.msalService.handleRedirectObservable().subscribe({
      next: (result: AuthenticationResult | null) => {

        if (result?.account) {
          this.msalService.instance.setActiveAccount(result.account);
        }

        this.checkLoginStatus();
      },

      error: (error) => {
        console.error('MSAL redirect error:', error);
      }
    });

    // Escuchar cambios de autenticación
    this.msalBroadcastService.msalSubject$
      .pipe(
        filter(
          (msg: EventMessage) =>
            msg.eventType === EventType.LOGIN_SUCCESS ||
            msg.eventType === EventType.ACCOUNT_ADDED ||
            msg.eventType === EventType.ACCOUNT_REMOVED ||
            msg.eventType === EventType.LOGOUT_SUCCESS
        ),
        takeUntil(this.destroying$)
      )
      .subscribe((msg: EventMessage) => {

        if (msg.eventType === EventType.LOGIN_SUCCESS) {
          const result = msg.payload as AuthenticationResult;

          if (result.account) {
            this.msalService.instance.setActiveAccount(result.account);
          }
        }

        this.checkLoginStatus();
      });

    // Esperar a que MSAL termine cualquier interacción
    this.msalBroadcastService.inProgress$
      .pipe(
        filter(
          (status: InteractionStatus) =>
            status === InteractionStatus.None
        ),
        takeUntil(this.destroying$)
      )
      .subscribe(() => {
        this.checkLoginStatus();
      });
  }

  checkLoginStatus(): void {

    const accounts = this.msalService.instance.getAllAccounts();

    this.isLoggedIn = accounts.length > 0;

    if (!this.isLoggedIn) {
      this.userRoles = [];
      this.userEmail = '';
      this.isAdmin = false;
      return;
    }

    let activeAccount =
      this.msalService.instance.getActiveAccount();

    if (!activeAccount) {
      activeAccount = accounts[0];
      this.msalService.instance.setActiveAccount(activeAccount);
    }

    this.userEmail = activeAccount.username || '';

    const idTokenClaims = activeAccount.idTokenClaims as any;

    this.userRoles = Array.isArray(idTokenClaims?.roles)
      ? idTokenClaims.roles
      : [];

    this.isAdmin = this.userRoles.includes('admin');
  }

  login(): void {

    this.msalService.loginRedirect({
      scopes: environment.scopes
    });
  }

  logout(): void {

    this.msalService.logoutRedirect({
      postLogoutRedirectUri:
        environment.msalConfig.auth.postLogoutRedirectUri
    });
  }

  hasRole(role: string): boolean {
    return this.userRoles.includes(role);
  }

  ngOnDestroy(): void {
    this.destroying$.next();
    this.destroying$.complete();
  }
}