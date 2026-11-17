import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PublicAuthService } from './public-auth.service';

@Injectable()
export class CognitoInterceptor implements HttpInterceptor {
  constructor(private publicAuth: PublicAuthService) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const token = this.publicAuth.getAccessToken();
    const isCustomerRoute = req.url.includes('/api/customer/');

    if (token && isCustomerRoute) {
      const cloned = req.clone({
        setHeaders: { Authorization: `Bearer ${token}` }
      });
      return next.handle(cloned);
    }
    return next.handle(req);
  }
}
