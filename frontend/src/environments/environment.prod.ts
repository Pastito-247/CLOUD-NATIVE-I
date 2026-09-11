// Archivo GENERADO por scripts/generate-env.js.
// NO editar a mano - los valores provienen de .env en la raiz.
// CONFIGURACION DE PRODUCCION (GitHub Pages + backend HTTPS).
export const environment = {
  production: true,

  apiGatewayUrl: 'https://backend.MIDOMINIO.com',
  productsUrl: 'https://backend.MIDOMINIO.com/api/products',
  categoriesUrl: 'https://backend.MIDOMINIO.com/api/categories',
  usersUrl: 'https://backend.MIDOMINIO.com/api/users',
  ordersUrl: 'https://backend.MIDOMINIO.com/api/orders',

  enableAuth: true,

  authority: 'https://login.microsoftonline.com/e3e92dfe-ea59-4c42-a539-90e6fea570b6/v2.0',
  apiUri: 'api://tuki-tech-api',
  apiScope: 'api://tuki-tech-api/access_as_user',
  scopes: ['api://tuki-tech-api/access_as_user'],

  msalConfig: {
    auth: {
      clientId: '66c0f84b-3701-42fb-9189-3f4092cba201',
      authority: 'https://login.microsoftonline.com/e3e92dfe-ea59-4c42-a539-90e6fea570b6/v2.0',
      redirectUri: 'https://pastito-247.github.io/CLOUD-NATIVE-I',
      postLogoutRedirectUri: 'https://pastito-247.github.io/CLOUD-NATIVE-I'
    },
    cache: {
      cacheLocation: 'localStorage',
      storeAuthStateInCookie: false
    }
  }
};
