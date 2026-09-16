// ============================================================
// Seed de datos demo para Tuki-Tech.
//
// Carga categorías y productos de ejemplo en el backend a través
// de la API (crea solo lo que no exista, es idempotente).
//
// Uso:
//   node scripts/seed-data.js --backend URL_BACKEND --token ACCESS_TOKEN
//
//   --backend : base de la API (ej: https://TU_API.execute-api.REGION.amazonaws.com).
//               Si se omite, usa DEV_BACKEND_URL o PROD_BACKEND_URL del .env.
//   --token   : access token de Azure AD con rol 'admin' (se obtiene desde
//               DevTools de la app logueada, o con el flujo de MSAL).
//
// Las rutas públicas se consultan sin token; las de escritura (POST)
// exigen el token de admin.
// ============================================================

const fs = require('fs');
const path = require('path');

// --- Lee .env para obtener el backend por defecto ---
const ROOT = path.resolve(__dirname, '..');
const ENV_FILE = path.join(ROOT, '.env');

function loadEnv(filePath) {
  const env = {};
  if (!fs.existsSync(filePath)) return env;
  const content = fs.readFileSync(filePath, 'utf8');
  for (const rawLine of content.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) continue;
    const eq = line.indexOf('=');
    if (eq === -1) continue;
    const key = line.slice(0, eq).trim();
    let value = line.slice(eq + 1).trim();
    if ((value.startsWith('"') && value.endsWith('"')) ||
        (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    if (key) env[key] = value;
  }
  return env;
}

// --- Argumentos CLI ---
function argValue(name) {
  const idx = process.argv.indexOf(name);
  return idx !== -1 && process.argv[idx + 1] ? process.argv[idx + 1] : null;
}

const env = loadEnv(ENV_FILE);
const backend =
  argValue('--backend') ||
  env.DEV_BACKEND_URL ||
  env.PROD_BACKEND_URL ||
  'http://localhost:8000';

const token = argValue('--token');

if (!token) {
  console.error('Falta el token. Obtenlo desde DevTools (Network -> request a /api/products -> Authorization: Bearer ...) y pasa --token TU_TOKEN');
  process.exit(1);
}

const API = {
  categories: `${backend}/api/categories`,
  products: `${backend}/api/products`,
  categoriesPublic: `${backend}/api/public/categories`,
  productsPublic: `${backend}/api/public/products`
};

// ============================================================
// DATOS DEMO
// ============================================================

const CATEGORIES = [
  { name: 'Computadores', description: 'Laptops, PCs de escritorio y monitores.' },
  { name: 'Celulares', description: 'Smartphones y accesorios móviles.' },
  { name: 'Audio y Sonido', description: 'Audífonos, parlantes y equipos de audio.' },
  { name: 'Gaming', description: 'Consolas, controles y periféricos gamer.' },
  { name: 'Accesorios', description: 'Cables, cargadores y complementos.' },
  { name: 'Hogar Inteligente', description: 'Dispositivos IoT para tu casa.' }
];

const PRODUCTS = [
  // Computadores
  { name: 'Laptop Lenovo IdeaPad 3', description: 'Core i5, 16GB RAM, SSD 512GB, pantalla 15.6" Full HD.', price: 2599000, category: 'Computadores', stockQuantity: 12 },
  { name: 'PC de Escritorio Gaming Core i7', description: 'Core i7, RTX 4060, 32GB RAM, SSD 1TB. Ideal para gaming.', price: 4199000, category: 'Computadores', stockQuantity: 5 },
  { name: 'Monitor Samsung 24" Full HD', description: 'Panel IPS de 24 pulgadas, 75Hz, gran ángulo de visión.', price: 749000, category: 'Computadores', stockQuantity: 18 },
  // Celulares
  { name: 'Samsung Galaxy A54 5G', description: '8GB RAM, 128GB, cámara triple de 50MP y batería 5000mAh.', price: 1599000, category: 'Celulares', stockQuantity: 20 },
  { name: 'Xiaomi Redmi Note 12', description: '6GB RAM, 128GB, AMOLED 120Hz y carga rápida 33W.', price: 899000, category: 'Celulares', stockQuantity: 25 },
  { name: 'iPhone 13 128GB', description: 'Chip A15 Bionic, 128GB y cámara dual de 12MP.', price: 3299000, category: 'Celulares', stockQuantity: 8 },
  { name: 'Huawei P30 Lite', description: '4GB RAM, 128GB, gran rendimiento a precio accesible.', price: 699000, category: 'Celulares', stockQuantity: 15 },
  // Audio y Sonido
  { name: 'Audífonos JBL Tune 510BT', description: 'Bluetooth, graves potentes y hasta 40h de batería.', price: 249000, category: 'Audio y Sonido', stockQuantity: 30 },
  { name: 'Audífonos Sony WH-CH520', description: 'Diadema inalámbrica con cancelación de ruido HD.', price: 289000, category: 'Audio y Sonido', stockQuantity: 22 },
  { name: 'Parlante Bluetooth JBL Clip 5', description: 'Portátil, resistente al agua IP67 y 15h de batería.', price: 299000, category: 'Audio y Sonido', stockQuantity: 14 },
  // Gaming
  { name: 'Consola Xbox Series S', description: '512GB, 1440p/120fps y Game Pass incluido.', price: 1499000, category: 'Gaming', stockQuantity: 10 },
  { name: 'Control Inalámbrico PS5 DualSense', description: 'Adaptive triggers y retroalimentación háptica.', price: 349000, category: 'Gaming', stockQuantity: 16 },
  { name: 'Teclado Mecánico Logitech G413', description: 'Switches mecánicos, retroiluminación RGB y aluminio.', price: 429000, category: 'Gaming', stockQuantity: 19 },
  // Accesorios
  { name: 'Cargador Rápido 65W GaN', description: 'Carga USB-C/PD compatible con laptop y celular.', price: 69000, category: 'Accesorios', stockQuantity: 50 },
  { name: 'Cable USB-C Trenzado 1m', description: 'Carga rápida y transferencia de datos a alta velocidad.', price: 19000, category: 'Accesorios', stockQuantity: 60 },
  { name: 'Base Refrigeradora para Laptop', description: 'Doble ventilador adjustable, ideal para uso prolongado.', price: 129000, category: 'Accesorios', stockQuantity: 11 },
  // Hogar Inteligente
  { name: 'Foco Wi-Fi Smart RGB', description: 'Control por app y voz, 16 millones de colores.', price: 49900, category: 'Hogar Inteligente', stockQuantity: 40 },
  { name: 'Cámara Xiaomi Smart 360º', description: 'Full HD, visión nocturna y detección de movimiento.', price: 279000, category: 'Hogar Inteligente', stockQuantity: 13 },
  { name: 'Enchufe Inteligente Wi-Fi', description: 'Control remoto de tus electrodomésticos desde el celular.', price: 59900, category: 'Hogar Inteligente', stockQuantity: 35 }
];

// ============================================================
// HELPERS
// ============================================================

async function request(method, url, body, auth) {
  const headers = { 'Content-Type': 'application/json' };
  if (auth) headers['Authorization'] = `Bearer ${auth}`;

  const res = await fetch(url, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined
  });

  if (!res.ok) {
    const text = await res.text();
    throw new Error(`${method} ${url} -> ${res.status} ${text.slice(0, 200)}`);
  }

  return res.status === 204 ? null : res.json();
}

async function getList(url) {
  const res = await fetch(url);
  if (!res.ok) return [];
  return res.json();
}

const sleep = (ms) => new Promise(r => setTimeout(r, ms));

// ============================================================
// SEED
// ============================================================

async function main() {
  console.log(`Backend: ${backend}\n`);

  // ---- Categorías ----
  console.log('== Categorías ==');
  const existingCategories = await getList(API.categoriesPublic);
  const existingNames = new Set(existingCategories.map(c => c.name));

  let createdCategories = 0;
  for (const cat of CATEGORIES) {
    if (existingNames.has(cat.name)) {
      console.log(`  - Ya existe: ${cat.name}`);
      continue;
    }
    await request('POST', API.categories, cat, token);
    console.log(`  + Creada: ${cat.name} (${cat.description})`);
    createdCategories++;
    await sleep(150);
  }

  // ---- Productos ----
  console.log('\n== Productos ==');
  const existingProducts = await getList(API.productsPublic);
  const existingProductNames = new Set(existingProducts.map(p => p.name));

  let createdProducts = 0;
  for (let i = 0; i < PRODUCTS.length; i++) {
    const p = PRODUCTS[i];

    if (existingProductNames.has(p.name)) {
      console.log(`  - Ya existe: ${p.name}`);
      continue;
    }

    const product = {
      ...p,
      description: p.description,
      imageUrl: `https://picsum.photos/seed/tuki-${i + 1}/400/300`
    };

    await request('POST', API.products, product, token);
    console.log(`  + Creado: ${p.name} ($${p.price.toLocaleString('es-CO')})`);
    createdProducts++;
    await sleep(150);
  }

  console.log(`\nResumen: ${createdCategories} categorías y ${createdProducts} productos creados.`);
  if (createdProducts === 0 && createdCategories === 0) {
    console.log('Ya estaba todo cargado. Nada por hacer.');
  }
  console.log('\nRecarga el Home en el navegador para ver el catálogo. 🦎');
}

main().catch((err) => {
  console.error('\nError:', err.message);
  if (err.message.includes('401')) {
    console.error('El token no es válido o expiró. Obtén uno nuevo desde DevTools.');
  }
  process.exit(1);
});