// Archivo GENERADO por scripts/generate-env.js.
// NO editar a mano - los valores provienen de .env en la raiz.
export const environment = {
  production: false,
<<<<<<< Updated upstream

=======
  // API Gateway URL for production
  apiGatewayUrl: 'https://YOUR_API_GATEWAY_URL.execute-api.region.amazonaws.com/prod',
  // Direct microservice URLs for local testing
>>>>>>> Stashed changes
  productsUrl: 'http://localhost:8081/api/products',
  categoriesUrl: 'http://localhost:8082/api/categories',
  usersUrl: 'http://localhost:8083/api/users',
  ordersUrl: 'http://localhost:8084/api/orders',
<<<<<<< Updated upstream

  enableAuth: true,

  authority: 'https://login.microsoftonline.com/TU_TENANT_ID/v2.0',
  apiUri: 'api://TU_CLIENT_ID',
  apiScope: 'api://TU_CLIENT_ID/access_as_user',

  msalConfig: {
    auth: {
      clientId: 'TU_CLIENT_ID',
      authority: 'https://login.microsoftonline.com/TU_TENANT_ID/v2.0',
      redirectUri: 'http://localhost:4200'
=======
  // EntraID (Azure AD) Configuration
  msalConfig: {
    auth: {
      clientId: 'YOUR_CLIENT_ID',
      authority: 'https://login.microsoftonline.com/YOUR_TENANT_ID',
      redirectUri: 'http://localhost:4200',
      postLogoutRedirectUri: 'http://localhost:4200'
>>>>>>> Stashed changes
    },
    cache: {
      cacheLocation: 'localStorage',
      storeAuthStateInCookie: false
    }
<<<<<<< Updated upstream
  }
=======
  },
  // Enable authentication
  enableAuth: true,
  // Scopes for API access
  scopes: ['api://YOUR_API_ID/access_as_user']
>>>>>>> Stashed changes
};
