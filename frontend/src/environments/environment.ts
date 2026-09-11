// Archivo GENERADO por scripts/generate-env.js.
// NO editar a mano - los valores provienen de .env en la raiz.
export const environment = {
  production: false,

  productsUrl: 'http://localhost:8081/api/products',
  categoriesUrl: 'http://localhost:8082/api/categories',
  usersUrl: 'http://localhost:8083/api/users',
  ordersUrl: 'http://localhost:8084/api/orders',

  enableAuth: true,

  authority: 'https://login.microsoftonline.com/e3e92dfe-ea59-4c42-a539-90e6fea570b6/v2.0',
  apiUri: 'api://tuki-tech-api',
  apiScope: 'api://tuki-tech-api/access_as_user',
  scopes: ['api://tuki-tech-api/access_as_user'],

  msalConfig: {
    auth: {
      clientId: '66c0f84b-3701-42fb-9189-3f4092cba201',
      authority: 'https://login.microsoftonline.com/e3e92dfe-ea59-4c42-a539-90e6fea570b6/v2.0',
      redirectUri: 'http://localhost:4200',
      postLogoutRedirectUri: 'http://localhost:4200'
    },
    cache: {
      cacheLocation: 'localStorage',
      storeAuthStateInCookie: false
    }
  }
};
