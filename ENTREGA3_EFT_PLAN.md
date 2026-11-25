# EFT / Entrega 3 — Plan técnico y backlog

> Documento de trabajo para la rama `entrega-3`.
> Objetivo: cerrar la brecha entre el estado actual (Entrega 2) y la pauta del
> Examen Final Transversal (40%), con foco en Kafka, API Manager, RabbitMQ,
> federación pública con Cognito, transacciones guest y BD cloud.
>
> Fecha: 2026-11-16

---

## 1. Decisiones tomadas

| Tema | Decisión |
|---|---|
| Rama | `entrega-3` (creada desde `main` en `d7f3306`) |
| Región AWS | `us-east-1` (la misma del API Gateway y el EC2 actuales) |
| API Manager | **AWS API Gateway HTTP API existente**: `https://3cbwduklgb.execute-api.us-east-1.amazonaws.com` |
| Identidad interna | Microsoft Entra ID + MSAL (se mantiene) |
| Identidad pública | **AWS Cognito User Pool** (nuevo) |
| Base de datos cloud | **RDS PostgreSQL** (reemplaza H2 en producción) |
| Kafka | Clúster local con Docker Compose para la demo (3 ZK + 3 brokers + UI) |
| Despliegue cloud | Frontend en GitHub Pages + backend en EC2 + RDS + API Gateway |
| Estrategia de demo | Dos entornos: **local** para el flujo E2E con Kafka y **nube** para el criterio de despliegue/API Manager |

### Nota sobre la topología de demo

La pauta pide "todo levantable con Docker Compose, desplegado en la nube" y un
flujo end-to-end `acción → evento Kafka → reporte/auditoría`. Con la decisión de
mantener Kafka local para no pagar un EC2 más grande, la defensa se divide así:

- **Demo E2E local**: `docker compose -f docker-compose.full.yml up` levanta
  PostgreSQL, RabbitMQ (2 nodos), Kafka (3 ZK + 3 brokers), Kafka UI y todos los
  microservicios. Aquí corre el guion completo de 10 minutos.
- **Demo cloud**: se muestra GitHub Pages + API Gateway + EC2 + RDS con las
  rutas públicas y protegidas, validación JWT y login interno.
- **Upgrade opcional**: si el presupuesto lo permite, mover Kafka al EC2
  (`t3.large`) y unificar todo en un solo entorno. El compose debe quedar
  preparado para ese caso.

---

## 2. Estado actual verificado

### 2.1 Lo que ya existe y funciona

| Componente | Detalle |
|---|---|
| Microservicios | 6 Spring Boot 3.2 / Java 17: products (8081), categories (8082), users (8083), orders (8084), rabbitmq-admin (8085), payments (8086) |
| Frontend | Angular 17, MSAL, GitHub Pages, carrito, checkout, admin, mis compras |
| Login interno | Entra ID (tenant `e3e92dfe-...`), JWT validado en cada servicio |
| Roles | `admin`, `staff`, `customer` (claim `roles`) |
| RabbitMQ | 2 nodos en `docker-compose.yml`, fanout de negocio + DLX direct + DLQ, logs de no entregados, admin de colas/exchanges/bindings |
| Colas actuales | `order.created`, `order.review`, `payment.notification`, `payment.screen`, `receipt.email`, `stock.decrement`, `product.sync`, `category.sync` (cada una con su DLQ) |
| API Gateway | Instancia HTTP API `3cbwduklgb` en `us-east-1`; rutas privadas responden `401` sin token |
| Despliegue | Backend en EC2 (`http://184.195.234.120`), frontend en Pages |
| Documentación | README, RABBITMQ_SETUP, EC2_DEPLOYMENT, API_GATEWAY_DEPLOYMENT, guías de demo |

### 2.2 Brechas frente a la pauta

| Requisito | Estado | Acción |
|---|---|---|
| F01-F04 login interno, JWT, roles | ✅ | Mantener; roles corregidos en Cognito |
| F05-F08 entidad principal | ✅ | Ítems de pedido y ownership agregados |
| F09-F10 entidad secundaria | ✅ | Sin cambios mayores |
| F11-F13 RabbitMQ + DLQ + log | ✅ | Topología de comandos y demo de fallo implementadas |
| F14-F17 eventos Kafka, KPIs, auditoría, reacción | ✅ | Productores, consumidores, report y audit implementados |
| F18 admin RabbitMQ | ✅ | Sin cambios |
| F19-F21 admin/supervisión/resiliencia Kafka | ✅ | `kafka-admin-service`, Kafka UI y DLT implementados |
| F22 API Manager | ✅ | Authorizers, rutas, CORS y throttling aplicados |
| F23 despliegue | ✅ | EC2 + RDS + nginx + API Gateway verificados |
| F24-F28 Cognito (registro, confirmación, login, rol, forgot, logout) | ✅ | User Pool, cliente, grupo y usuario demo creados |
| F29 catálogo público | ✅ | Mantener |
| F30-F33 transacción guest, consulta por código, límites, convivencia | ✅ | Endpoints y pantallas implementados |
| T01 IDaaS interno | ✅ | Mantener |
| T02-T05 Cognito, tokens, rutas, roles | ✅ | Multi-issuer y `ROLE_customer` forzado |
| T06-T08 Kafka 3+3, tópicos, UI | ✅ | `docker-compose.kafka.yml` y script de tópicos |
| T09 RabbitMQ 2 nodos, 3 colas + DLQ, direct + topic | ✅ | `DemoTopologyConfig` + endpoints demo |
| T10 despliegue cloud | ✅ | RDS, EC2, nginx y API Gateway operativos |
| T11-T17 Kafka en código | ✅ | Productores, consumidores y DLT implementados |
| BD cloud | ✅ | RDS PostgreSQL con 7 bases creadas |
| Seguridad | ✅ | Registro admin eliminado y consultas por CustomerUserDetails |
| CI/tests backend | ✅ | `.github/workflows/backend-ci.yml` con 9 servicios |

### 2.3 Riesgos detectados que hay que corregir sí o sí

1. **Auto-registro con rol Administrador** (`register.component.html`): un
   visitante puede crearse como admin. Debe desaparecer; el registro público
   pasa a Cognito y siempre obtiene rol `customer`.
2. **IDOR**: `GET /api/orders/{id}`, `GET /api/orders/user/{email}`,
   `GET /api/payments/**` y `GET /api/users/**` no verifican ownership.
   `POST /orders` y `POST /payments` confían en el `userEmail` del body.
3. **H2 en memoria**: los datos se pierden al reiniciar; no cumple "BD cloud".
4. **EC2 caído**: el API Gateway devuelve `503` en rutas públicas.
5. **Roles inconsistentes**: el selector del admin usa `admin/user/seller`
   mientras el backend usa `admin/customer/staff`.

---

## 3. Arquitectura objetivo

```
┌──────────────────────────────┐
│  GitHub Pages (Angular 17)   │
│  Login interno: MSAL/Entra   │
│  Login público: Cognito SDK  │
└──────────────┬───────────────┘
               │ HTTPS
┌──────────────▼───────────────┐
│  AWS API Gateway (HTTP API)  │
│  tuki-jwt      -> Entra      │
│  tuki-cognito  -> Cognito    │
│  Rutas /api/public/**  (sin) │
│  Rutas /api/customer/** (Cog)│
│  Rutas /api/**         (Entra)│
└──────┬───────────┬───────────┘
       │           │
┌──────▼─────┐ ┌───▼──────────┐
│ ms-orders  │ │ ms-products  │ ... (6 servicios core)
│ ms-payments│ │ ms-categories│
│ ms-users   │ │ ms-rabbit    │
└──┬──────┬──┘ └──────────────┘
   │      │
   │      └──────────────► Kafka (3 ZK + 3 brokers)
   │                         topics: tuki.order.events
   │                                 tuki.payment.events
   │                         DLT:    *.DLT
   │                           │
   │                    ┌──────▼───────┐   ┌──────────────┐
   │                    │ ms-report    │   │ ms-audit     │
   │                    │ KPIs en vivo │   │ trazabilidad │
   │                    └──────────────┘   └──────────────┘
   │
   └──────────────► RabbitMQ (2 nodos)
                     tuki.commands (direct) -> 3 colas + DLQ
                     tuki.catalog.events (topic)
                     tuki.dlx (direct)

RDS PostgreSQL: una instancia, una base por servicio
```

### Servicios nuevos

| Servicio | Puerto | Responsabilidad |
|---|---|---|
| `report-service` | 8087 | Consumidor Kafka (grupo `tuki-report`), KPIs en tiempo real y API `/api/reports/**` |
| `audit-service` | 8088 | Consumidor Kafka (grupo `tuki-audit`), auditoría con filtros y API `/api/audit/**` |
| `kafka-admin-service` | 8089 | AdminClient: crear/listar/borrar tópicos, configs, consumer groups y lag |

> Si el EC2 no aguanta 9 JVMs, `report-service` y `audit-service` pueden
> consolidarse en un solo JVM con dos `@KafkaListener` y dos consumer groups.
> La demo local sí puede correr los 3.

---

## 4. Diseño por componente

### 4.1 Kafka — infraestructura (T06, T07, T08, T15)

Archivo nuevo: `docker-compose.kafka.yml` (incluido desde
`docker-compose.full.yml`).

- 3 Zookeeper (`zookeeper1..3`) con `ZOO_SERVERS` en clúster.
- 3 brokers (`kafka1..3`) con `KAFKA_BROKER_ID`, `KAFKA_ZOOKEEPER_CONNECT`,
  `KAFKA_ADVERTISED_LISTENERS`, `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=3`,
  `KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=3`,
  `KAFKA_DEFAULT_REPLICATION_FACTOR=3`, `KAFKA_MIN_INSYNC_REPLICAS=2`.
- `kafka-ui` (provectuslabs/kafka-ui) apuntando a los 3 brokers.
- `kafka-init` (contenedor one-shot) que crea:
  - `tuki.order.events` — 3 particiones, RF 3, `retention.ms=604800000` (7 días),
    `cleanup.policy=delete`, `min.insync.replicas=2`.
  - `tuki.payment.events` — 3 particiones, RF 3, misma retención.
  - `tuki.order.events.DLT` y `tuki.payment.events.DLT` — 3 particiones, RF 3,
    retención 30 días.
- Healthchecks y volúmenes.
- Documentar memoria: Docker Desktop necesita ~6 GB para 6 contenedores Kafka.

**Criterio de aceptación:** `docker exec kafka1 kafka-topics.sh --describe` muestra
2 tópicos con 3 particiones y RF 3; Kafka UI lista brokers, tópicos, offsets y lag.

### 4.2 Kafka — código (T11-T17)

#### Dependencias

Agregar a `orders-service`, `payments-service`, `report-service`,
`audit-service` y `kafka-admin-service`:

```xml
<dependency>
  <groupId>org.springframework.kafka</groupId>
  <artifactId>spring-kafka</artifactId>
</dependency>
```

#### DTOs de evento (T12)

Paquete `com.ecommerce.<servicio>.event.kafka`:

```java
public record OrderEvent(
    String eventId,        // UUID
    String eventType,      // ORDER_CREATED, ORDER_CONFIRMED, ORDER_SHIPPED, ORDER_DELIVERED, ORDER_CANCELLED
    Long orderId,
    String trackingCode,
    String userEmail,      // null si es guest
    boolean guest,
    BigDecimal total,
    String status,
    Instant occurredAt,
    String source,         // orders-service
    Map<String, Object> metadata
) {}
```

`PaymentEvent` análogo: `eventId`, `eventType` (`PAYMENT_APPROVED`,
`PAYMENT_REJECTED`), `orderId`, `amount`, `occurredAt`, `source`.

Serialización: `JsonSerializer` / `JsonDeserializer` con
`spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer`
y `spring.json.add.type.headers=false` (para no acoplar paquetes entre servicios).

#### Productores

- `orders-service`:
  - `OrderEventPublisher` publica en `tuki.order.events` con key = `orderId`
    (garantiza orden por pedido).
  - Se invoca después de persistir, tanto en pedidos autenticados como guest.
  - Si Kafka está caído, la transacción HTTP **no** debe fallar: log de error y
    `KafkaTemplate` con timeout corto (la consistencia fina no es el foco del curso).
- `payments-service`: publica `PaymentEvent` en `tuki.payment.events`.

#### Consumidores (T13, T14)

- `report-service`:
  - `@KafkaListener(topics = {"tuki.order.events","tuki.payment.events"}, groupId = "tuki-report")`.
  - Mantiene KPIs en PostgreSQL (tabla `report_kpi`) o en memoria + endpoint:
    - pedidos totales, pedidos por estado, ingresos, ticket promedio,
      pedidos guest vs autenticados, pagos aprobados/rechazados.
  - Endpoint `GET /api/reports/summary` y `GET /api/reports/orders-by-status`.
- `audit-service`:
  - `@KafkaListener(..., groupId = "tuki-audit")`.
  - Persiste `AuditRecord` (eventId, eventType, orderId, actor, source,
    occurredAt, payload, correlationId).
  - Endpoint `GET /api/audit?eventType=&actor=&from=&to=&page=` con filtros y
    paginación.
- **Reacción en tiempo real (F17)**: `audit-service` o `report-service` puede
  marcar el pedido como `CONFIRMED` al recibir `PAYMENT_APPROVED`, o disparar
  una notificación Rabbit. Documentar la reacción elegida.

#### Manejo de errores (T16, F21)

- `DefaultErrorHandler` con `FixedBackOff(2000, 3)`.
- `DeadLetterPublishingRecoverer` hacia `<topic>.DLT` conservando headers
  originales + `kafka_dlt-exception-*`.
- Log explícito con `eventId`, tópico, partición y offset.
- Prueba de demo: publicar un JSON inválido a mano y mostrar el mensaje en el DLT
  desde Kafka UI.

#### Admin Kafka (T17, F19, F20)

`kafka-admin-service` con `AdminClient`:

- `GET /api/kafka/topics` — nombre, particiones, RF, configs.
- `POST /api/kafka/topics` — crear tópico (particiones, RF, retention).
- `PUT /api/kafka/topics/{name}/config` — modificar retention/cleanup.
- `DELETE /api/kafka/topics/{name}`.
- `GET /api/kafka/groups` — consumer groups, offsets, lag por partición.
- Seguridad: solo `ROLE_admin` (Entra).
- Frontend: nueva pantalla `/kafka-admin` (o pestaña en el admin actual) con
  tabla de tópicos y acción de crear/borrar, más link a Kafka UI.

### 4.3 Cognito — federación pública (F24-F28, T02-T05)

#### Configuración AWS

1. User Pool `tuki-public-users` en `us-east-1`.
   - Sign-in: email. Auto-verify email. MFA off (para demo).
   - Password policy: mínimo 8, mayúscula, minúscula, número.
   - Atributos: `email` obligatorio, `name` estándar.
2. App client `tuki-web`:
   - Sin secret (SPA).
   - Auth flows: `ALLOW_USER_SRP_AUTH`, `ALLOW_REFRESH_TOKEN_AUTH`.
   - Callback/sign-out: `http://localhost:4200`, `https://pastito-247.github.io/CLOUD-NATIVE-I`.
3. Grupo `customer` (opcional, ver rol).
4. (Opcional) Lambda `PostConfirmation` para agregar al grupo `customer`.
   Si no se usa Lambda, el backend asigna `ROLE_customer` a todo token Cognito
   que no traiga un grupo admin/staff.

> **Límite de correos**: Cognito en modo por defecto envía ~50 emails/día. Para
> la demo usar cuentas de prueba o configurar SES. Documentarlo.

#### Frontend

- Dependencia: `amazon-cognito-identity-js` (liviana; no hace falta Amplify).
- `PublicAuthService`:
  - `signUp(email, password, name)` → `signUp` + `confirmSignUp(code)`.
  - `signIn(email, password)` → guarda `accessToken`, `idToken`, `refreshToken`.
  - `forgotPassword(email)` / `confirmForgotPassword(code, newPassword)`.
  - `signOut()` → limpia localStorage y estado.
  - `getAccessToken()` y `getEmail()`.
- `AuthService` fachada con `mode: 'internal' | 'public' | null`.
- Pantallas:
  - `/login`: dos pestañas "Empresa" (MSAL) y "Cliente" (Cognito).
  - `/registro`: formulario email/nombre/contraseña + confirmación por código.
  - `/recuperar`: solicitar código + nueva contraseña.
  - Header: muestra "Hola, <email>" y "Salir" para ambos modos.
- Interceptor:
  - Mantener `MsalInterceptor` solo para URLs internas.
  - Agregar `CognitoInterceptor` para `customerOrdersUrl` y rutas de cliente.
- Guards:
  - `/admin`, `/kafka-admin`, `/rabbitmq-admin`: requieren rol admin (Entra).
  - `/mis-compras`: requiere modo public o internal customer.
- Quitar el select de rol del registro y cualquier intento de crear admin.

#### Backend (multi-issuer)

Cada servicio que atienda rutas de cliente (`orders`, `payments`, `users`,
`receipts`) debe aceptar dos emisores:

```java
@Bean
SecurityFilterChain filterChain(HttpSecurity http) {
  http.oauth2ResourceServer(o -> o.authenticationManagerResolver(
      new JwtIssuerAuthenticationManagerResolver(
          issuer -> {
            if (issuer.equals(entraIssuer)) return entraManager;
            if (issuer.equals(cognitoIssuer)) return cognitoManager;
            return null; // rechaza
          })));
}
```

- `entraManager`: issuer Entra + audience `api://tuki-tech-api`.
- `cognitoManager`: issuer `https://cognito-idp.us-east-1.amazonaws.com/<poolId>`
  + audience `<appClientId>`.
- Converter de roles:
  - Entra: claim `roles` → `ROLE_admin|staff|customer`.
  - Cognito: claim `cognito:groups` si existe; si no, `ROLE_customer`.
  - **Un token Cognito nunca puede obtener `ROLE_admin`**.
- Nuevo prefijo de rutas de cliente: `/api/customer/**` (ver 4.4).
- `/api/public/**`: `permitAll`.

#### Variables nuevas

```
COGNITO_REGION=us-east-1
COGNITO_USER_POOL_ID=us-east-1_xxxxxxx
COGNITO_CLIENT_ID=xxxxxxxxxxxxxxxxxxxxxxxxxx
COGNITO_ISSUER=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_xxxxxxx
COGNITO_JWKS_URI=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_xxxxxxx/.well-known/jwks.json
```

### 4.4 Usuarios públicos y transacciones guest (F29-F33)

#### Modelo de datos

`orders-service`:

- `Order` agrega:
  - `trackingCode` (único, `TUKI-XXXXXXXX`).
  - `guest` (boolean).
  - `customerName`, `customerEmail` (para guest).
  - `userEmail` pasa a nullable (pedidos guest no tienen cuenta).
  - `items` (`@OneToMany`).
- Nueva entidad `OrderItem`: `id`, `productId`, `productName`, `unitPrice`,
  `quantity`, `subtotal`.
- `OrderStatusHistory` (opcional, recomendado para auditoría): estado anterior,
  nuevo, actor, timestamp.

Migrar datos existentes no aplica (H2 en memoria); con RDS se parte limpio.

#### Endpoints nuevos

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| GET | `/api/public/products` | sin token | Catálogo (ya existe) |
| GET | `/api/public/categories` | sin token | Categorías (ya existe) |
| POST | `/api/public/orders` | sin token | Crear pedido guest |
| GET | `/api/public/orders/{trackingCode}` | sin token | Estado acotado del pedido |
| GET | `/api/customer/orders` | Cognito `customer` | Mis pedidos |
| GET | `/api/customer/orders/{id}` | Cognito `customer` | Detalle propio (ownership) |
| POST | `/api/customer/orders` | Cognito `customer` | Crear pedido autenticado |
| POST | `/api/customer/payments` | Cognito `customer` | Pagar orden propia (ownership) |
| GET | `/api/customer/receipts/{orderId}` | Cognito `customer` | Boleta propia |

`POST /api/public/orders` body:

```json
{
  "customerName": "Ana Pérez",
  "customerEmail": "ana@example.com",
  "shippingAddress": "Av. Siempre Viva 123",
  "items": [{ "productId": 3, "quantity": 2 }]
}
```

Validaciones (F32):

- `customerName` 2-100, email válido, dirección 5-200.
- 1-20 ítems, cantidad 1-10.
- Producto existente y activo; stock suficiente.
- **El precio se calcula en el servidor** consultando `products-service`; nunca
  se confía en el total enviado por el cliente.
- Respuesta 201: `{ trackingCode, status, total, createdAt }` (sin datos
  internos ni email de otros).
- La consulta pública devuelve solo `{ trackingCode, status, total, itemCount,
  createdAt }`; **nunca** email, dirección, id interno ni listados.
- Rate limit en API Gateway para `/api/public/**` (ej. 20 rps, burst 40) y
  validación Bean Validation en backend.

Al crear (guest o autenticado):

1. Persistir orden + ítems.
2. Publicar comando Rabbit `order.created` (notificación asíncrona).
3. Publicar evento Kafka `ORDER_CREATED` (reportes/auditoría).
4. Responder sin esperar al consumidor.

#### Frontend público

- Home/catálogo visible sin login (ya existe).
- Botón "Comprar como invitado" en el carrito.
- Formulario guest → pantalla de confirmación con `trackingCode` grande.
- Página `/seguir-pedido`: input de código → estado y resumen.
- `/mis-compras` para cliente Cognito.
- Al intentar `/admin` sin rol admin: 403 visual, no solo ocultar el link.

### 4.5 API Gateway (F22, F33, T04)

Instancia existente: `3cbwduklgb` (`us-east-1`).

#### Authorizers

| Nombre | Tipo | Issuer | Audience |
|---|---|---|---|
| `tuki-jwt` | JWT | `https://login.microsoftonline.com/<tenant>/v2.0` | `api://tuki-tech-api` |
| `tuki-cognito` | JWT | `https://cognito-idp.us-east-1.amazonaws.com/<poolId>` | `<appClientId>` |

#### Rutas

| Método | Ruta | Integración | Authorizer |
|---|---|---|---|
| ANY | `/api/public/products`, `/api/public/products/{proxy+}` | products 8081 | — |
| ANY | `/api/public/categories`, `.../{proxy+}` | categories 8082 | — |
| POST | `/api/public/orders` | orders 8084 | — |
| GET | `/api/public/orders/{proxy+}` | orders 8084 | — |
| ANY | `/api/customer/orders`, `.../{proxy+}` | orders 8084 | `tuki-cognito` |
| ANY | `/api/customer/payments`, `.../{proxy+}` | payments 8086 | `tuki-cognito` |
| ANY | `/api/public/*` OPTIONS | — | — |
| ANY | `/api/products`, `/api/categories`, `/api/users`, `/api/orders`, `/api/rabbit`, `/api/payments`, `/api/receipts` (+ `{proxy+}`) | servicios | `tuki-jwt` |
| ANY | `/api/customer/*` OPTIONS | — | — |

> Cuidado con el orden: `/api/public/orders` es más específica que
> `/api/orders`; no hay conflicto porque los prefijos son distintos. Mantener
> las rutas `OPTIONS` explícitas sin authorizer para la preflight.

#### Configuración adicional

- CORS: `http://localhost:4200`, `https://pastito-247.github.io`.
- Throttling por stage: 50 rps / burst 100 global; 20/40 para `/api/public/**`.
- Actualizar `PROD_BACKEND_URL` en GitHub Actions Variables al nuevo stage.
- Repetir el deploy del stage `$default` después de cada cambio.

**Criterio de aceptación:**

- `curl .../api/public/orders` sin token → 201.
- `curl .../api/public/orders/CODIGO` sin token → 200 acotado.
- `curl .../api/orders` sin token → 401.
- `curl .../api/customer/orders` con token Cognito → 200.
- `curl .../api/customer/orders` con token Cognito manipulado → 401.
- `curl .../api/rabbit/queues` con token Cognito → 403 (backend exige admin).

### 4.6 RabbitMQ (T09, F11-F13, F18, F21)

La base ya está sólida; falta ordenarla para la demo y para el concepto
"RabbitMQ = comandos".

#### Topología objetivo

- **`tuki.commands` (direct)**: comandos de notificación.
  - `rk=notify.order` → `tuki.notify.order.queue`
  - `rk=notify.payment` → `tuki.notify.payment.queue`
  - `rk=notify.receipt` → `tuki.notify.receipt.queue`
- **`tuki.catalog.events` (topic)**: eventos de catálogo.
  - `product.*`, `category.*` → `tuki.catalog.sync.queue`
- **`tuki.dlx` (direct)**: dead-letter de todas las colas.
- Cada cola con `x-dead-letter-exchange=tuki.dlx` y su routing key.
- Cada DLQ con un `@RabbitListener` que hace log estructurado
  (`queue`, `messageId`, `routingKey`, `payload`, `x-death`).

> Las colas actuales (`order.created`, `order.review`, `payment.screen`, etc.)
> se conservan para no romper la Entrega 2; la topología nueva se agrega como
> capa de comandos y se documenta en `RABBITMQ_SETUP.md`. Para la demo se
> muestran **3 colas + 3 DLQ** de `tuki.commands`, más el exchange topic de
> catálogo y el DLX direct.

#### Demo de resiliencia

- Endpoint de prueba o mensaje manual con payload inválido → reintentos 3 →
  DLQ.
- Mostrar en la Management UI el mensaje en la DLQ y el log del consumidor.
- Mostrar `rabbitmqctl cluster_status` con 2 nodos.

### 4.7 BD cloud — RDS PostgreSQL (6%)

#### Servicios

- Agregar driver `org.postgresql:postgresql` y quitar H2 de `runtime`
  (dejarlo `test`).
- `application.yml` con perfiles:
  - `local`: PostgreSQL en Docker (`jdbc:postgresql://localhost:5432/tuki_<svc>`).
  - `prod`: variables `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`.
- `ddl-auto: update` en prod/local (curso); `create-drop` solo en tests.
- Una base por servicio en la misma instancia RDS:
  `tuki_products`, `tuki_categories`, `tuki_users`, `tuki_orders`,
  `tuki_payments`, `tuki_audit`, `tuki_reporting`.
- Seed idempotente (6 categorías + 19 productos + usuarios de prueba) vía
  `CommandLineRunner` o `data.sql`.

#### RDS

- Instancia `db.t4g.micro` o `db.t3.micro` (free tier si aplica), PostgreSQL 16.
- Security Group: solo el SG del EC2 y la IP de desarrollo.
- Backup automático 1 día (demo).
- Documentar costos y cómo apagarla al terminar.

**Criterio de aceptación:** reiniciar el backend y los datos siguen ahí;
`/api/products` devuelve el seed; el EC2 usa RDS y no H2.

### 4.8 Seguridad y calidad

- [ ] Eliminar el rol del registro público; forzar `customer`.
- [ ] `POST /orders` y `POST /payments` toman el email del JWT, no del body.
- [ ] `GET /orders/{id}`: admin o dueño; si no, 403/404.
- [ ] `GET /orders/user/{email}`: solo admin o el propio email.
- [ ] `GET /users/**`, `GET /payments/**`: admin o dueño.
- [ ] Selector de roles del admin: `admin/customer/staff`.
- [ ] Quitar `LOCAL_JWT_*` de `.env.example` y código muerto.
- [ ] Validar tamaño de payloads y `@Valid` en todos los DTOs públicos.
- [ ] Rate limiting en API Gateway para rutas públicas.
- [ ] No devolver stacktraces en errores públicos.

#### Tests y CI

- Tests unitarios: guest order (validaciones + cálculo de total), mapeo de roles
  Cognito/Entra, publisher Kafka (mock), consumer (embedded Kafka opcional).
- GitHub Actions `backend-ci.yml`: matriz de 9 servicios, Java 21, Maven,
  `mvn -q test`.
- Mantener `deploy-pages.yml`; agregar build del frontend en PR.

### 4.9 Despliegue

#### Local (demo E2E)

`docker-compose.full.yml`:

- `postgres` (init con las 7 bases).
- `rabbitmq1`, `rabbitmq2` (clúster).
- `zookeeper1..3`, `kafka1..3`, `kafka-ui`, `kafka-init`.
- 9 servicios Spring Boot (build multi-stage).
- `seed` (one-shot) o seed al arrancar.
- Red única `tuki-net`; healthchecks y `depends_on`.

Frontend: `npm start` (proxy al gateway local o URLs directas).

#### Cloud

- EC2 `t3.medium` recomendado si se suben los 3 servicios nuevos; `t3.micro`
  solo para los 6 actuales.
- Dockerfiles por servicio; alternativa systemd con JARs.
- Variables en `/etc/tuki/*.env`; nunca commitear secretos.
- RDS + Security Groups.
- API Gateway + GitHub Pages.
- Actualizar `EC2_DEPLOYMENT.md` y `API_GATEWAY_DEPLOYMENT.md`.

#### Dockerfiles

Un `Dockerfile` multi-stage por servicio:

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java","-Xmx256m","-jar","app.jar"]
```

---

## 5. Backlog por fases

Cada fase termina con commit en `entrega-3` y criterio de aceptación verificado.

### Fase 0 — Preparación (0.5 día)

- [ ] Instalar Maven portable o usar `mvnw` (hoy no hay `mvn` en el equipo).
- [ ] Crear `.env` local desde `.env.example` y generar environments.
- [ ] Verificar Docker Desktop con ≥ 6 GB de RAM.
- [ ] Levantar el backend actual y confirmar que compila.
- [ ] Documentar línea base en este archivo.

### Fase 1 — Kafka infra (1 día) — 15%

- [ ] `docker-compose.kafka.yml` con 3 ZK + 3 brokers + UI + init.
- [ ] Tópicos `tuki.order.events`, `tuki.payment.events` (3p/3r) y DLT.
- [ ] Retention y cleanup policy.
- [ ] `KAFKA_SETUP.md` con comandos de verificación.
- [ ] Evidencia: `kafka-topics --describe`, captura de Kafka UI.

### Fase 2 — Kafka código (2-3 días) — 15%

- [ ] Dependencias y configuración en orders/payments.
- [ ] `OrderEventPublisher` y `PaymentEventPublisher` con DTO.
- [ ] `report-service` (8087) con consumidor y KPIs.
- [ ] `audit-service` (8088) con consumidor, persistencia y filtros.
- [ ] `kafka-admin-service` (8089) con AdminClient.
- [ ] DLT + reintentos + logs.
- [ ] Frontend: `/kafka-admin` y vista de reportes/auditoría.
- [ ] Prueba: crear pedido → evento en Kafka UI → KPI y auditoría actualizados.

### Fase 3 — Cognito (1-2 días) — nuevo EFT

- [ ] Crear User Pool + app client + grupo.
- [ ] `PublicAuthService` + pantallas registro/confirmación/login/forgot/logout.
- [ ] Interceptor Cognito + guards.
- [ ] Backend multi-issuer en orders/payments/users.
- [ ] Mapeo de roles: Cognito → `customer`; jamás admin.
- [ ] Quitar registro local con rol admin.
- [ ] Prueba desde cero: registro → código → login → JWT → `/api/customer/orders`.

### Fase 4 — Rutas públicas guest (2 días) — nuevo EFT

- [ ] `OrderItem`, `trackingCode`, `guest`, `customerName/Email`.
- [ ] `POST /api/public/orders` con validación y precio server-side.
- [ ] `GET /api/public/orders/{trackingCode}` acotado.
- [ ] Prefijo `/api/customer/**` con ownership.
- [ ] Frontend: checkout guest, confirmación, seguimiento, mis compras.
- [ ] Prueba: sin token crear y consultar; sin token no ver listados ni admin.

### Fase 5 — API Gateway (1-2 días) — 35% defensa

- [ ] Authorizer `tuki-cognito`.
- [ ] Rutas públicas, customer y protegidas.
- [ ] CORS + throttling.
- [ ] Deploy del stage y actualizar `PROD_BACKEND_URL`.
- [ ] Pruebas 200/201/401/403 documentadas.
- [ ] Frontend en Pages consumiendo el gateway.

### Fase 6 — RabbitMQ topología demo (1 día) — 32%

- [ ] Exchange direct `tuki.commands` + 3 colas + 3 DLQ.
- [ ] Exchange topic `tuki.catalog.events` + DLX direct.
- [ ] Log consumer en cada DLQ.
- [ ] Clúster 2 nodos documentado y verificado.
- [ ] Demo de fallo → DLQ.
- [ ] Actualizar `RABBITMQ_SETUP.md`.

### Fase 7 — RDS + despliegue (2 días) — 15%

- [x] Crear RDS PostgreSQL y bases.
- [x] Migrar los 5 servicios JPA a PostgreSQL.
- [x] Seed idempotente.
- [x] Dockerfiles y compose cloud.
- [x] Desplegar en EC2 y verificar desde API Gateway.
- [x] Actualizar guías y variables.

### Fase 8 — Cierre (1-2 días)

- [x] Arreglos de seguridad (IDOR, roles, registro).
- [x] CI backend + tests.
- [x] `GUIA_DEFENSA_EFT.md` con guion de 10 minutos.
- [ ] Ensayo cronometrado y video.
- [x] Checklist final de la pauta.

---

## 6. Matriz de evaluación → dónde se cumple

| Bloque | Peso | Se cumple con |
|---|---|---|
| Backend completo | 11% | 6 servicios actuales + report/audit/kafka-admin |
| Frontend completo | 9% | Angular con catálogo, guest, cliente, admin, Kafka UI |
| Frontend ↔ IDaaS | 6% | MSAL (sin cambios) |
| Backend valida JWT IDaaS | 6% | Resource server Entra en todos los servicios |
| BD cloud | 6% | RDS PostgreSQL + repositorios JPA |
| Clúster RabbitMQ | 8% | `docker-compose.full.yml` con 2 nodos |
| Producen/consumen colas | 12% | Flujo order/payment/receipt/notificación |
| DLQ con log | 6% | DLX + DLQ + `DeadLetterLogConsumer` |
| Admin RabbitMQ | 6% | `rabbitmq-admin-service` + pantalla |
| Kafka 3 ZK + 3 brokers | 15% | `docker-compose.kafka.yml` |
| Producen/consumen tópicos | 10% | orders/payments + report/audit |
| Admin Kafka | 5% | `kafka-admin-service` + Kafka UI |
| API Manager en nube | 7% | `3cbwduklgb` |
| Rutas a todos los endpoints | 9% | Tabla de rutas del gateway |
| Frontend vía API Manager | 9% | `PROD_BACKEND_URL` |
| Gateway valida JWT | 10% | Authorizers Entra + Cognito |
| Tenant IDaaS | 6% | Ya existe |
| Login OIDC con JWT | 11% | MSAL ya funciona |
| Backend + frontend en nube | 9% | EC2 + Pages + RDS |
| Rabbit 2 nodos | 2% + 5% | Clúster local/EC2 |
| 3 colas + DLQ | 6% + 6% | `tuki.commands` + DLQ |
| Exchanges direct + topic | 5% | `tuki.commands` + `tuki.catalog.events` |
| Kafka 2 tópicos 3p/3r | 9% | Init script |
| Kafka UI | 6% | `kafka-ui` |
| Federación Cognito | nuevo | F24-F28 |
| Transacción pública | nuevo | F29-F33 |

---

## 7. Guion de defensa (10 minutos)

1. **Infra local** (1 min): `docker compose -f docker-compose.full.yml up -d`;
   mostrar `docker ps`, Kafka UI y `rabbitmqctl cluster_status`.
2. **Sin token** (1.5 min): abrir catálogo → comprar como invitado → mostrar
   código de confirmación → consultar estado con el código.
3. **Kafka** (1.5 min): Kafka UI → `tuki.order.events`, 3 particiones, offset y
   lag; mostrar evento guest. Abrir reportes (KPI actualizado) y auditoría
   (registro con actor `guest`).
4. **Cognito** (2 min): registro público → código de email → login → mostrar
   JWT en DevTools → `/mis-compras`.
5. **Roles** (1 min): con ID token Cognito, intentar `/api/rabbit/queues`
   → 401 en el authorizer Entra; con token Entra admin → 200.
6. **API Manager** (1.5 min): mostrar rutas públicas vs. protegidas, authorizers,
   y un `curl` con token inválido → 401.
7. **RabbitMQ** (1 min): 2 nodos, 3 colas + DLQ, exchanges direct y topic.
8. **Fallo** (1 min): publicar mensaje inválido → DLQ → log; evento Kafka
   inválido → DLT → log.

### Checklist pre-demo

- [x] RDS arriba y seed cargado.
- [x] EC2 arriba (EIP `184.195.234.120`, gateway verificado sin `502/503` el `2026-11-19`).
- [x] Usuario de prueba Cognito confirmado (`cliente@tuki.cl`).
- [ ] Usuario admin Entra con rol `admin`.
- [ ] Kafka UI y Rabbit Management abiertos en pestañas.
- [x] Frontend apuntando al gateway correcto.
- [ ] Plan B: video grabado con el flujo completo.

---

## 8. Variables de entorno nuevas

```dotenv
# PostgreSQL / RDS
DB_HOST=tuki-postgres.xxxx.us-east-1.rds.amazonaws.com
DB_PORT=5432
DB_USER=tuki
DB_PASSWORD=********
DB_NAME=tuki_orders          # una por servicio

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092,localhost:9093,localhost:9094
KAFKA_ORDER_TOPIC=tuki.order.events
KAFKA_PAYMENT_TOPIC=tuki.payment.events
KAFKA_CONSUMER_GROUP=tuki-report

# Cognito
COGNITO_REGION=us-east-1
COGNITO_USER_POOL_ID=us-east-1_xxxxxxx
COGNITO_CLIENT_ID=xxxxxxxxxxxxxxxxxxxxxxxxxx
COGNITO_ISSUER=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_xxxxxxx

# Rutas nuevas del frontend
PUBLIC_ORDERS_URL=${PROD_BACKEND_URL}/api/public/orders
CUSTOMER_ORDERS_URL=${PROD_BACKEND_URL}/api/customer/orders
REPORTS_URL=${PROD_BACKEND_URL}/api/reports
AUDIT_URL=${PROD_BACKEND_URL}/api/audit
KAFKA_ADMIN_URL=${PROD_BACKEND_URL}/api/kafka
```

---

## 9. Preguntas abiertas / a confirmar durante la implementación

1. ¿Se usa SES para Cognito o alcanza con el email por defecto (límite 50/día)?
2. ¿El EC2 se sube a `t3.medium` para los 3 servicios nuevos o report/audit se
   consolidan en uno?
3. ¿Se mantiene el login local de usuarios creados en `/register` o se elimina
   por completo en favor de Cognito? (Recomendado: eliminar el rol admin y
   dejar solo Cognito para público).
4. ¿La boleta PDF se mantiene en payments-service o se mueve a un
   `receipt-service`? (Recomendado: no mover, es alcance extra).
5. ¿Se agrega un API Gateway local (Kong) para desarrollo o se prueba directo
   contra los puertos? (Recomendado: directo en local, gateway solo en nube).

---

## 10. Orden recomendado de ejecución

1. Fase 1 + 2 (Kafka): mayor peso y completamente independiente de AWS.
2. Fase 3 + 4 (Cognito + guest): la novedad del EFT.
3. Fase 5 (API Gateway): cierra el 35% de la defensa.
4. Fase 6 (RabbitMQ): pulir lo que ya existe.
5. Fase 7 (RDS + deploy): al final, cuando el código esté estable.
6. Fase 8 (cierre, tests, guion y video).
