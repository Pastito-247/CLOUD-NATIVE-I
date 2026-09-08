# E-Commerce Tech

Proyecto del ramo DESARROLLO CLOUD NATIVE I

## Arquitectura

### Frontend (Angular + MSAL/EntraID)
- **Home**: Catálogo de productos públicos (API pública vía API Gateway)
- **Login**: Autenticación con Azure EntraID (MSAL) - flujo Authorization Code + PKCE
- **Admin Dashboard**: Gestión de productos, categorías, usuarios (perfilado por roles)
- **Carrito/Checkout**: Funcionalidades para usuarios autenticados (APIs privadas con JWT)
- **Validación de roles**: admin, customer, staff desde Azure AD

### Backend (Spring Boot Microservicios)
- **Products Service** (puerto 8081): 
  - APIs públicas: `/api/products/public/**` (sin autenticación)
  - APIs privadas: `/api/products/**` (requiere JWT)
  - APIs admin: POST, PUT, DELETE (requiere rol admin)
- **Categories Service** (puerto 8082): 
  - APIs públicas: `/api/categories/public/**`
  - APIs privadas: `/api/categories/**`
  - APIs admin: POST, PUT, DELETE (requiere rol admin)
- **Users Service** (puerto 8083): 
  - APIs privadas: `/api/users/**` (requiere JWT)
  - APIs admin: GET all users, POST, PUT, DELETE (requiere rol admin)
- **Orders Service** (puerto 8084): 
  - APIs privadas: `/api/orders/**` (requiere JWT)
  - APIs admin: GET all orders, PUT, DELETE (requiere rol admin)

### Infraestructura AWS
- **API Gateway**: 
  - Rutas públicas: `/products/public/**`, `/categories/public/**` (sin validación JWT)
  - Rutas privadas: `/products/**`, `/categories/**`, `/users/**`, `/orders/**` (validación JWT)
  - Validación JWT con Azure AD issuer
- **EC2**: Instancias para cada microservicio
- **RDS**: Base de datos MySQL cloud

### IDaaS (Azure EntraID)
- **Roles**: admin, customer, staff
- **Scopes**: `api://ecommerce-tech-api/access_as_user`
- **Flujo**: Authorization Code con PKCE
- **Access Token**: JWT validado en backend y API Gateway

## Instalación

<<<<<<< Updated upstream
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
=======
### Prerrequisitos
- Node.js 20+
- Java 17+
- Maven 3.9+
- Cuenta de Azure AD (EntraID)
- Cuenta de AWS
>>>>>>> Stashed changes

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

<<<<<<< Updated upstream
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
=======
### 1. Azure AD (EntraID)

Sigue la guía completa en [AZURE_AD_SETUP.md](./AZURE_AD_SETUP.md) para:
- Registrar aplicación en Azure AD
- Configurar roles (admin, customer, staff)
- Crear usuarios de prueba (1 por rol)
- Obtener Client ID y Tenant ID

### 2. Frontend

Editar `frontend/src/environments/environment.ts`:

```typescript
export const environment = {
  production: false,
  apiGatewayUrl: 'https://YOUR_API_GATEWAY_URL.execute-api.region.amazonaws.com/prod',
  productsUrl: 'http://localhost:8081/api/products',
  categoriesUrl: 'http://localhost:8082/api/categories',
  usersUrl: 'http://localhost:8083/api/users',
  ordersUrl: 'http://localhost:8084/api/orders',
  
  msalConfig: {
    auth: {
      clientId: 'YOUR_CLIENT_ID',
      authority: 'https://login.microsoftonline.com/YOUR_TENANT_ID',
      redirectUri: 'http://localhost:4200',
      postLogoutRedirectUri: 'http://localhost:4200'
    },
    cache: {
      cacheLocation: 'localStorage',
      storeAuthStateInCookie: false
    }
  },
  
  enableAuth: true,
  scopes: ['api://ecommerce-tech-api/access_as_user']
};
```

### 3. Backend

Editar `src/main/resources/application.properties` en cada servicio:

```properties
# Azure AD Configuration
spring.security.oauth2.resourceserver.jwt.issuer-uri=https://login.microsoftonline.com/YOUR_TENANT_ID/v2.0
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://login.microsoftonline.com/YOUR_TENANT_ID/discovery/v2.0/keys

# CORS Configuration
cors.allowed-origins=http://localhost:4200,https://tu-usuario.github.io/ecommerce-tech/

# Database Configuration
spring.datasource.url=jdbc:mysql://your-database-endpoint:3306/db_name
spring.datasource.username=your_username
spring.datasource.password=your_password
```

## Despliegue

### Frontend a GitHub Pages

1. Sube el código a GitHub
2. Habilita GitHub Pages en el repositorio
3. Configura el source en `gh-pages` branch
4. El workflow `.github/workflows/deploy-pages.yml` se ejecutará automáticamente al hacer push a `main`

### Backend a AWS EC2

1. Crea instancias EC2 para cada microservicio
2. Instala Java y Maven en cada instancia
3. Sube los JAR compilados
4. Ejecuta cada servicio con `java -jar service.jar`
5. Configura Security Groups para permitir tráfico en puertos 8081-8084

### AWS API Gateway

1. Crea un API Gateway REST
2. Configura recursos:
   - `/products/public/**` → Products Service (sin autorizador)
   - `/products/**` → Products Service (con autorizador JWT)
   - `/categories/public/**` → Categories Service (sin autorizador)
   - `/categories/**` → Categories Service (con autorizador JWT)
   - `/users/**` → Users Service (con autorizador JWT)
   - `/orders/**` → Orders Service (con autorizador JWT)
3. Configura autorizador JWT:
   - Issuer: `https://login.microsoftonline.com/YOUR_TENANT_ID/v2.0`
   - Audience: Client ID de la aplicación Azure AD
4. Deploya el API Gateway
>>>>>>> Stashed changes

## Criterios de Evaluación

- **Home de productos**: Consumiendo API pública desde API Gateway ✅
- **Login**: Funcional con EntraID (Azure AD) ✅
- **Página de administración**: Perfilada por roles (admin, customer, staff) ✅
- **CRUD**: Con APIs privadas desde API Gateway (access_token JWT) ✅
- **Validación JWT**: En backend y API Gateway ✅
- **CORS**: Configurado en backend ✅
- **GitHub Actions**: Workflow para deploy a GitHub Pages ✅
- **Usuarios de prueba**: 1 por rol en Azure AD ✅

## Estructura del Proyecto

```
CLOUD-NATIVE-I/
├── frontend/                    # Angular Frontend
│   ├── src/
│   │   ├── app/
│   │   │   ├── admin/          # Dashboard admin (requiere rol admin)
│   │   │   ├── home/           # Catálogo público
│   │   │   ├── login/          # Login con EntraID
│   │   │   └── app.module.ts   # Configuración MSAL
│   │   └── environments/
│   │       ├── environment.ts  # Configuración desarrollo
│   │       └── environment.prod.ts
│   ├── .github/workflows/
│   │   └── deploy-pages.yml    # GitHub Actions
│   └── package.json
├── backend/                     # Spring Boot Microservicios
│   ├── products-service/       # Puerto 8081
│   │   ├── src/main/java/com/ecommerce/products/
│   │   │   ├── config/         # SecurityConfig (JWT, CORS)
│   │   │   ├── controller/     # REST APIs (públicas/privadas)
│   │   │   ├── model/          # Entity Product
│   │   │   ├── repository/     # JPA Repository
│   │   │   └── service/        # Business Logic
│   │   └── src/main/resources/
│   │       └── application.properties
│   ├── categories-service/      # Puerto 8082
│   ├── users-service/          # Puerto 8083
│   └── orders-service/         # Puerto 8084
├── AZURE_AD_SETUP.md           # Guía configuración Azure AD
└── README.md
```

## Endpoints

### APIs Públicas (sin autenticación)
- `GET /api/products/public` - Listar todos los productos
- `GET /api/products/public/{id}` - Obtener producto por ID
- `GET /api/products/public/category/{category}` - Productos por categoría
- `GET /api/categories/public` - Listar todas las categorías
- `GET /api/categories/public/{id}` - Obtener categoría por ID

### APIs Privadas (requieren JWT)
- `GET /api/products` - Listar productos
- `GET /api/products/{id}` - Obtener producto
- `GET /api/categories` - Listar categorías
- `GET /api/categories/{id}` - Obtener categoría
- `GET /api/users/{id}` - Obtener usuario
- `GET /api/users/me` - Usuario actual
- `GET /api/orders/{id}` - Obtener pedido
- `GET /api/orders/my-orders` - Pedidos del usuario actual
- `POST /api/orders` - Crear pedido

### APIs Admin (requieren rol admin)
- `POST /api/products` - Crear producto
- `PUT /api/products/{id}` - Actualizar producto
- `DELETE /api/products/{id}` - Eliminar producto
- `POST /api/categories` - Crear categoría
- `PUT /api/categories/{id}` - Actualizar categoría
- `DELETE /api/categories/{id}` - Eliminar categoría
- `GET /api/users` - Listar todos los usuarios
- `POST /api/users` - Crear usuario
- `PUT /api/users/{id}` - Actualizar usuario
- `DELETE /api/users/{id}` - Eliminar usuario
- `GET /api/orders` - Listar todos los pedidos
- `PUT /api/orders/{id}` - Actualizar pedido
- `DELETE /api/orders/{id}` - Eliminar pedido

## Pruebas Locales

### 1. Iniciar microservicios
```bash
# Terminal 1
cd backend/products-service
mvn spring-boot:run

# Terminal 2
cd backend/categories-service
mvn spring-boot:run

# Terminal 3
cd backend/users-service
mvn spring-boot:run

# Terminal 4
cd backend/orders-service
mvn spring-boot:run
```

### 2. Iniciar frontend
```bash
cd frontend
npm start
```

### 3. Probar APIs públicas
```bash
curl http://localhost:8081/api/products/public
curl http://localhost:8082/api/categories/public
```

### 4. Probar APIs privadas (requiere token)
```bash
# Primero obtén el access token desde el frontend
# Luego úsalo en el header Authorization
curl -H "Authorization: Bearer YOUR_ACCESS_TOKEN" http://localhost:8081/api/products
```

## Solución de Problemas

Ver [AZURE_AD_SETUP.md](./AZURE_AD_SETUP.md) para problemas comunes de configuración de Azure AD. 
