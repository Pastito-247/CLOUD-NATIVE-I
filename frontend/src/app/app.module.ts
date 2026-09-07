import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule } from '@angular/common/http';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { HomeComponent } from './home/home.component';
import { LoginComponent } from './login/login.component';
import { AdminComponent } from './admin/admin.component';

import { MSAL_INSTANCE, MsalService, MsalGuard, MsalInterceptor, MsalBroadcastService, MsalModule } from '@azure/msal-angular';
import { IPublicClientApplication, PublicClientApplication, InteractionType, BrowserCacheLocation } from '@azure/msal-browser';
import { HTTP_INTERCEPTORS } from '@angular/common/http';

import { environment } from '../environments/environment';

export function MSALInstanceFactory(): IPublicClientApplication {
  return new PublicClientApplication({
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
  });
}

// Endpoint -> scopes requeridos para ese endpoint.
// Los endpoints privados obtienen el access token con el scope 'access_as_user'.
// Los endpoints publicos (catálogo, categorías) quedan fuera del mapa para que
// no se les adjunte token innecesariamente.
export const protectedResourceMap: Map<string, Array<string>> = new Map([
  [environment.usersUrl, [environment.apiScope]]
]);

@NgModule({
  declarations: [
    AppComponent,
    HomeComponent,
    LoginComponent,
    AdminComponent
  ],
  imports: [
    BrowserModule,
    HttpClientModule,
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
    )
  ],
  providers: [
    {
      provide: HTTP_INTERCEPTORS,
      useClass: MsalInterceptor,
      multi: true
    },
    {
      provide: MSAL_INSTANCE,
      useFactory: MSALInstanceFactory
    },
    MsalService,
    MsalGuard,
    MsalBroadcastService
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }