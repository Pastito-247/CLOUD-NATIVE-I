# E-Commerce Tech

Proyecto del ramo DESARROLLO CLOUD NATIVE I

## Arquitectura

### Frontend (Angular + MSAL)
- **Home**: Catálogo de productos públicos (API pública vía API Gateway)
- **Login**: Autenticación con Azure AD (MSAL) - flujo Authorization Code + PKCE
- **Admin Dashboard**: Gestión de productos, categorías, usuarios (perfilado por roles)
- **Carrito/Checkout**: Funcionalidades para usuarios autenticados (APIs privadas con JWT)

### Backend (Spring Boot Microservicios)
- **Products Service** (puerto 8081): CRUD de productos
- **Categories Service** (puerto 8082): CRUD de categorías
- **Users Service** (puerto 8083): Gestión de usuarios y roles
- **Orders Service** (puerto 8084): Gestión de pedidos

### Infraestructura AWS
- **API Gateway**: Rutas públicas y privadas con validación JWT
- **EC2**: Instancias para cada microservicio
- **RDS**: Base de datos MySQL cloud

### IDaaS (Azure AD)
- **Roles**: admin, customer, staff
- **Scopes**: read, write, admin
- **Flujo**: Authorization Code con PKCE

## Instalación

### Requisitos previos
- Java 17+ y Maven
- Node 18+ y npm
- Una cuenta con permisos de administrador en Microsoft Entra ID (para crear la app registration)

## Configuración del archivo `.env`

Todos los valores de Entra ID se gestionan desde el archivo **`.env`** en la raíz del proyecto.

1. Copia la plantilla: `Copy-Item .env.example .env`
2. Rellena los valores con tu App Registration (pasos abajo)

```dotenv
ENTRA_CLIENT_ID=XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX
ENTRA_TENANT_ID=XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX
ENTRA_AUTHORITY=https://login.microsoftonline.com/XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX/v2.0
ENTRA_API_URI=api://XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX
ENTRA_API_SCOPE=api://XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX/access_as_user
FRONTEND_REDIRECT_URI=http://localhost:4200
USERS_SERVICE_PORT=8083
```

> **Nota:** `environment.ts` (Angular) se **genera automáticamente** desde `.env` al ejecutar `npm start` o `npm run build` (script `prestart`/`prebuild`). No lo edites a mano.

## Configuración de Microsoft Entra ID (Azure AD)

### 1. Crear la App Registration (SPA)
1. Ve a **Azure Portal → Microsoft Entra ID → App registrations → New registration**
2. **Nombre**: `ecommerce-frontend`
3. **Supported account types**: "Accounts in this organizational directory only"
4. **Redirect URI**: tipo **Single-page application (SPA)** → `http://localhost:4200`
5. Crear. Anota el **Application (client) ID** → `ENTRA_CLIENT_ID` y el **Directory (tenant) ID** → `ENTRA_TENANT_ID`

### 2. Exponer la API (scope)
1. En tu app → **Expose an API**
2. En **Application ID URI** → **Set** → guarda el URI tipo `api://<client-id>`
3. **+ Add a scope**:
   - Scope name: `access_as_user`
   - Who can consent: **Admins and users**
   - Guarda el scope completo: `api://<client-id>/access_as_user` → `ENTRA_API_SCOPE`

### 3. Crear los roles personalizados (App Roles)
Los roles deben crearse **dentro de la App Registration** para que aparezcan en el claim `roles` del token.

1. En tu app → **App roles** → **+ Create app role**
2. Crea el rol **admin**:
   - Display name: `Administrator`
   - Allowed member types: **Users/Groups**
   - Value: `admin`
   - Description: "Acceso administrador"
3. Crea el rol **customer**:
   - Display name: `Customer`
   - Allowed member types: **Users/Groups**
   - Value: `customer`
   - Description: "Cliente regular"

> **IMPORTANTE:** El claim `roles` solo se incluye en el token **después** de asignar los roles a los usuarios.

### 4. Crear usuarios y asignar roles
1. Ve a **Microsoft Entra ID → Users → New user** y crea:
   - Un usuario **admin** (p. ej. `admin@tudominio.com`)
   - Un usuario **customer** (p. ej. `cliente@tudominio.com`)
2. Para asignarles los roles de la app:
   - **Enterprise applications** → busca tu app (`ecommerce-frontend`)
   - → **Users and groups** → **+ Add user**
   - Selecciona el usuario **admin** y asígnale el rol **admin**
   - Repite con el usuario **customer** asignándole el rol **customer**

### 5. Permisos de la API
1. En tu app → **API permissions** → **+ Add a permission** → **My APIs** → tu app → marca `access_as_user`
2. Click **Grant admin consent**

### 6. Verificación
- El **Access token** (para la API) incluirá `roles: ["admin"]` o `roles: ["customer"]`
- El **ID token** (para el frontend) también incluirá el claim `roles`
- Considera usar [jwt.ms](https://jwt.ms) para inspeccionar los tokens

## Instalación

### Frontend
```bash
cd frontend
npm install
npm start
# Genera environment.ts desde .env automaticamente y arranca ng serve
```

### Backend (users-service - API privada)
```bash
cd backend/users-service
powershell -ExecutionPolicy Bypass -File run.ps1
# Carga las variables del .env y ejecuta mvn spring-boot:run
```

### Backend (resto de microservicios)
```bash
cd backend/[service-name]
mvn clean install
mvn spring-boot:run
```

## Configuración

### Frontend
Todos los valores de Entra ID se configuran en el archivo `.env` de la raíz (se generan automáticamente en `environment.ts`):
- `ENTRA_CLIENT_ID`: Client ID de tu App Registration
- `ENTRA_AUTHORITY`: Authority URL (login.microsoftonline.com)
- `ENTRA_API_SCOPE`: Scope de la API protegida
- `FRONTEND_REDIRECT_URI`: URI de redirección

### Backend
Los valores de Entra ID se leen del `.env` mediante placeholders (`${ENTRA_AUTHORITY}`, `${ENTRA_API_URI}`, `${USERS_SERVICE_PORT}`) en `application.properties`.

## Seguridad implementada

### APIs
- **API pública**: `products-service` (8081) y `categories-service` (8082) — catálogo sin autenticación
- **API privada**: `users-service` (8083) — requiere **Access token** de Entra ID con rol `admin`

### Roles
- **admin**: acceso completo al panel de administración y a la API de usuarios
- **customer**: usuario normal (sin acceso al panel admin ni a `/api/users`)

### Control de acceso
- **Backend**: validación JWT vía JWKS del tenant + `@PreAuthorize("hasRole('admin')")` en `/api/users`
- **Frontend**: `MsalGuard` (login) + `AdminRoleGuard` (rol admin) en la ruta `/admin`; el menú Admin solo se muestra a administradores

## Criterios de Evaluación

- **Home de productos**: Consumiendo API pública desde API GATEWAY ✅
- **Login**: Funcional con IDaaS ✅
- **Página de administración**: Perfilada y segura ✅
- **CRUD**: Con APIs privadas desde API GATEWAY (access_token) ✅ 
