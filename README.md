# Tuki-Tech

Proyecto del ramo DESARROLLO CLOUD NATIVE I

## Entrega final EFT

- Repositorio: `https://github.com/Pastito-247/CLOUD-NATIVE-I`.
- Página publicada: `https://pastito-247.github.io/CLOUD-NATIVE-I/`.
- Diagrama de arquitectura: [ARQUITECTURA.md](./ARQUITECTURA.md).
- Estado y pendientes: [CHECKLIST_FINAL_EFT.md](./CHECKLIST_FINAL_EFT.md).
- Material de estudio: [ESTUDIO_EFT.md](./ESTUDIO_EFT.md).
- Diapositivas y notas: [PRESENTACION_EFT.md](./PRESENTACION_EFT.md).
- Preguntas y glosario: [PREGUNTAS_Y_TERMINOS_EFT.md](./PREGUNTAS_Y_TERMINOS_EFT.md).
- Guion de presentación: [GUION_PRESENTACION_EFT.md](./GUION_PRESENTACION_EFT.md).
- Guion técnico y comandos: [GUIA_DEFENSA_EFT.md](./GUIA_DEFENSA_EFT.md).
- Paquete limpio (sin `.env` ni llaves `.pem`):
  `powershell -ExecutionPolicy Bypass -File .\scripts\create-delivery-archive.ps1`.

## Arquitectura

### Frontend (Angular + MSAL/EntraID + Cognito)
- **Home** `/`: catálogo público sin token (`/api/public/products`, `/api/public/categories`).
- **Login interno** `/login`: Azure EntraID (MSAL), Authorization Code + PKCE.
- **Acceso público** `/publico`: registro, confirmación, login, recuperación y logout con Cognito.
- **Checkout invitado** `/checkout-invitado` y **seguimiento** `/seguir-pedido`: sin token.
- **Mis compras** `/my-orders`: pedidos del usuario Entra o Cognito según la sesión activa.
- **Admin** `/admin/**`: productos, categorías, roles, RabbitMQ, Kafka, reportes y auditoría (JWT + rol `admin`).

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
- **Report Service** (8087) y **Audit Service** (8088): consumidores Kafka de
  `tuki.order.events` / `tuki.payment.events`, KPIs y trazabilidad en PostgreSQL cloud.
- **Kafka Admin Service** (8089): crear/listar/eliminar tópicos, ver configs, grupos y offsets.

### Infraestructura AWS
- **API Gateway** (`tuki-api`, stage `$default`):
  - Públicas: `/api/public/products/**`, `/api/public/categories/**`, `/api/public/orders/**`, `/api/public/users/**` y `OPTIONS` de todas las rutas.
  - Cliente Cognito: `/api/customer/orders/**` y `/api/customer/payments/**` (authorizer JWT `tuki-cognito`).
  - Protegidas Entra ID: `/api/products/**`, `/api/categories/**`, `/api/users/**`, `/api/orders/**`, `/api/payments/**`, `/api/receipts/**`, `/api/rabbit/**`, `/api/kafka/**`, `/api/reports/**`, `/api/audit/**` (authorizer JWT `tuki-jwt`).
  - CORS con 10 orígenes (incluye `http://localhost:4200` y `https://pastito-247.github.io`), throttling y auto-deploy.
- **EC2**: backend systemd + RabbitMQ en Docker (cluster de 2 nodos en `docker-compose.ec2.yml`).
- **RDS**: PostgreSQL `tuki-postgres`, una base por microservicio JPA.
- **Cognito**: User Pool `tuki-public-users` para registro/login público; Entra ID para Admin/Operador.
- **GitHub Pages**: frontend publicado en `https://pastito-247.github.io/CLOUD-NATIVE-I/`.

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
  4. Registro Cognito confirmado -> perfil de usuario y aviso de bienvenida
     asíncrono (`user.exchange`, `user.registered.queue`).
- **Registro de cuentas:** el registro público se hace en Cognito desde `/publico`
  (email + contraseña + confirmación por código). El antiguo endpoint
  `/api/public/users/register` responde `410 Gone`. Los usuarios internos se
  gestionan desde el tenant Entra ID. Nombres de cola/exchange en
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


### Streaming de eventos (Kafka)
- Cluster local: `docker-compose.kafka.yml` (3 Zookeeper + 3 brokers) o el stack
  completo `docker-compose.full.yml`; Kafka UI en `http://localhost:8080`.
- Tópicos `tuki.order.events` y `tuki.payment.events`: 3 particiones, RF 3,
  `min.insync.replicas=2`, `cleanup.policy=delete` y retention 7 días.
- Tópicos dead-letter `*.DLT`: retention 30 días. Productores DTO en orders y
  payments; consumidores en report y audit con grupos `tuki-report` y
  `tuki-audit`, reintentos (3 x 2 s) y publicación al DLT.
- Administración: `/api/kafka/**` (rol `admin`) para tópicos, configuraciones,
  grupos y offsets. Ver [KAFKA_SETUP.md](./KAFKA_SETUP.md).
- Despliegue cloud en `tuki-backend-ef`: `docker-compose.ec2-kafka.yml` levanta un
  modo reducido (1 Zookeeper + 1 broker, RF 1) con
  `docker/kafka/kafka-init-ec2.sh`; crea los mismos 4 tópicos con 3 particiones,
  `min.insync.replicas=1` y retention de 7/30 días. Los backends de la EC2 usan
  `KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9092`. La UI está en el puerto `8080` de la
  EC2; para verla sin exponerla: `ssh -L 8080:127.0.0.1:8080 -i <clave> ubuntu@<IP_EC2>`.

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

Los archivos `frontend/src/environments/environment.ts` y
`environment.prod.ts` son **generados** y están ignorados por Git: no se editan
a mano. Copia `.env.example` a `.env`, completa los valores y ejecuta:

```bash
npm run generate-env --prefix frontend
```

El script valida las variables obligatorias y genera ambos environments. En CI,
los mismos valores se toman de las Variables del repositorio
(ver [GITHUB_PAGES_DEPLOYMENT.md](./GITHUB_PAGES_DEPLOYMENT.md)).

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

### 0. Stack completo (recomendado para la defensa)
```bash
docker compose -f docker-compose.full.yml up -d --build
docker compose -f docker-compose.full.yml ps
```
Levanta PostgreSQL, RabbitMQ (2 nodos), Kafka (3 ZK + 3 brokers), Kafka UI y los
microservicios. Sin Docker, usa el apartado siguiente y ejecuta cada servicio.

### 0b. Kafka en EC2 (modo reducido, RF 1)
```bash
mkdir -p ~/kafka
cp docker-compose.ec2-kafka.yml ~/kafka/docker-compose.yml
cp docker/kafka/kafka-init-ec2.sh ~/kafka/kafka-init.sh
sed -i 's#./docker/kafka/kafka-init-ec2.sh#./kafka-init.sh#' ~/kafka/docker-compose.yml
cd ~/kafka && docker compose up -d
docker inspect --format '{{.State.Status}} {{.State.ExitCode}}' kafka-init
```
En la EC2 `tuki-backend-ef` el stack ya está desplegado y verificado:
`zookeeper`, `kafka`, `kafka-ui` activos y `kafka-init` con `exited 0`. Los 4
tópicos (`tuki.order.events`, `tuki.payment.events` y sus `*.DLT`) tienen 3
particiones y RF 1. Se agregó un swapfile de 2 GiB para absorber el pico de
memoria del broker en una `t3.medium`.

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
