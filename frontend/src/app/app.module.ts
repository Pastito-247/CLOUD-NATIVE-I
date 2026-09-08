import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { HomeComponent } from './home/home.component';
import { LoginComponent } from './login/login.component';
import { AdminComponent } from './admin/admin.component';

<<<<<<< Updated upstream
import { MSAL_INSTANCE, MsalService, MsalGuard, MsalInterceptor, MsalBroadcastService, MsalModule } from '@azure/msal-angular';
import { IPublicClientApplication, PublicClientApplication, InteractionType, BrowserCacheLocation } from '@azure/msal-browser';
import { HTTP_INTERCEPTORS } from '@angular/common/http';

=======
import { MsalModule, MsalInterceptor, MsalGuard, MsalBroadcastService, MsalService } from '@azure/msal-angular';
import { BrowserCacheLocation, InteractionType, PublicClientApplication } from '@azure/msal-browser';
>>>>>>> Stashed changes
import { environment } from '../environments/environment';

const isIE = window.navigator.userAgent.indexOf('MSIE ') > -1 || window.navigator.userAgent.indexOf('Trident/') > -1;

export function MSALInstanceFactory(): PublicClientApplication {
  return new PublicClientApplication({
    auth: {
      clientId: environment.msalConfig.auth.clientId,
      authority: environment.msalConfig.auth.authority,
      redirectUri: environment.msalConfig.auth.redirectUri,
      postLogoutRedirectUri: environment.msalConfig.auth.postLogoutRedirectUri
    },
    cache: {
      cacheLocation: environment.msalConfig.cache.cacheLocation,
      storeAuthStateInCookie: environment.msalConfig.cache.storeAuthStateInCookie
    }
  });
}

<<<<<<< Updated upstream
// Endpoint -> scopes requeridos para ese endpoint.
// Los endpoints privados obtienen el access token con el scope 'access_as_user'.
// Los endpoints publicos (catálogo, categorías) quedan fuera del mapa para que
// no se les adjunte token innecesariamente.
export const protectedResourceMap: Map<string, Array<string>> = new Map([
  [environment.usersUrl, [environment.apiScope]]
]);
=======
export function MSALInterceptorConfigFactory() {
  return {
    interactionType: InteractionType.Popup,
    protectedResourceMap: new Map([
      [environment.apiGatewayUrl, environment.scopes],
      [environment.productsUrl, environment.scopes],
      [environment.categoriesUrl, environment.scopes],
      [environment.usersUrl, environment.scopes],
      [environment.ordersUrl, environment.scopes]
    ])
  };
}

export function MSALGuardConfigFactory() {
  return {
    interactionType: InteractionType.Popup,
    authRequest: {
      scopes: environment.scopes
    }
  };
}
>>>>>>> Stashed changes

@NgModule({
  declarations: [
    AppComponent,
    HomeComponent,
    LoginComponent,
    AdminComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    HttpClientModule,
<<<<<<< Updated upstream
    AppRoutingModule,
    // Configura el interceptor con el mapa de recursos protegidos y el tipo de interaccion
    MsalModule.forRoot(
      new PublicClientApplication({
        auth: {
          clientId: environment.msalConfig.auth.clientId,
          authority: environment.msalConfig.auth.authority,
          redirectUri: environment.msalConfig.auth.redirectUri
        },
        cache: {
          cacheLocation: BrowserCacheLocation.LocalStorage,
          storeAuthStateInCookie: false
        },
        system: {
          allowNativeBroker: false
        }
      }),
      {
        interactionType: InteractionType.Popup
      },
      {
        interactionType: InteractionType.Popup,
        protectedResourceMap
      }
=======
    FormsModule,
    MsalModule.forRoot(
      MSALInstanceFactory,
      MSALInterceptorConfigFactory,
      MSALGuardConfigFactory
>>>>>>> Stashed changes
    )
  ],
  providers: [
    MsalService,
    MsalBroadcastService,
    {
      provide: HTTP_INTERCEPTORS,
      useClass: MsalInterceptor,
      multi: true
    }
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }