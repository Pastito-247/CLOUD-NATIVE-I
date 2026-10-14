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
  - Pedidos para revisión (staff/admin): `/api/orders/review/**`
- **Payments Service** (puerto 8086): 
  - API de pago: `POST /api/payments` (requiere JWT)
  - Boletas: `/api/receipts/**` (consulta y descarga del PDF)
  - Orquesta el flujo asíncrono de pago y generación de boleta

### Infraestructura AWS
- **API Gateway**: 
  - Rutas públicas: `/api/public/products/**`, `/api/public/categories/**`, `/api/public/users/**` (sin validación JWT)
  - Rutas privadas: `/api/products/**`, `/api/categories/**`, `/api/users/**`, `/api/orders/**`, `/api/rabbit/**`, `/api/payments/**`, `/api/receipts/**` (validación JWT)
  - Validación JWT con Azure AD issuer
- **EC2**: Instancias para cada microservicio
- **RDS**: Base de datos MySQL cloud

### IDaaS (Azure EntraID)
- **Roles**: admin, customer, staff
- **Scopes**: `api://tuki-tech-api/access_as_user`
- **Flujo**: Authorization Code con PKCE
- **Access Token**: JWT validado en backend y API Gateway

### Mensajería asíncrona (RabbitMQ)
- **Payments Service** (puerto 8086): expone la API de pago
  (`POST /api/payments`) y orquesta el flujo asíncrono. No hay pasarela real:
  el pago se **simula** con un retardo (cola de pantalla) y al procesarse genera
  una **boleta en PDF** (OpenPDF).
- **Flujo de pago (2 exchanges fanout con DLQ):**
  1. `POST /api/payments` -> `payment.exchange` (fanout) ->
     - `payment.notification.queue` (users-service): aviso "pago en proceso".
     - `payment.screen.queue` (payments-service): simula el pago (retardo) y
       genera la boleta PDF.
  2. Al procesar el pago -> `receipt.exchange` (fanout) ->
     - `receipt.email.queue` (users-service): correo de confirmación + boleta.
     - `order.review.queue` (orders-service): pedido para revisión de
       staff/administrador.
     - `stock.decrement.queue` (products-service): descuento de stock.
  3. `POST /api/orders` -> `order.exchange` (fanout) -> `order.created.queue`
     (users-service): aviso "pedido recibido".
  4. `POST /api/public/users/register` -> `user.exchange` (fanout) ->
     `user.registered.queue` (users-service): correo de bienvenida (async).
- **Registro de cuentas:** endpoint público `POST /api/public/users/register`
  (crea el perfil en users-service, rol por defecto `customer`) y página
  Angular `/register`. Los nombres de cola/exchange se centralizan en
  `application.yml` (`messaging.*`).
- **Dead Letter Queues (DLQ):** cada cola tiene `x-dead-letter-exchange` hacia
  `payment.dlx` / `receipt.dlx` / `order.dlx` / `user.dlx`; los mensajes que
  agotan reintentos se registran en logs por los `DeadLetterLogConsumer`.
- **Catálogo** (products-service / categories-service): se mantiene la
  sincronización vía `catalog.exchange` (topic).
- **RabbitMQ Admin Service** (puerto 8085): administra colas, exchanges y
  bindings vía API REST (`/api/rabbit/**`, requiere rol admin).
- Config centralizada en `application.yml` (`messaging.*`) con beans
  `Queue`/`Exchange`/`Binding`. Ver [RABBITMQ_SETUP.md](./RABBITMQ_SETUP.md).


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

Cada servicio tiene su configuración en `src/main/resources/application.yml`
(JWT de Azure AD, CORS y mensajería). Ajusta el emisor/audiencia:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://login.microsoftonline.com/YOUR_TENANT_ID/v2.0
          audiences: api://tuki-tech-api
cors:
  allowed-origins: "http://localhost:4200,https://pastito-247.github.io"
```

### 4. RabbitMQ

Las credenciales se inyectan por variables de entorno (CloudAMQP o instancia
existente). Ejemplo:

```bash
RABBITMQ_HOST=<instancia>.rmq.cloudamqp.com
RABBITMQ_PORT=5671
RABBITMQ_SSL=true
RABBITMQ_USERNAME=<usuario>
RABBITMQ_PASSWORD=<password>
RABBITMQ_VHOST=<vhost>
RABBITMQ_MANAGEMENT_URL=https://<instancia>.rmq.cloudamqp.com
```

Detalle completo de la topología, DLQ y endpoints en
[RABBITMQ_SETUP.md](./RABBITMQ_SETUP.md).

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
5. Configura Security Groups para permitir tráfico en puertos 8081-8086

### AWS API Gateway

Sigue la guía completa en [API_GATEWAY_DEPLOYMENT.md](./API_GATEWAY_DEPLOYMENT.md).
Resumen (HTTP API de AWS):

1. Crea una **HTTP API** con integraciones HTTP al EC2:
   - `/api/public/products/{proxy+}` → 8081 (sin autorizador)
   - `/api/public/categories/{proxy+}` → 8082 (sin autorizador)
   - `/api/{products|categories|users|orders}/{proxy+}` → 8081-8084 (con autorizador JWT)
   - `/api/rabbit/{proxy+}` → 8085 (con autorizador JWT + rol admin)
2. Configura el **JWT authorizer**:
   - Issuer: `https://login.microsoftonline.com/YOUR_TENANT_ID/v2.0`
   - Audience: `api://tuki-tech-api` (Application ID URI de la API)
3. Configura **CORS** en la API (origen de GitHub Pages) y vacía
   `CORS_ALLOWED_ORIGINS` en el backend para no duplicar headers.
4. Deploya y usa la URL de invocación como `PROD_BACKEND_URL`.

## Criterios de Evaluación

- **Home de productos**: Consumiendo API pública desde API Gateway ✅
- **Login**: Funcional con EntraID (Azure AD) ✅
- **Página de administración**: Perfilada por roles (admin, customer, staff) ✅
- **CRUD**: Con APIs privadas desde API Gateway (access_token JWT) ✅
- **Validación JWT**: En backend y API Gateway ✅
- **CORS**: Configurado en backend ✅
- **GitHub Actions**: Workflow para deploy a GitHub Pages ✅
- **Usuarios de prueba**: 1 por rol en Azure AD ✅

### Entrega 2 (RabbitMQ)
- **Nombres centralizados**: `application.yml` + `@ConfigurationProperties` ✅
- **Beans Queue/Exchange/Binding**: en clases `RabbitMQConfig` ✅
- **Flujo de pago (fanout)**: `payment.requested`/`receipt.generated` →
  notificación, simulación con boleta PDF, correo, revisión de pedido y descuento
  de stock ✅
- **Catálogo**: `product.*`/`category.*` entre products-service y
  categories-service ✅
- **ACK/NACK explícito y DLQ**: reintentos, NACK sin requeue y logs de DLQ ✅
- **Microservicio administrador**: `rabbitmq-admin-service` con API REST validada ✅
- **Vista frontend**: `/admin/rabbitmq` para gestionar colas, exchanges y bindings ✅

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
│   ├── products-service/       # Puerto 8081 (productor product.* + consumidor category.*)
│   │   ├── src/main/java/com/ecommerce/products/
│   │   │   ├── config/         # SecurityConfig (JWT, CORS) + RabbitMQConfig
│   │   │   ├── controller/     # REST APIs (públicas/privadas)
│   │   │   ├── event/          # Contratos de eventos (product.*, category.*)
│   │   │   ├── messaging/      # Productor y consumidores
│   │   │   ├── model/          # Entity Product
│   │   │   ├── repository/     # JPA Repository
│   │   │   └── service/        # Business Logic
│   │   └── src/main/resources/
│   │       └── application.yml # Config centralizada (messaging.*)
│   ├── categories-service/      # Puerto 8082 (productor category.* + consumidor product.*)
│   ├── users-service/          # Puerto 8083 (consumidor: aviso de pago + correo boleta)
│   ├── orders-service/         # Puerto 8084 (consumidor: pedidos para revisión)
│   ├── payments-service/       # Puerto 8086 (API de pago + boleta PDF, exchanges fanout)
│   └── rabbitmq-admin-service/ # Puerto 8085 (administración de RabbitMQ)
├── RABBITMQ_SETUP.md           # Guía de mensajería asíncrona (Entrega 2)
├── AZURE_AD_SETUP.md           # Guía configuración Azure AD
├── EC2_DEPLOYMENT.md           # Guía despliegue backend a EC2
├── API_GATEWAY_DEPLOYMENT.md   # Guía despliegue AWS API Gateway
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
- `POST /api/payments` - Registrar un pago (inicia el flujo asíncrono)
- `GET /api/payments/my-payments` - Pagos del usuario actual
- `GET /api/receipts/payment/{paymentId}` - Boleta de un pago
- `GET /api/receipts/{id}/pdf` - Descargar la boleta PDF

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
- `GET /api/orders/review` - Pedidos para revisión (admin/staff)
- `GET /api/orders/review/pending` - Pedidos pendientes de revisión
- `GET /api/payments` - Listar todos los pagos

### API de administración de RabbitMQ (requieren rol admin)
- `POST /api/rabbit/queues` - Crear cola
- `GET /api/rabbit/queues` - Listar colas
- `GET /api/rabbit/queues/{name}` - Detalle de una cola
- `DELETE /api/rabbit/queues/{name}` - Eliminar cola
- `POST /api/rabbit/exchanges` - Crear exchange
- `GET /api/rabbit/exchanges` - Listar exchanges
- `DELETE /api/rabbit/exchanges/{name}` - Eliminar exchange
- `POST /api/rabbit/bindings` - Crear binding
- `GET /api/rabbit/bindings` - Listar bindings
- `DELETE /api/rabbit/bindings` - Eliminar binding

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

# Terminal 5
cd backend/payments-service
mvn spring-boot:run

# Terminal 6
cd backend/rabbitmq-admin-service
mvn spring-boot:run
```

> Alternativa (Windows): `.\scripts\run-backend.ps1 payments-service` carga
> automáticamente las variables del `.env` raíz (incluidas las credenciales de
> RabbitMQ/CloudAMQP) antes de arrancar.

### 2. Probar el flujo de pago (asíncrono)
```bash
curl -X POST http://localhost:8086/api/payments \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" -H "Content-Type: application/json" \
  -d '{"userEmail":"cliente@demo.cl","amount":19990,"method":"CREDIT_CARD","shippingAddress":"Av. Siempre Viva 742","items":[{"productId":1,"productName":"Teclado","quantity":2,"unitPrice":9995}]}'
```
Provoca: aviso de "pago en proceso", simulación con boleta PDF, correo de
confirmación, pedido para revisión y descuento de stock. Usa `fail@demo.cl` para
ver la DLQ.

### 3. Iniciar frontend
```bash
cd frontend
npm start
```

### 4. Probar APIs públicas
```bash
curl http://localhost:8081/api/products/public
curl http://localhost:8082/api/categories/public
```

> products y categories se **auto-siembran** con datos demo al arrancar si la
> base está vacía (6 categorías + 19 productos). No necesitas seed manual.

### 5. Probar APIs privadas (requiere token)
```bash
# Primero obtén el access token desde el frontend
# Luego úsalo en el header Authorization
curl -H "Authorization: Bearer YOUR_ACCESS_TOKEN" http://localhost:8081/api/products
```

## Solución de Problemas

Ver [AZURE_AD_SETUP.md](./AZURE_AD_SETUP.md) para problemas comunes de configuración de Azure AD. 
