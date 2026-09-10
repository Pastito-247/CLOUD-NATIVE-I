// Archivo GENERADO por scripts/generate-env.js.
// NO editar a mano - los valores provienen de .env en la raiz.
// Para produccion ajusta API_GATEWAY_URL si usas un API Gateway.
export const environment = {
  production: true,

  apiGatewayUrl: 'https://your-api-gateway-url.execute-api.region.amazonaws.com/prod',
  productsUrl: 'https://your-api-gateway-url.execute-api.region.amazonaws.com/prod/products',
  categoriesUrl: 'https://your-api-gateway-url.execute-api.region.amazonaws.com/prod/categories',
  usersUrl: 'https://your-api-gateway-url.execute-api.region.amazonaws.com/prod/users',
  ordersUrl: 'https://your-api-gateway-url.execute-api.region.amazonaws.com/prod/orders',

  enableAuth: true,

  authority: 'https://login.microsoftonline.com/e3e92dfe-ea59-4c42-a539-90e6fea570b6/v2.0',
  apiUri: 'api://66c0f84b-3701-42fb-9189-3f4092cba201',
  apiScope: 'api://66c0f84b-3701-42fb-9189-3f4092cba201/access_as_user',
  scopes: ['api://66c0f84b-3701-42fb-9189-3f4092cba201/access_as_user'],

  msalConfig: {
    auth: {
      clientId: '66c0f84b-3701-42fb-9189-3f4092cba201',
      authority: 'https://login.microsoftonline.com/e3e92dfe-ea59-4c42-a539-90e6fea570b6/v2.0',
      redirectUri: 'https://your-frontend-domain.com',
      postLogoutRedirectUri: 'https://your-frontend-domain.com'
    },
    cache: {
      cacheLocation: 'localStorage',
      storeAuthStateInCookie: false
    }
  }
};
