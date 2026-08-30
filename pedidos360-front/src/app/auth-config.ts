import { Configuration, BrowserCacheLocation } from '@azure/msal-browser';

export const msalConfig: Configuration = {
  auth: {
    clientId: '75afb8c6-32f4-44ac-b16d-7f01872441cd', 
    authority: 'https://login.microsoftonline.com/testpasto123.onmicrosoft.com', 
    redirectUri: 'http://localhost:4200'
  },
  cache: {
    cacheLocation: BrowserCacheLocation.LocalStorage
  }
};

export const loginRequest = {
  scopes: ['User.Read']
};