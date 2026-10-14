import { Injectable } from '@angular/core';
import {
  HttpEvent,
  HttpHandler,
  HttpInterceptor,
  HttpRequest
} from '@angular/common/http';
import { MsalService } from '@azure/msal-angular';
import { Observable, from } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { LocalAuthService } from './local-auth.service';

/**
 * Adjunta el token adecuado a las peticiones a las APIs protegidas:
 *
 * - Si hay sesion local, usa ese JWT.
 * - Si no, y hay cuenta de Microsoft, pide un token silencioso a MSAL.
 * - Si no hay ninguna sesion, deja pasar la peticion (el backend responde
 *   401 sin abrir ningun popup, a diferencia del MsalInterceptor original).
 *
 * Los endpoints publicos (/api/public/...) nunca se tocan.
 */
@Injectable()
export class AuthInterceptor implements HttpInterceptor {

  private readonly protectedUrls: string[] = [
    environment.productsUrl,
    environment.categoriesUrl,
    environment.usersUrl,
    environment.ordersUrl,
    environment.paymentsUrl,
    environment.rabbitUrl
  ];

  constructor(
    private msalService: MsalService,
    private localAuth: LocalAuthService
  ) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    if (!this.requiresToken(req.url)) {
      return next.handle(req);
    }

    const localToken = this.localAuth.getToken();
    if (localToken) {
      return next.handle(this.withBearer(req, localToken));
    }

    const account =
      this.msalService.instance.getActiveAccount()
      || this.msalService.instance.getAllAccounts()[0];

    if (!account) {
      return next.handle(req);
    }

    return this.msalService
      .acquireTokenSilent({ scopes: environment.scopes, account })
      .pipe(
        switchMap(result => next.handle(this.withBearer(req, result.accessToken))),
        // Si el token silencioso falla, se deja pasar la peticion sin token.
        catchError(() => next.handle(req))
      );
  }

  private requiresToken(url: string): boolean {
    if (url.includes('/api/public/')) {
      return false;
    }
    return this.protectedResourcePrefixes().some(prefix => url.startsWith(prefix));
  }

  private protectedResourcePrefixes(): string[] {
    return [
      environment.productsUrl,
      environment.categoriesUrl,
      environment.usersUrl,
      environment.ordersUrl,
      environment.paymentsUrl,
      environment.rabbitUrl
    ];
  }

  private withBearer(req: HttpRequest<any>, token: string): HttpRequest<any> {
    return req.clone({
      setHeaders: { Authorization: `Bearer ${token}` }
    });
  }
}
