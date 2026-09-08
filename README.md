# Tuki-Tech

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
- **Scopes**: `api://tuki-tech-api/access_as_user`
- **Flujo**: Authorization Code con PKCE
- **Access Token**: JWT validado en backend y API Gateway

## Instalación

### Prerrequisitos
- Node.js 20+
- Java 17+
- Maven 3.9+
- Cuenta de Azure AD (EntraID)
- Cuenta de AWS

### Frontend
```bash
cd frontend
npm install
npm start
```

### Backend (Cada microservicio)
```bash
cd backend/[service-name]
mvn clean install
mvn spring-boot:run
```

## Configuración

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
  scopes: ['api://tuki-tech-api/access_as_user']
};
```

### 3. Backend

Editar `src/main/resources/application.properties` en cada servicio:

```properties
# Azure AD Configuration
spring.security.oauth2.resourceserver.jwt.issuer-uri=https://login.microsoftonline.com/YOUR_TENANT_ID/v2.0
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://login.microsoftonline.com/YOUR_TENANT_ID/discovery/v2.0/keys

# CORS Configuration
cors.allowed-origins=http://localhost:4200,https://tu-usuario.github.io/tuki-tech/

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
