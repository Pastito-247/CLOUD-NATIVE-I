import { Injectable } from '@angular/core';
import {
  AuthenticationDetails,
  CognitoUser,
  CognitoUserAttribute,
  CognitoUserPool
} from 'amazon-cognito-identity-js';
import { environment } from '../../environments/environment';

const POOL_DATA = {
  UserPoolId: environment.cognito.userPoolId,
  ClientId: environment.cognito.clientId
};

const STORAGE_KEY = 'tuki_cognito_session';

@Injectable({ providedIn: 'root' })
export class PublicAuthService {
  private userPool = new CognitoUserPool(POOL_DATA);

  isConfigured(): boolean {
    return !!POOL_DATA.UserPoolId && !!POOL_DATA.ClientId
      && !POOL_DATA.ClientId.startsWith('TU_');
  }

  signUp(email: string, password: string, name: string): Promise<any> {
    return new Promise((resolve, reject) => {
      const attributes = [
        new CognitoUserAttribute({ Name: 'email', Value: email }),
        new CognitoUserAttribute({ Name: 'name', Value: name })
      ];
      this.userPool.signUp(email, password, attributes, [], (err, result) => {
        if (err) reject(err);
        else resolve(result);
      });
    });
  }

  confirmSignUp(email: string, code: string): Promise<string> {
    return new Promise((resolve, reject) => {
      const user = new CognitoUser({ Username: email, Pool: this.userPool });
      user.confirmRegistration(code, true, (err, result) => {
        if (err) reject(err);
        else resolve(result);
      });
    });
  }

  signIn(email: string, password: string): Promise<any> {
    return new Promise((resolve, reject) => {
      const user = new CognitoUser({ Username: email, Pool: this.userPool });
      const details = new AuthenticationDetails({ Username: email, Password: password });
      user.authenticateUser(details, {
        onSuccess: (session) => {
          const tokens = {
            accessToken: session.getAccessToken().getJwtToken(),
            idToken: session.getIdToken().getJwtToken(),
            refreshToken: session.getRefreshToken().getToken(),
            email: session.getIdToken().payload['email'] || email
          };
          localStorage.setItem(STORAGE_KEY, JSON.stringify(tokens));
          resolve(tokens);
        },
        onFailure: (err) => reject(err),
        newPasswordRequired: () => reject(new Error('Se requiere cambiar la contraseña'))
      });
    });
  }

  forgotPassword(email: string): Promise<any> {
    return new Promise((resolve, reject) => {
      const user = new CognitoUser({ Username: email, Pool: this.userPool });
      user.forgotPassword({
        onSuccess: (data) => resolve(data),
        onFailure: (err) => reject(err)
      });
    });
  }

  confirmForgotPassword(email: string, code: string, newPassword: string): Promise<any> {
    return new Promise((resolve, reject) => {
      const user = new CognitoUser({ Username: email, Pool: this.userPool });
      user.confirmPassword(code, newPassword, {
        onSuccess: () => resolve(true),
        onFailure: (err) => reject(err)
      });
    });
  }

  signOut(): void {
    const tokens = this.getTokens();
    if (tokens) {
      const user = new CognitoUser({ Username: tokens.email, Pool: this.userPool });
      user.signOut();
    }
    localStorage.removeItem(STORAGE_KEY);
  }

  getTokens(): any | null {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw);
    } catch {
      return null;
    }
  }

  getAccessToken(): string | null {
    return this.getTokens()?.accessToken || null;
  }

  getIdToken(): string | null {
    return this.getTokens()?.idToken || null;
  }

  getEmail(): string | null {
    return this.getTokens()?.email || null;
  }

  isLoggedIn(): boolean {
    return !!this.getAccessToken();
  }
}
