# Despliegue del AWS API Gateway

Guía para poner un **API Gateway de AWS** delante del backend en EC2 y así tener
toda la arquitectura en la nube:

```
Navegador
├── https://pastito-247.github.io/CLOUD-NATIVE-I/   <- Frontend (GitHub Pages, HTTPS)
└── https://<API_ID>.execute-api.<REGION>.amazonaws.com   <- API Gateway (HTTPS)
       ├── /api/public/products   (sin JWT) -> EC2:8081 (products-service)
       ├── /api/public/categories (sin JWT) -> EC2:8082 (categories-service)
       ├── /api/products/*        (JWT)     -> EC2:8081 (products-service)
       ├── /api/categories/*      (JWT)     -> EC2:8082 (categories-service)
       ├── /api/users/*           (JWT)     -> EC2:8083 (users-service)
       └── /api/orders/*          (JWT)     -> EC2:8084 (orders-service)
```

El API Gateway da **HTTPS** gratis (sin comprar dominio ni Caddy) y valida los JWT
de Azure AD con un **authorizer nativo**, cumpliendo el criterio de evaluación
"Validación JWT en backend y API Gateway".

---

## Ventajas de usar HTTP API (en vez de REST API)

| | HTTP API (elegida) | REST API |
|---|---|---|
| Authorizer JWT | Nativo (issuer + audience) | No nativo (Lambda authorizer o Cognito) |
| Costo | Gratis 1M req/mes | Gratis 1M req/mes |
| CORS | Configuración simple a nivel API | Configuración más trabajosa |
| Configuración | Rápida (console / CLI) | Más pasos |

Para tokens de **Entra ID (v2.0)** el JWT authorizer de HTTP API verifica firma,
issuer y audience sin código extra.

---

## Prerrequisitos

- El backend ya desplegado en el EC2 (**Fase 1-6 de `EC2_DEPLOYMENT.md`**):
  4 JARs corriendo en 8081-8084 con `systemd`.
- Elastic IP asignada al EC2 (no cambia). Anota `TU_EC2_IP`.
- Security group con 8081-8084 abiertos (ya configurado en la guía del EC2).
- Reemplazar `TU_EC2_IP` y `REGION` por tus valores reales.
- El código del repo ya tiene las rutas públicas con alias `/api/public/**`
  (las agregamos en el commit `2d509fb`).

---

## Fase 1 — Confirmar que el EC2 responde (sin API Gateway aún)

Desde tu PC (PowerShell):

```powershell
curl http://TU_EC2_IP:8081/api/public/products
curl http://TU_EC2_IP:8082/api/public/categories
```

Debe responder JSON (no `Connection refused`). Si no responde, revisa primero
`EC2_DEPLOYMENT.md` antes de seguir.

> Si ya instalamos Caddy ponlo detrás solo cuando quieras ocultar los puertos;
> para el API Gateway directo a los puertos 8081-8084 es suficiente.

---

## Fase 2 — Crear la HTTP API

1. **AWS Console → API Gateway → Create API → HTTP API → Build**.
2. En **Name** pon `tuki-api`.
3. **Integrations** — agrega 4 integraciones tipo **HTTP** (una por servicio).
   En el **Target** usa la URL completa con `{proxy}` para que el path llegue
   **entero** al backend (el prefijo se conserva):

   | Nombre integración | Target (HTTP) | Servicio |
   |---|---|---|
   | `products-public` | `http://TU_EC2_IP:8081/api/public/products/{proxy}` | products |
   | `categories-public` | `http://TU_EC2_IP:8082/api/public/categories/{proxy}` | categories |
   | `products` | `http://TU_EC2_IP:8081/api/products/{proxy}` | products |
   | `categories` | `http://TU_EC2_IP:8082/api/categories/{proxy}` | categories |
   | `users` | `http://TU_EC2_IP:8083/api/users/{proxy}` | users |
   | `orders` | `http://TU_EC2_IP:8084/api/orders/{proxy}` | orders |

   > El `{proxy}` del target se llena con el resto del path de la ruta:
   > ruta `ANY /api/products/{proxy+}` + target `.../api/products/{proxy}`
   > ⇒ el backend recibe exactamente `/api/products/...`.

4. **Routes — Configure routes** — crea estas 6 rutas (`method ANY`):

   | Method | Path | Integración | Authorizer |
   |---|---|---|---|
   | ANY | `/api/public/products/{proxy+}` | `products-public` | (ninguno) |
   | ANY | `/api/public/categories/{proxy+}` | `categories-public` | (ninguno) |
   | ANY | `/api/products/{proxy+}` | `products` | `tuki-jwt` |
   | ANY | `/api/categories/{proxy+}` | `categories` | `tuki-jwt` |
   | ANY | `/api/users/{proxy+}` | `users` | `tuki-jwt` |
   | ANY | `/api/orders/{proxy+}` | `orders` | `tuki-jwt` |

   > Las rutas `/api/public/...` son más específicas y ganan sobre `/api/*`.
   > Lo que no matchee ninguna ruta devuelve 403/404 (esperado).

5. Al final de la creación, el API ya tiene un stage `$default` y una
   **URL de invocación** tipo:
   `https://<API_ID>.execute-api.<REGION>.amazonaws.com`

   Anota esa URL (sin stage) — será tu `PROD_BACKEND_URL`.

---

## Fase 3 — Authorizer JWT (Azure AD)

1. En el menú de la API → **Authorization → Manage authorizers → Create**.
   - **Name**: `tuki-jwt`
   - **Type**: **JWT**
   - **Identity source**: `$request.header.Authorization`
   - **Issuer**: `https://login.microsoftonline.com/e3e92dfe-ea59-4c42-a539-90e6fea570b6/v2.0`
   - **Audience**: `api://tuki-tech-api`
2. **Create**.
3. Adjunta el authorizer a las **4 rutas privadas** (`/api/products/*`,
   `/api/categories/*`, `/api/users/*`, `/api/orders/*`):
   - Entra a **Routes**, abre cada ruta privada → **Attach authorization → tuki-jwt**.
   - Las rutas `/api/public/*` quedan **sin** authorizer.
4. Guarda y **Re-deploy** (botón **Deploy** → stage `$default`).

> El access token de la app SPA trae `aud: api://tuki-tech-api`, igual que el
> `audiences` configurado en los 4 servicios (validación en ambas capas).

---

## Fase 4 — CORS

Como el frontend publica en GitHub Pages y el API Gateway está en otro origen,
la API responde con headers CORS. Hay que configurar CORS **solo en el API
Gateway** y **apagar el CORS del backend**, si no el navegador ve el header
`Access-Control-Allow-Origin` repetido y bloquea la respuesta.

### 4.1 CORS en la API

1. **CORS → Configure** (menú de la API).
2. **Allow origins**: `http://localhost:4200`, `https://pastito-247.github.io`
3. **Allow methods**: `GET,POST,PUT,DELETE,PATCH,OPTIONS`
4. **Allow headers**: `authorization,content-type,x-requested-with`
5. **Expose headers**: `*`
6. **Allow credentials**: **OFF** (coincide con `allowCredentials=false` del backend).
7. **Save**.

> Con esto API Gateway responde las preflight `OPTIONS` él mismo, sin llegar
> al backend ni al authorizer (sin token roto para rutas privadas).

### 4.2 Apagar CORS en el backend (EC2)

Por SSH en el EC2, edita los 4 unit files para **vaciar** `CORS_ALLOWED_ORIGINS`:

```bash
sudo systemctl edit tuki-products
```

Pega y guarda:

```ini
[Service]
Environment=CORS_ALLOWED_ORIGINS=
```

Haz lo mismo en `tuki-categories`, `tuki-users` y `tuki-orders`, luego:

```bash
sudo systemctl daemon-reload
sudo systemctl restart tuki-products tuki-categories tuki-users tuki-orders
```

> Dejar el valor vacío hace que Spring no agregue headers CORS; los únicos
> headers CORS serán los del API Gateway.

---

## Fase 5 — Probar el API Gateway

```powershell
# Público (sin token) -> 200 con JSON
curl -i https://<API_ID>.execute-api.<REGION>.amazonaws.com/api/public/products
curl -i https://<API_ID>.execute-api.<REGION>.amazonaws.com/api/public/categories

# Privado sin token -> 401 (el authorizer lo rechaza)
curl -i https://<API_ID>.execute-api.<REGION>.amazonaws.com/api/products

# (Opcional) Con token -> 200
curl -i -H "Authorization: Bearer EL_ACCESS_TOKEN" https://<API_ID>.execute-api.<REGION>.amazonaws.com/api/products
```

- Público: espera `200` + JSON (no `Connection refused`).
- Privado sin token: espera `401` (valida JWT el authorizer) o `403`.
- En el navegador con DevTools: el login debe seguir funcionando y el Home
  cargando productos/categorías **sin** error CORS (los headers ACAO deben aparecer
  una sola vez).

---

## Fase 6 — Apuntar el frontend al API Gateway

### 6.1 Variable de GitHub (para el CI / GitHub Pages)

En **GitHub → repo → Settings → Secrets and variables → Actions → Variables**,
edita `PROD_BACKEND_URL` con:

```
https://<API_ID>.execute-api.<REGION>.amazonaws.com
```

(Es la URL de invocación del stage `$default`. Si usaste otro nombre de stage,
agrégalo: `...amazonaws.com/NOMBRE_STAGE`.)

### 6.2 `.env` local (opcional, para build local)

En la raíz del proyecto, edita `.env`:

```
PROD_BACKEND_URL=https://<API_ID>.execute-api.<REGION>.amazonaws.com
```

Y regenera los environments:

```powershell
node scripts/generate-env.js
cd frontend
npm run build -- --configuration production --base-href /CLOUD-NATIVE-I/
```

### 6.3 Redesplegar GitHub Pages

Haz un push o corre **Actions → Deploy Frontend to GitHub Pages → Run workflow**.
El CI reconstruye el `.env` desde las Variables del repo y publica con la URL del
API Gateway.

---

## Fase 7 — Verificación final

```powershell
# 1. Frontend público (productos cargan sin login)
https://pastito-247.github.io/CLOUD-NATIVE-I/

# 2. Login -> Microsoft -> vuelve al sitio
# 3. En DevTools (Network) revisa que las peticiones van a:
#    https://<API_ID>.execute-api.<REGION>.amazonaws.com/api/...
#    - /api/public/*    -> 200 sin token
#    - /api/products    -> 200 con Authorization: Bearer ...
```

Checklist:

- [ ] `curl https://<API_ID>.execute-api.<REGION>.amazonaws.com/api/public/products` responde JSON.
- [ ] Home muestra productos/categorías desde el navegador (sin CORS ni mixed-content).
- [ ] Login redirige a Microsoft y vuelve.
- [ ] CRUD en `/admin` funciona (token viaja con `Authorization`).
- [ ] DevTools: `Access-Control-Allow-Origin` aparece **una sola vez**.
- [ ] Rutas privadas sin token devuelven `401/403`.

---

## Notas y costo

- **Costo**: HTTP API gratis hasta 1M requests/mes (free tier); después ~$1/M.
  Para el curso no paga nada. Deja la API **eliminada** cuando termines si no la usarás.
- **URLs generadas por el frontend** con `PROD_BACKEND_URL` = `https://...execute-api...`:

  | Key | URL |
  |---|---|
  | `productsPublicUrl` | `{PROD_BACKEND_URL}/api/public/products` |
  | `categoriesPublicUrl` | `{PROD_BACKEND_URL}/api/public/categories` |
  | `productsUrl` | `{PROD_BACKEND_URL}/api/products` |
  | `categoriesUrl` | `{PROD_BACKEND_URL}/api/categories` |
  | `usersUrl` | `{PROD_BACKEND_URL}/api/users` |
  | `ordersUrl` | `{PROD_BACKEND_URL}/api/orders` |

- **Azur AD**: no cambia nada. El redirect URI sigue siendo la URL de GitHub
  Pages. El access token es el mismo.

---

## Solución de problemas

| Síntoma | Causa probable | Fix |
|---|---|---|
| `401` en rutas privadas con token válido | Audience/issuer mal configurados en el authorizer o token de otra app | Verifica en `jwt.ms` el `iss`/`aud`; deben ser `.../e3e92dfe.../v2.0` y `api://tuki-tech-api` |
| `403` en rutas que ya existen | El authorizer rechazó sin dar 401, o la ruta no matchea | Revisa `$request.header.Authorization` y que la ruta tenga `{proxy+}` |
| `Access-Control-Allow-Origin` **duplicado** | Backend con CORS encendido + API Gateway CORS | Fase 4.2: vacía `CORS_ALLOWED_ORIGINS` en los 4 units y reinicia |
| `Connection refused` / `502` | API Gateway no llega al EC2 | Revisa Security Group (8081-8084) y que el EC2 esté corriendo; prueba `curl` directo a `http://TU_EC2_IP:808X/api/public/products` |
| Response timeout | Integración HTTP lenta | Los servicios arrancan hasta 30-60s; espera a que `systemctl status` diga running |
| Todo funciona local pero no en Pages | El CI usó `PROD_BACKEND_URL` vieja | Actualiza la **Variable de GitHub** y re-corre el workflow |
| `invalid_token` en backend aunque API GW dejó pasar | La audience del backend (`audiences`) no coincide | Verifica `spring.security.oauth2.resourceserver.jwt.audiences=api://tuki-tech-api` en los 4 servicios |