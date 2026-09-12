# Despliegue del Frontend a GitHub Pages (apuntando al EC2)

Guía para publicar el Frontend de Tuki-Tech en GitHub Pages habilitando HTTPS.
Incluye los cambios ya aplicados al repo para que CI genere las URLs de producción.

> ⚠️ **Por qué es necesario HTTPS en el backend:** GitHub Pages sirve por HTTPS.
> Los navegadores **bloquean** peticiones `fetch`/`XHR` desde una página HTTPS hacia
> un backend **HTTP** (`http://IP_EC2:8081`) — error *mixed content*. Por eso el EC2
> queda detrás de un **HTTPS** (Caddy + Let's Encrypt). No hace falta comprar un
> dominio: un hostname gratuito tipo `sslip.io` que resuelve a tu IP sirve igual
> (Opción A de la Fase 1).

---

## Arquitectura final

```
Navegador del usuario
├── https://pastito-247.github.io/CLOUD-NATIVE-I/   <- Frontend Angular (GitHub Pages)
└── https://<HOST_BACKEND>:443                       <- EC2 con TLS (Caddy + Let's Encrypt)
       Caddy -> 127.0.0.1:8081  (products)
       Caddy -> 127.0.0.1:8082  (categories)
       Caddy -> 127.0.0.1:8083  (users)
       Caddy -> 127.0.0.1:8084  (orders)

Auth: Azure AD redirige a https://pastito-247.github.io/CLOUD-NATIVE-I
```

`<HOST_BACKEND>` es `54.123.45.67.sslip.io` (Opción A, sin dominio) o
`backend.MIDOMINIO.com` (Opción B, dominio propio). Las 4 URLs que genera
el frontend en producción:

| Key del environment | URL producida |
|---|---|
| `productsUrl` | `https://<HOST_BACKEND>/api/products` |
| `categoriesUrl` | `https://<HOST_BACKEND>/api/categories` |
| `usersUrl` | `https://<HOST_BACKEND>/api/users` |
| `ordersUrl` | `https://<HOST_BACKEND>/api/orders` |
| `productsPublicUrl` | `https://<HOST_BACKEND>/api/public/products` (Home, sin login) |
| `categoriesPublicUrl` | `https://<HOST_BACKEND>/api/public/categories` (Home, sin login) |

---

## Cambios ya aplicados en este repo

(Mira `git diff` para verificarlos)

1. `scripts/generate-env.js`
   - `environment.prod.ts` ya no usa placeholders de API Gateway: lee
     `PROD_BACKEND_URL` y `PROD_REDIRECT_URI` del `.env`.
   - Advierte si esas keys faltan.
2. `.env.example` y `.env` → se agregaron las keys de producción.
3. `.github/workflows/deploy-pages.yml`
   - Construye `.env` en CI desde las **Variables** del repo (Settings → Secrets and
     variables → Actions) porque `.env` está en `.gitignore`.
   - `--base-href /CLOUD-NATIVE-I/` (antes decía `/tuki-tech/`, no coincidía con la URL real).
   - Copia `index.html` → `404.html` para que los refrescos en `/CLOUD-NATIVE-I/admin`
     funcionen (GitHub Pages no tiene rewrite de SPA).
   - `publish_dir: ./frontend/dist/ecommerce-tech` (antes apuntaba a `dist/frontend`,
     que no existe — el build real genera `dist/ecommerce-tech`).

---

## Fase 1 — Backend HTTPS: Caddy en el EC2

### 1.1 Elegir cómo exponer el backend

| | **Opción A — sin comprar dominio** | **Opción B — dominio propio** |
|---|---|---|
| Host | `TU-IP.sslip.io` (o `.nip.io`) | `backend.MIDOMINIO.com` |
| Costo | $0 | ~$10-15/año (o DuckDNS gratis) |
| DNS | Ninguno, se resuelve solo | Registro A en tu DNS/registrador |
| TLS | Let's Encrypt (HTTP-01) | Let's Encrypt o Cloudflare |
| Requisito | IP **fija / Elastic IP** | IP fija recomendada |
| Para el curso | ✅ Suficiente | Más "profesional" |

En ambas opciones Caddy emite el certificado automáticamente. Lo único que cambia
es el host que pones en el `Caddyfile` y en `PROD_BACKEND_URL`.

### 1.2 Opción A — `sslip.io` / `nip.io` (sin dominio, gratis)

`54.123.45.67.sslip.io` **resuelve solo a la IP `54.123.45.67`** (reemplaza los
puntos por guiones en `nip.io`). No hay que registrar nada.

1. Asegúrate de que tu instancia tiene una **Elastic IP** (sección EC2 → Elastic IPs
   → Allocate → Associate), porque si la IP cambia, cambia el hostname.
2. Tu `<HOST_BACKEND>` será: `54.123.45.67.sslip.io` (tu IP pública con la
   terminación `.sslip.io`). Yo usaré `TU-IP` como alias en esta guía.

> `nip.io` funciona igual: `54-123-45-67.nip.io`. Si prefieres, usa `nip.io`.

### 1.3 Opción B — dominio propio (DuckDNS o Cloudflare)

- **DuckDNS** (https://duckdns.org, gratis): te regala `tucosa.duckdns.org`
  gestionado por su web/app; se refresca solo. Tu `<HOST_BACKEND>` = `tucosa.duckdns.org`.
- **Con tu dominio + Cloudflare:** registrate en https://dash.cloudflare.com, agrega
  tu dominio y añade el registro:
  - `Type: A` · `Name: backend` · `IPv4 address: IP_DEL_EC2` · `Proxy: OFF` (nube gris).
  - Actualiza los nameservers en tu registrador (te los indica Cloudflare).

> Nube gris (DNS only) mantiene Caddy → Let's Encrypt directo. Si prefieres nube
> naranja (proxy), configura SSL mode = **Full** en Cloudflare.

### 1.4 Abrir puertos 80/443 en el Security Group

En EC2 → tu instancia → Security → Security group → **Edit inbound rules**, agrega:

| Tipo | Puerto | Origen |
|---|---|---|
| HTTP | 80 | 0.0.0.0/0 |
| HTTPS | 443 | 0.0.0.0/0 |

El puerto 80 es necesario para que Let's Encrypt valide el certificado (HTTP-01).
(8081-8084 pueden quedar abiertos para debugging, o cerrarlos ya.)

### 1.5 Instalar Caddy en el EC2

Conectado por SSH:

```bash
sudo apt update
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https curl gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update
sudo apt install -y caddy
```

### 1.6 Configurar el proxy (Caddyfile)

Reemplaza `<HOST_BACKEND>` por tu host:

- Opción A → `TU-IP.sslip.io`
- Opción B → `backend.MIDOMINIO.com`

```bash
sudo tee /etc/caddy/Caddyfile > /dev/null <<'EOF'
# CAMBIA <HOST_BACKEND> por TU-IP.sslip.io (opcion A) o backend.MIDOMINIO.com (opcion B)
<HOST_BACKEND> {
    reverse_proxy /api/products* 127.0.0.1:8081
    reverse_proxy /api/categories* 127.0.0.1:8082
    reverse_proxy /api/users* 127.0.0.1:8083
    reverse_proxy /api/orders* 127.0.0.1:8084
}
EOF

sudo systemctl restart caddy
```

> Caddy obtiene el certificado TLS de Let's Encrypt automáticamente para ese host.

### 1.7 Probar

Desde tu PC (PowerShell):

```powershell
curl https://TU-IP.sslip.io/api/public/products
curl https://TU-IP.sslip.io/api/public/categories
```

(respectivamente con `backend.MIDOMINIO.com` si vas por la Opción B)

Debe responder el mismo JSON que probabas con la IP. Si falla, espera 1-2 min
(primera emisión del certificado) y reintenta.

---

## Fase 2 — CORS en los 4 servicios

El navegador del sitio publicado enviará `Origin: https://pastito-247.github.io`.
Agrega esa URL en cada unidad systemd (y conserva `http://localhost:4200`
para seguir probando local).

Edit en EC2 (`sudo systemctl edit tuki-products` o edita el unit file), cambia:

```
Environment=CORS_ALLOWED_ORIGINS=http://localhost:4200
```
por
```
Environment=CORS_ALLOWED_ORIGINS=http://localhost:4200,https://pastito-247.github.io
```

Repite en `tuki-categories`, `tuki-users` y `tuki-orders`. Luego:

```bash
sudo systemctl daemon-reload
sudo systemctl restart tuki-products tuki-categories tuki-users tuki-orders
```

> Nota: los controllers ya no llevan `@CrossOrigin("*")` (se quitaron al migrar a
> la validación por `issuer` + `audience`). Todo el CORS sale del `SecurityConfig`
> (`cors.allowed-origins`), por eso es obligatorio actualizar el
> `CORS_ALLOWED_ORIGINS` en los 4 servicios.

---

## Fase 3 — Azure AD: registrar la URL de GitHub Pages

En Azure Portal → **App registrations** → tu app del frontend → **Authentication**
(plataforma **SPA**):

1. En **Redirect URIs** agrega:
   ```
   https://pastito-247.github.io/CLOUD-NATIVE-I
   ```
2. En **Front-channel logout URL** (si aparece) agrega la misma URL.
3. Guarda.

Con esto, después del login de Microsoft, Azure redirige de vuelta a GitHub Pages.
Conserva `http://localhost:4200` que ya está registrado para desarrollo local.

---

## Fase 4 — Variables de GitHub (para el CI sin exponer .env)

Crea las Variables del repo (no *Secrets*) en:
**GitHub → tu repo → Settings → Secrets and variables → Actions → Variables → New repository variable**

| Variable | Valor |
|---|---|
| `ENTRA_CLIENT_ID` | `66c0f84b-3701-42fb-9189-3f4092cba201` |
| `ENTRA_TENANT_ID` | `e3e92dfe-ea59-4c42-a539-90e6fea570b6` |
| `ENTRA_AUTHORITY` | `https://login.microsoftonline.com/e3e92dfe-ea59-4c42-a539-90e6fea570b6/v2.0` |
| `ENTRA_API_URI` | `api://tuki-tech-api` |
| `ENTRA_API_SCOPE` | `api://tuki-tech-api/access_as_user` |
| `FRONTEND_REDIRECT_URI` | `http://localhost:4200` (solo usado en dev) |
| `USERS_SERVICE_PORT` | `8083` |
| `PROD_BACKEND_URL` | `https://TU-IP.sslip.io` (Opción A) o `https://backend.MIDOMINIO.com` (Opción B) |
| `PROD_REDIRECT_URI` | `https://pastito-247.github.io/CLOUD-NATIVE-I` |

> Son identificadores públicos de la app SPA (no secretos). Por eso se usan
> **Variables** y no Secrets, evitando el asterisco al escribirlas.
> Si prefieres, puedes usar Secrets con los mismos nombres.

---

## Fase 5 — Habilitar GitHub Pages y publicar

1. **Habilitar Pages**: GitHub → repo → **Settings → Pages**
   - Source: **Deploy from a branch**
   - Branch: `gh-pages` · `/ (root)`
   - Save.
2. **Commit y push** de los cambios (el workflow ya está corregido):
   ```powershell
   git add -A
   git commit -m "corrige generacion de env prod y workflow para GitHub Pages"
   git push origin main
   ```
   (o ejecuta el workflow manualmente con **Actions → Deploy Frontend to GitHub Pages → Run workflow**).
3. Espera a que termine el job (Actions). La primera vez toma ~2 min.

Tu frontend queda en: **https://pastito-247.github.io/CLOUD-NATIVE-I/**

---

## Fase 6 — Verificación

```powershell
# 1. Página pública (productos cargan sin login)
https://pastito-247.github.io/CLOUD-NATIVE-I/

# 2. Login y Admin
#    Click "Iniciar Sesión" -> Microsoft -> acepta -> vuelve al admin si eres admin.

# 3. Endpoints privados traen token (comprueba en DevTools -> Network)
#    GET https://TU-IP.sslip.io/api/products  -> 200 con Authorization: Bearer ...
```

Lista de chequeo:

- [ ] `curl https://TU-IP.sslip.io/api/public/products` responde JSON.
- [ ] Home muestra productos y categorías desde el navegador (sin errores mixed content).
- [ ] Login redirige a Microsoft y vuelve al sitio.
- [ ] `/CLOUD-NATIVE-I/admin` accesible con usuario `admin`, login funciona al refrescar la página.
- [ ] Consola DevTools sin errores `Origin ... not allowed` (CORS) ni `blocked:mixed-content`.

---

## Notas y solución de problemas

| Síntoma | Causa probable | Fix |
|---|---|---|
| `CORS policy: No 'Access-Control-Allow-Origin'` en users | `CORS_ALLOWED_ORIGINS` no actualizado | Fase 2 |
| `blocked:mixed-content` o `ERR_INSECURE_RESPONSE` | Se llamó a `http://IP_EC2` desde la página HTTPS | Tus URLs deben usar `https://TU-IP.sslip.io` (o tu dominio) |
| Error de Azure tras login (`AADSTS50011`) | Redirect URI no registrada en la App Registration | Fase 3 |
| `/CLOUD-NATIVE-I/admin` da 404 al refrescar | Faltó el `404.html` en la build | El workflow ya lo copia; re-push |
| Página en blanco / assets no cargan | `base-href` incorrecto | Debe ser `/CLOUD-NATIVE-I/` |
| Build falla en CI con `.env not found` | Falta crearlas variables del repo | Fase 4 (no uses Secrets si pusiste Variables) |
| Let's Encrypt no emite certificado | El host no resuelve al EC2 (A record/dns) o cambió la IP | Con sslip.io debes usar una **Elastic IP**; con dominio verifica `nslookup TU-IP.sslip.io` / del dominio |

**Actualizar el host del backend** (si cambias la IP o el subdominio):
cambia `PROD_BACKEND_URL` (Variable de GitHub y `.env` local), ajusta el `Caddyfile`,
registra la nueva URL en Azure AD si cambia el host, y re-push.
Recuerda: con la Opción A el hostname depende de la Elastic IP — si la IP cambia,
cambia el host; con dominio propio solo cambias el registro A.

**Volver a desarrollo local**: no afecta, porque el environment de desarrollo sigue
apuntando a `http://localhost:8081-8084` y CORS ya permite `http://localhost:4200`.