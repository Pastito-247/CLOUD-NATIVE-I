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

4. **Routes — Configure routes** — crea estas **13 rutas** (`method ANY`):

   > En HTTP API el `{proxy+}` **no matchea un path vacío** después del prefijo.
   > El frontend llama URLs peladas (`.../api/products`) además de las de `{id}`
   > (`.../api/products/{id}`), así que cada recurso necesita **dos** rutas:
   > una pelada y una con `{proxy+}`. Y una ruta `OPTIONS` sin authorizer para
   > que la preflight no pase por el authorizer JWT.

   | Method | Path | Integración | Authorizer |
   |---|---|---|---|
   | OPTIONS | `/api/public/products` | `products-public-bare` | (ninguno) |
   | OPTIONS | `/api/public/products/{proxy+}` | `products-public` | (ninguno) |
   | OPTIONS | `/api/public/categories` | `categories-public-bare` | (ninguno) |
   | OPTIONS | `/api/public/categories/{proxy+}` | `categories-public` | (ninguno) |
   | OPTIONS | `/api/products` | `products-bare` | (ninguno) |
   | OPTIONS | `/api/products/{proxy+}` | `products` | (ninguno) |
   | OPTIONS | `/api/categories` | `categories-bare` | (ninguno) |
   | OPTIONS | `/api/categories/{proxy+}` | `categories` | (ninguno) |
   | OPTIONS | `/api/users` | `users-bare` | (ninguno) |
   | OPTIONS | `/api/users/{proxy+}` | `users` | (ninguno) |
   | OPTIONS | `/api/orders` | `orders-bare` | (ninguno) |
   | OPTIONS | `/api/orders/{proxy+}` | `orders` | (ninguno) |
   | ANY | `/api/public/products` | `products-public-bare` | (ninguno) |
   | ANY | `/api/public/products/{proxy+}` | `products-public` | (ninguno) |
   | ANY | `/api/public/categories` | `categories-public-bare` | (ninguno) |
   | ANY | `/api/public/categories/{proxy+}` | `categories-public` | (ninguno) |
   | ANY | `/api/products` | `products-bare` | `tuki-jwt` |
   | ANY | `/api/products/{proxy+}` | `products` | `tuki-jwt` |
   | ANY | `/api/categories` | `categories-bare` | `tuki-jwt` |
   | ANY | `/api/categories/{proxy+}` | `categories` | `tuki-jwt` |
   | ANY | `/api/users` | `users-bare` | `tuki-jwt` |
   | ANY | `/api/users/{proxy+}` | `users` | `tuki-jwt` |
   | ANY | `/api/orders` | `orders-bare` | `tuki-jwt` |
   | ANY | `/api/orders/{proxy+}` | `orders` | `tuki-jwt` |

   > **Integraciones `-bare`**: las rutas peladas (`/api/products` sin nada después)
   > NO pueden reusar las integraciones con `{proxy}`, porque el backend recibe
   > `/api/products/` (slash final) y Spring responde 401/404. Crea una
   > integración **sin `{proxy}`** para cada recurso (ej. `products-bare` → URI
   > `http://TU_EC2_IP:8081/api/products`).

   > Cada ruta debe tener **una integración asociada** (si está vacía devuelve
   > `404 {"message":"Not Found"}`). Las rutas `OPTIONS` explícitas ganan sobre
   > las `ANY` en las preflight por el método; **no** basta una catch-all
   > `OPTIONS /api/{proxy+}` porque las rutas `ANY` con path más específico la
   > ensombrecen y el authorizer rechaza la preflight con 401.
   > Las rutas `/api/public/...` son más específicas que `/api/*` peladas.
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
3. Adjunta el authorizer a las **8 rutas privadas** (las 4 peladas y las 4 con
`{proxy+}` de `/api/products*`, `/api/categories*`, `/api/users*`,
`/api/orders*`):
- Entra a **Routes**, abre cada ruta privada → **Attach authorization → tuki-jwt**.
- Las rutas `/api/public/*` y la `OPTIONS /api/{proxy+}` quedan **sin** authorizer.
4. Guarda y **Re-deploy** (botón **Deploy** → stage `$default`).

> El access token de la app SPA trae `aud: api://tuki-tech-api`, igual que el
> `audiences` configurado en los 4 servicios (validación en ambas capas).

---

## Fase 4 — CORS

Como el frontend publica en GitHub Pages y el API Gateway está en otro origen,
la API responde con headers CORS solo desde el **API Gateway**.

> ⚠️ **Importante — por qué falla la preflight sin esto:**
> Con las rutas `ANY /.../{proxy+}` protegidas por el authorizer JWT, el
> `OPTIONS` de preflight **matchea la ruta `ANY`** y el authorizer lo rechaza
> con `401` (la preflight del navegador no lleva token). El navegador entonces
> muestra `Response to preflight request doesn't pass access control check`.
> API Gateway solo responde la preflight automáticamente cuando **ninguna ruta
> matchea**, cosa que con `ANY .../{proxy+}` nunca ocurre. La solución es crear
> una ruta `OPTIONS` explícita **sin authorizer** (una ruta con método explícito
> gana sobre la `ANY`).

### 4.1 CORS en la API

1. **CORS → Configure** (menú de la API).
2. **Allow origins**: `http://localhost:4200`, `https://pastito-247.github.io`
3. **Allow methods**: `GET,POST,PUT,DELETE,PATCH,OPTIONS`
4. **Allow headers**: `authorization,content-type,x-requested-with`
5. **Expose headers**: `*`
6. **Allow credentials**: **OFF** (coincide con `allowCredentials=false` del backend).
7. **Save**.

### 4.2 Crear una ruta OPTIONS sin authorizer (IMPORTANTE)

En **Routes → Create**, agrega:

| Method | Path | Integration | Authorizer |
|---|---|---|---|
| OPTIONS | `/api/{proxy+}` | `products` (cualquiera de las integraciones HTTP) | (ninguno) |

- La ruta `OPTIONS /api/{proxy+}` tiene **prioridad** sobre `ANY /api/.../{proxy+}`
solo para los `OPTIONS`, así que la preflight **no pasa por el authorizer JWT**.
- El `OPTIONS` se reenvía al backend, que responde `200` (ver 4.3). API Gateway le
agrega los headers CORS configurados en 4.1.

### 4.3 Dejar CORS ENCENDIDO en el backend (EC2)

Como el `OPTIONS` se reenvía al backend por la integración HTTP, el backend debe
responder `200` a la preflight. Por eso **NO** se vacía `CORS_ALLOWED_ORIGINS` (a
diferencia de versiones anteriores de esta guía). Por SSH en el EC2, en los 4
units:

```bash
sudo systemctl edit tuki-products
```

Pega y guarda:

```ini
[Service]
Environment=CORS_ALLOWED_ORIGINS=http://localhost:4200,https://pastito-247.github.io
```

Haz lo mismo en `tuki-categories`, `tuki-users` y `tuki-orders`, luego:

```bash
sudo systemctl daemon-reload
sudo systemctl restart tuki-products tuki-categories tuki-users tuki-orders
```

> El `SecurityConfig` de cada servicio usa un `CorsFilter` de Spring que
> responde la preflight `OPTIONS` con `200` de forma directa (sin pasar por la
> autorización JWT). API Gateway **ignora** los headers CORS que devuelva el
> backend (`Access-Control-Allow-Origin` duplicado no será un problema): solo
> se ven los del API Gateway.

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
| Preflight `OPTIONS` → `401 Unauthorized` en rutas privadas | El authorizer JWT rechaza el `OPTIONS` (matchea la ruta `ANY .../{proxy+}`) | Crea la ruta `OPTIONS /api/{proxy+}` **sin authorizer** (Fase 4.2) |
| Preflight `OPTIONS` → `403 Invalid CORS request` | `CORS_ALLOWED_ORIGINS` vacío en el backend (Spring responde 403 a la preflight) | Vuelve a poner las origins en los 4 units y reinicia (Fase 4.3) |
| `Access-Control-Allow-Origin` **duplicado** | Backend con CORS encendido + API Gateway CORS | No es problema: API Gateway ignora los headers CORS del backend. Déjalo encendido (Fase 4.3) |
| `Connection refused` / `502` | API Gateway no llega al EC2 | Revisa Security Group (8081-8084) y que el EC2 esté corriendo; prueba `curl` directo a `http://TU_EC2_IP:808X/api/public/products` |
| Response timeout | Integración HTTP lenta | Los servicios arrancan hasta 30-60s; espera a que `systemctl status` diga running |
| Todo funciona local pero no en Pages | El CI usó `PROD_BACKEND_URL` vieja | Actualiza la **Variable de GitHub** y re-corre el workflow |
| `invalid_token` en backend aunque API GW dejó pasar | La audience del backend (`audiences`) no coincide | Verifica `spring.security.oauth2.resourceserver.jwt.audiences=api://tuki-tech-api` en los 4 servicios |