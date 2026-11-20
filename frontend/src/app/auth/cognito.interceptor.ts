import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { from, Observable, switchMap } from 'rxjs';
import { PublicAuthService } from './public-auth.service';

@Injectable()
export class CognitoInterceptor implements HttpInterceptor {
  constructor(private publicAuth: PublicAuthService) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    if (!req.url.includes('/api/customer/')) {
      return next.handle(req);
    }

    return from(this.publicAuth.getValidIdToken()).pipe(
      switchMap((token) => {
        if (!token) return next.handle(req);
        return next.handle(req.clone({
          setHeaders: { Authorization: `Bearer ${token}` }
        }));
      })
    );
  }
}
