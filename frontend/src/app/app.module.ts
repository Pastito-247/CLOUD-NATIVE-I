import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { FormsModule } from '@angular/forms';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { HomeComponent } from './home/home.component';
import { LoginComponent } from './login/login.component';
import { RegisterComponent } from './register/register.component';
import { AdminComponent } from './admin/admin.component';
import { MyOrdersComponent } from './my-orders/my-orders.component';
import { RabbitmqAdminComponent } from './rabbitmq-admin/rabbitmq-admin.component';

import {
  MsalModule,
  MsalBroadcastService,
  MsalService
} from '@azure/msal-angular';

import {
  BrowserCacheLocation,
  InteractionType,
  PublicClientApplication
} from '@azure/msal-browser';

import type {
  MsalGuardConfiguration,
  MsalInterceptorConfiguration
} from '@azure/msal-angular';

import { environment } from '../environments/environment';
import { AuthInterceptor } from './auth/auth.interceptor';

const msalInstance = new PublicClientApplication({
  auth: {
    clientId: environment.msalConfig.auth.clientId,
    authority: environment.msalConfig.auth.authority,
    redirectUri: environment.msalConfig.auth.redirectUri,
    postLogoutRedirectUri: environment.msalConfig.auth.postLogoutRedirectUri,
    navigateToLoginRequestUrl: true
  },

  cache: {
    cacheLocation: BrowserCacheLocation.LocalStorage,
    storeAuthStateInCookie: false
  }
});

/*
 * El Admin está protegido.
 * Si alguien intenta entrar a /admin sin estar autenticado,
 * MSAL lo enviará a Microsoft para iniciar sesión.
 */
const msalGuardConfig: MsalGuardConfiguration = {
  interactionType: InteractionType.Redirect,

  authRequest: {
    scopes: environment.scopes
  }
};

/*
 * MsalModule.forRoot exige una config de interceptor aunque no usemos el
 * MsalInterceptor (lo reemplazamos por AuthInterceptor para soportar tambien
 * el login local). El mapa de recursos se mantiene por compatibilidad.
 */
const msalInterceptorConfig: MsalInterceptorConfiguration = {
  interactionType: InteractionType.Popup,

  protectedResourceMap: new Map<string, string[]>([
    [environment.productsUrl, environment.scopes],
    [environment.categoriesUrl, environment.scopes],
    [environment.usersUrl, environment.scopes],
    [environment.ordersUrl, environment.scopes],
    [environment.paymentsUrl, environment.scopes],
    [environment.rabbitUrl, environment.scopes]
  ])
};

@NgModule({
  declarations: [
    AppComponent,
    HomeComponent,
    LoginComponent,
    RegisterComponent,
    AdminComponent,
    MyOrdersComponent,
    RabbitmqAdminComponent
  ],

  imports: [
    BrowserModule,
    AppRoutingModule,
    HttpClientModule,
    FormsModule,

    MsalModule.forRoot(
      msalInstance,
      msalGuardConfig,
      msalInterceptorConfig
    )
  ],

  providers: [
    MsalService,
    MsalBroadcastService,

    {
      provide: HTTP_INTERCEPTORS,
      useClass: AuthInterceptor,
      multi: true
    }
  ],

  bootstrap: [
    AppComponent
  ]
})
export class AppModule {}