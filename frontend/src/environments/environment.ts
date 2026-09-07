// Archivo GENERADO por scripts/generate-env.js.
// NO editar a mano - los valores provienen de .env en la raiz.
export const environment = {
  production: false,

  productsUrl: 'http://localhost:8081/api/products',
  categoriesUrl: 'http://localhost:8082/api/categories',
  usersUrl: 'http://localhost:8083/api/users',
  ordersUrl: 'http://localhost:8084/api/orders',

  enableAuth: true,

  authority: 'https://login.microsoftonline.com/TU_TENANT_ID/v2.0',
  apiUri: 'api://TU_CLIENT_ID',
  apiScope: 'api://TU_CLIENT_ID/access_as_user',

  msalConfig: {
    auth: {
      clientId: 'TU_CLIENT_ID',
      authority: 'https://login.microsoftonline.com/TU_TENANT_ID/v2.0',
      redirectUri: 'http://localhost:4200'
    },
    cache: {
      cacheLocation: 'localStorage',
      storeAuthStateInCookie: false
    }
  }
};
