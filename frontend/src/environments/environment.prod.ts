export const environment = {
  production: true,
  apiGatewayUrl: 'https://your-api-gateway-url.execute-api.region.amazonaws.com/prod',
  msalConfig: {
    auth: {
      clientId: 'YOUR_CLIENT_ID',
      authority: 'https://login.microsoftonline.com/YOUR_TENANT_ID',
      redirectUri: 'https://your-frontend-domain.com'
    },
    cache: {
      cacheLocation: 'localStorage',
      storeAuthStateInCookie: false
    }
  }
};
