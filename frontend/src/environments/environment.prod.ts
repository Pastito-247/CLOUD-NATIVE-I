// Archivo GENERADO por scripts/generate-env.js.
// NO editar a mano - los valores provienen de .env en la raiz.
// Para produccion ajusta API_GATEWAY_URL si usas un API Gateway.
export const environment = {
  production: true,

  apiGatewayUrl: 'https://your-api-gateway-url.execute-api.region.amazonaws.com/prod',

  enableAuth: true,

  authority: 'https://login.microsoftonline.com/TU_TENANT_ID/v2.0',
  apiUri: 'api://TU_CLIENT_ID',
  apiScope: 'api://TU_CLIENT_ID/access_as_user',

  msalConfig: {
    auth: {
      clientId: 'TU_CLIENT_ID',
      authority: 'https://login.microsoftonline.com/TU_TENANT_ID/v2.0',
      redirectUri: 'https://your-frontend-domain.com'
    },
    cache: {
      cacheLocation: 'localStorage',
      storeAuthStateInCookie: false
    }
  }
};
