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

### Frontend
Editar `src/environments/environment.ts` con:
- `apiGatewayUrl`: URL de tu API Gateway
- `msalConfig.auth.clientId`: Client ID de Azure AD
- `msalConfig.auth.authority`: Authority URL de Azure AD
- `msalConfig.auth.redirectUri`: URI de redirección

### Backend
Editar `src/main/resources/application.properties` en cada servicio con:
- `spring.datasource.url`: URL de tu base de datos RDS
- `spring.datasource.username`: Usuario de base de datos
- `spring.datasource.password`: Contraseña de base de datos

## Criterios de Evaluación

- **Home de productos**: Consumiendo API pública desde API GATEWAY ✅
- **Login**: Funcional con IDaaS ✅
- **Página de administración**: Perfilada y segura ✅
- **CRUD**: Con APIs privadas desde API GATEWAY (access_token) ✅ 
