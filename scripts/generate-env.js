// ============================================================
// Genera frontend/src/environments/environment.ts y environment.prod.ts
// a partir del archivo .env de la raiz del proyecto (sin depender de dotenv).
//
// Uso:  node scripts/generate-env.js
// ============================================================
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const ENV_FILE = path.join(ROOT, '.env');
const FRONTEND_ENV_DIR = path.join(ROOT, 'frontend', 'src', 'environments');

// --- Lee y parsea el archivo .env (formato simple KEY=VALUE) ---
function loadEnv(filePath) {
  const env = {};
  if (!fs.existsSync(filePath)) {
    console.error(`No se encontro el archivo .env en: ${filePath}`);
    console.error('Copia el contenido y crealo, o renombra .env.example a .env');
    process.exit(1);
  }
  const content = fs.readFileSync(filePath, 'utf8');
  for (const rawLine of content.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) continue; // vacio o comentario
    const eq = line.indexOf('=');
    if (eq === -1) continue;
    const key = line.slice(0, eq).trim();
    let value = line.slice(eq + 1).trim();
    // Quita comillas simples o dobles
    if ((value.startsWith('"') && value.endsWith('"')) ||
        (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    if (key) env[key] = value;
  }
  return env;
}

// --- Detecta valores sin rellenar ---
function hasPlaceholders(env) {
  return Object.values(env).some(v => typeof v === 'string' && (v.includes('TU_') || v.toUpperCase().includes('YOUR_')));
}

const env = loadEnv(ENV_FILE);

if (hasPlaceholders(env)) {
  console.warn('ADVERTENCIA: El archivo .env todavia contiene valores placeholder (TU_CLIENT_ID / TU_TENANT_ID / YOUR_).');
  console.warn('La autenticacion no funcionara hasta rellenarlos con tu App Registration real.\n');
}

// --- Escribe environment.ts (desarrollo) ---
const envDev = `// Archivo GENERADO por scripts/generate-env.js.
// NO editar a mano - los valores provienen de .env en la raiz.
export const environment = {
  production: false,

  productsUrl: 'http://localhost:8081/api/products',
  categoriesUrl: 'http://localhost:8082/api/categories',
  usersUrl: 'http://localhost:${env.USERS_SERVICE_PORT || 8083}/api/users',
  ordersUrl: 'http://localhost:8084/api/orders',

  enableAuth: true,

  authority: '${env.ENTRA_AUTHORITY}',
  apiUri: '${env.ENTRA_API_URI}',
  apiScope: '${env.ENTRA_API_SCOPE}',

  msalConfig: {
    auth: {
      clientId: '${env.ENTRA_CLIENT_ID}',
      authority: '${env.ENTRA_AUTHORITY}',
      redirectUri: '${env.FRONTEND_REDIRECT_URI}'
    },
    cache: {
      cacheLocation: 'localStorage',
      storeAuthStateInCookie: false
    }
  }
};
`;

// --- Escribe environment.prod.ts (produccion) ---
const envProd = `// Archivo GENERADO por scripts/generate-env.js.
// NO editar a mano - los valores provienen de .env en la raiz.
// Para produccion ajusta API_GATEWAY_URL si usas un API Gateway.
export const environment = {
  production: true,

  apiGatewayUrl: 'https://your-api-gateway-url.execute-api.region.amazonaws.com/prod',

  enableAuth: true,

  authority: '${env.ENTRA_AUTHORITY}',
  apiUri: '${env.ENTRA_API_URI}',
  apiScope: '${env.ENTRA_API_SCOPE}',

  msalConfig: {
    auth: {
      clientId: '${env.ENTRA_CLIENT_ID}',
      authority: '${env.ENTRA_AUTHORITY}',
      redirectUri: 'https://your-frontend-domain.com'
    },
    cache: {
      cacheLocation: 'localStorage',
      storeAuthStateInCookie: false
    }
  }
};
`;

fs.mkdirSync(FRONTEND_ENV_DIR, { recursive: true });
fs.writeFileSync(path.join(FRONTEND_ENV_DIR, 'environment.ts'), envDev);
fs.writeFileSync(path.join(FRONTEND_ENV_DIR, 'environment.prod.ts'), envProd);

console.log('OK: environment.ts y environment.prod.ts generados desde .env');
