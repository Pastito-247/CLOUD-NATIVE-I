# Guía de pruebas - Entrega 2 (RabbitMQ)

Checklist y procedimiento para demostrar la mensajería asíncrona: colas,
exchanges, bindings, DLQ, ACK/NACK y microservicio administrador.

---

## 1. Estado actual frente a la pauta

| Indicador (pauta) | Dónde está implementado | Estado |
| --- | --- | --- |
| Nombres centralizados en `application.yml` / `@Configuration` | `messaging.*` en cada `application.yml` + `MessagingProperties` (`@ConfigurationProperties`) | ✅ |
| Beans `Queue`/`Exchange`/`Binding` por caso de uso | `RabbitMQConfig` en payments, orders, users, products y categories | ✅ |
| Separación config vs lógica de negocio | `config/` (RabbitMQConfig, MessagingProperties) vs `messaging/` y `service/` | ✅ |
| `@RabbitListener` agrupado por dominio | `payments.messaging` (screen + DLQ), `users.messaging` (payment/receipt/order-created/user-registered + DLQ), `orders.messaging` (review + DLQ), `products/categories.messaging` | ✅ |
| Exchanges fanout + colas por caso de uso | `payment.exchange` (2 colas), `receipt.exchange` (3 colas), `order.exchange` (1), `user.exchange` (1) | ✅ |
| ACK/NACK explícito + reintentos + DLQ | `acknowledge-mode: manual`; `basicAck` / republish con `x-retry-count` / `basicNack(requeue=false)` | ✅ |
| Microservicio administrador con API REST | `rabbitmq-admin-service` (8085), `/api/rabbit/**` | ✅ |
| Lógica encapsulada en servicio | `RabbitAdminService` (controladores no conocen RabbitMQ) | ✅ |
| Validación de entrada | DTOs con `@NotBlank`/`@Pattern` + `GlobalExceptionHandler` | ✅ |
| Vista frontend | `/admin/rabbitmq` (`rabbitmq-admin.component.ts`), `/register` (`register.component.ts`) | ✅ |
| Pruebas básicas | 47 tests (orders, users, products, categories, admin, payments) ✅ | ✅ |

Resultado de `mvn clean test` (verificado localmente, 14/10/2026):

```
rabbitmq-admin-service : Tests run: 15, Failures: 0, Errors: 0
users-service          : Tests run: 15, Failures: 0, Errors: 0
categories-service     : Tests run: 6,  Failures: 0, Errors: 0
orders-service         : Tests run: 4,  Failures: 0, Errors: 0
payments-service       : Tests run: 4,  Failures: 0, Errors: 0
products-service       : Tests run: 3,  Failures: 0, Errors: 0
```

---

## 2. Qué falta / recomendaciones

### Crítico para la demo
1. **RabbitMQ local: RESUELTO Y VERIFICADO (12/10/2026).** Ya existe
   `docker-compose.yml` en la raíz que levanta un **clúster de 2 nodos**
   (`rabbitmq1`, `rabbitmq2`) con peer discovery
   (`docker/rabbitmq/rabbitmq.conf`). Cubre lo que pide la pauta: "dos nodos
   RabbitMQ en clúster" y "docker-compose levantando RabbitMQ". Ver sección 3.1,
   donde se registra el `cluster_status` real de la validación.
2. **Ruta del API Gateway para el administrador.** El frontend llama a
   `<PROD_BACKEND_URL>/api/rabbit`; debe existir la ruta
   `/api/rabbit/{proxy+}` → `:8085` con el JWT authorizer y rol admin,
   o la vista `/admin/rabbitmq` fallará en producción.
3. **`.env` no se versiona** (correcto). Para evaluar hay que crearlo desde
   `.env.example` y correr `node scripts/generate-env.js`, o el build de Angular
   fallará porque `environment.ts` es generado y está en `.gitignore`.

### Mejoras menores (no bloquean)
4. `DELETE /api/rabbit/exchanges/{name}` y `DELETE /api/rabbit/bindings` **no
   devuelven 404** si el recurso no existe (AMQP lo ignora silenciosamente).
   Las colas sí lo hacen. Inconsistencia de diseño REST menor.
5. ~~`categories-service` **no tiene tests**~~ **RESUELTO**: `CategoryEventPublisherTest`
   (3) y `CategoryServiceTest` (3). Todos los servicios (incluido categories y el
   nuevo payments) ya tienen pruebas básicas.
6. Documentación: el `README.md` (línea ~181) menciona
   `/api/public/products/{proxy+}` pero los endpoints reales son
   `/api/products/public`. Unificar para evitar confusión en la defensa.

### Resuelto tras depurar el despliegue real (EC2 + CloudAMQP)

La demo end-to-end destapó 3 causas que dejaban la mensajería sin operar y que
quedaron corregidas en esta iteración:

7. **Audiencia JWT.** Entra no siempre emite `aud` = Application ID URI
   (`api://tuki-tech-api`); emite el Application (client) ID (GUID
   `66c0f84b-3701-42fb-9189-3f4092cba201`). Los `application.yml` y los units
   ahora aceptan **ambos** (lista separada por coma). Síntoma previo: el admin
   respondía `401 invalid_token` / "The required audience is missing".
8. **`Jackson2JsonMessageConverter` sin `JavaTimeModule`.** Publicar
   `payment.requested` fallaba con `Failed to convert Message content`
   (`LocalDateTime` no soportado por el ObjectMapper por defecto). Ahora el
   converter reusa el `ObjectMapper` de Spring Boot (registra `JavaTimeModule`)
   en payments/orders/users/products/categories.
9. **Units sin `RABBITMQ_*`.** orders/users/products/categories/payments
   arrancaban contra `localhost` (todas las colas con `consumers: 0`). Verifícalo
   con `sudo systemctl cat tuki-orders | grep -i RABBITMQ`. `EC2_DEPLOYMENT.md` ya
   incluye esas variables en los units (CloudAMQP, puerto 5671 TLS).

---

## 3. Guía de pruebas paso a paso

### 3.0 Prerrequisitos
- Java 21, Maven 3.9+, Node 20+, Docker Desktop (para RabbitMQ local).
- En Windows usar `curl.exe` (no el alias `curl` de PowerShell).

### 3.1 Levantar RabbitMQ local (Docker) — clúster de 2 nodos

El repo trae `docker-compose.yml` (raíz) + `docker/rabbitmq/rabbitmq.conf`, que
levantan **dos nodos RabbitMQ** que forman un clúster automáticamente:

```powershell
docker compose up -d
docker ps                                          # rabbitmq1 y rabbitmq2
docker exec rabbitmq1 rabbitmqctl cluster_status   # 2 nodos: rabbit@rabbitmq1, rabbit@rabbitmq2
```

- Management UI: `http://localhost:15672` (rabbitmq1) y `http://localhost:15673`
  (rabbitmq2), credenciales `tuki / tuki`.
- AMQP: `localhost:5672` (rabbitmq1) y `localhost:5673` (rabbitmq2).
- Ambos nodos comparten la topología: cualquiera de las dos UIs muestra las mismas
  colas/exchanges (evidencia del clúster).

**Verificado con Docker Desktop 4.94.0 (12/10/2026):** `docker compose up -d --wait`
deja ambos contenedores `healthy` y `rabbitmqctl cluster_status` reporta:

```text
Disk Nodes
rabbit@rabbitmq1
rabbit@rabbitmq2

Running Nodes
rabbit@rabbitmq1
rabbit@rabbitmq2

Versions: RabbitMQ 3.13.7 (Erlang 26.2.5.16) en ambos nodos
Alarms: (none)   Network Partitions: (none)
```

Nota: si al ejecutar `docker compose` fallas con
`error getting credentials - exec: "docker-credential-desktop": executable file
not found`, antepone el bin de Docker Desktop a tu PATH:
`$env:Path = "$env:LOCALAPPDATA\Programs\DockerDesktop\resources\bin;$env:Path"`.

Para apuntar los microservicios a este clúster local, `.env` debe usar
`localhost:5672` con `tuki/tuki`:

```
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=tuki
RABBITMQ_PASSWORD=tuki
RABBITMQ_VHOST=/
RABBITMQ_SSL=false
RABBITMQ_MANAGEMENT_URL=http://localhost:15672
```

> Para la demo también sirve una instancia CloudAMQP (broker único). En ese caso
> ajusta `RABBITMQ_PORT=5671 / SSL=true / VHOST` y
> `RABBITMQ_MANAGEMENT_URL=https://<instancia>.rmq.cloudamqp.com`.

### 3.2 Configurar `.env`

```powershell
Copy-Item .env.example .env
# Editar .env con tus valores reales de Entra ID.
# Para RabbitMQ local los valores por defecto ya sirven:
#   RABBITMQ_HOST=localhost / PORT=5672 / USER=guest / PASS=guest
#   RABBITMQ_SSL=false / RABBITMQ_VHOST=/
#   RABBITMQ_MANAGEMENT_URL=http://localhost:15672
node scripts/generate-env.js   # genera environment.ts / environment.prod.ts
```

### 3.3 Levantar los microservicios

Opción A (una terminal por servicio):

```powershell
mvn -f backend\orders-service\pom.xml spring-boot:run
mvn -f backend\users-service\pom.xml spring-boot:run
mvn -f backend\products-service\pom.xml spring-boot:run
mvn -f backend\categories-service\pom.xml spring-boot:run
mvn -f backend\payments-service\pom.xml spring-boot:run
mvn -f backend\rabbitmq-admin-service\pom.xml spring-boot:run
```

Opción B (carga `.env` automáticamente):

```powershell
.\scripts\run-backend.ps1 orders-service
.\scripts\run-backend.ps1 users-service
.\scripts\run-backend.ps1 products-service
.\scripts\run-backend.ps1 payments-service
.\scripts\run-backend.ps1 rabbitmq-admin-service
```

Al arrancar, cada servicio declara sus colas/exchanges/bindings en RabbitMQ.
Verifícalo en la UI (http://localhost:15672 → *Queues* / *Exchanges*):
`payment.exchange`, `payment.dlx`, `payment.notification.queue`,
`payment.screen.queue` y sus DLQ; `receipt.exchange`, `receipt.dlx`,
`receipt.email.queue`, `order.review.queue`, `stock.decrement.queue` y sus DLQ;
`catalog.exchange`, `catalog.dlx`, `product.sync.queue`, `category.sync.queue`
y sus DLQ.

### 3.4 Obtener un JWT (rol admin)

Los endpoints privados/admin requieren un access token de Entra ID. Dos formas:

- **Desde el frontend**: iniciar sesión como admin, abrir DevTools →
  Application → Local Storage → buscar la clave que contiene el `accessToken`.
- **Con Azure CLI**:
  ```powershell
  az login
  az account get-access-token --resource api://tuki-tech-api
  ```
  Copia el `accessToken` y úsalo como `Bearer`.

Guárdalo en una variable para los ejemplos:

```powershell
$JWT = "<pega-aqui-el-access-token>"
```

> Si no tienes Entra a mano, puedes probar los flujos de mensajería igual:
> los consumidores/logs y la DLQ se ven sin JWT. Solo los `POST/PUT/DELETE`
> de negocio y el microservicio administrador exigen token.

### 3.5 Probar el flujo asíncrono de pago (payment.requested / receipt.generated)

Fase 1: `POST /api/payments` → `payment.exchange` (fanout) → `users-service`
notifica "pago en proceso" y `payments-service` simula el pago y genera la
boleta PDF.

Fase 2: al procesar el pago se publica `receipt.generated` en
`receipt.exchange` (fanout) → correo de confirmación (users-service), pedido
para revisión (orders-service) y descuento de stock (products-service).

```powershell
curl.exe -X POST http://localhost:8086/api/payments `
  -H "Authorization: Bearer $JWT" -H "Content-Type: application/json" `
  -d '{\"userEmail\":\"cliente@demo.cl\",\"amount\":19990,\"method\":\"CREDIT_CARD\",\"shippingAddress\":\"Av. Siempre Viva 742\",\"items\":[{\"productId\":1,\"productName\":\"Teclado\",\"quantity\":2,\"unitPrice\":9995}]}'
```

Evidencia esperada en logs:
- `payments-service`: `Evento payment.requested publicado para el pago <id>` y
  `[SIMULACION] Procesando pago ... (retardo 3000 ms)`, luego
  `Boleta <id> generada ...` y `Evento receipt.generated publicado ...`.
- `users-service`: `Aviso de pago en proceso ...` y
  `Correo de confirmacion ... (boleta ...)`.
- `orders-service`: `Pedido <orderId> registrado para revision ...`.
- `products-service`: `Stock del producto <id> descontado en ...`.

Descarga de la boleta PDF (requiere JWT):

```powershell
curl.exe http://localhost:8086/api/receipts/1/pdf -H "Authorization: Bearer $JWT" -o boleta.pdf
```

### 3.6 Probar el flujo de catálogo (product.* / category.*)

Los nombres de cola se leen "al revés" a propósito: cada servicio consume los
eventos del *otro* dominio.

- Crear/editar/eliminar **producto** (products-service, 8081) publica
  `product.*` → lo consume **categories-service** (`category.sync.queue`).
- Crear/editar/eliminar **categoría** (categories-service, 8082) publica
  `category.*` → lo consume **products-service** (`product.sync.queue`).

```powershell
# Crear producto (admin)
curl.exe -X POST http://localhost:8081/api/products `
  -H "Authorization: Bearer $JWT" -H "Content-Type: application/json" `
  -d '{\"name\":\"Producto Demo\",\"price\":9999,\"stock\":5,\"category\":\"Tecnologia\"}'

# Crear categoría (admin)
curl.exe -X POST http://localhost:8082/api/categories `
  -H "Authorization: Bearer $JWT" -H "Content-Type: application/json" `
  -d '{\"name\":\"Tecnologia\",\"description\":\"Demo\"}'
```

Evidencia: logs de `products-service`/`categories-service` con el evento
consumido.

### 3.6b Probar el registro de usuario (user.registered) y el pedido (order.created)

**Registro público** (sin JWT) → publica `user.registered` → el propio
users-service lo consume para generar el correo de bienvenida (asíncrono):

```powershell
curl.exe -X POST http://localhost:8083/api/public/users/register `
  -H "Content-Type: application/json" `
  -d '{\"email\":\"nuevo@demo.cl\",\"name\":\"Nuevo Usuario\",\"role\":\"customer\"}'
```

- Respuesta `201 Created` con el usuario creado (email normalizado a minúsculas).
- Email repetido → `409 Conflict`; campos inválidos → `400 Bad Request`.
- Log en `users-service`: `Evento user.registered publicado ...` y luego el
  `UserRegisteredConsumer` con el correo de bienvenida.

**Pedido** (JWT) → publica `order.created` → lo consume users-service:

```powershell
curl.exe -X POST http://localhost:8084/api/orders `
  -H "Authorization: Bearer $JWT" -H "Content-Type: application/json" `
  -d '{\"userEmail\":\"nuevo@demo.cl\",\"total\":4999,\"items\":[{\"productId\":1,\"productName\":\"Teclado\",\"quantity\":1,\"unitPrice\":4999}]}'
```

- Log en `orders-service`: `Evento order.created publicado ...`.
- Log en `users-service`: `OrderCreatedConsumer` con el aviso "pedido recibido".

### 3.7 Probar la DLQ (mensajes no entregados)

`PaymentScreenConsumer`/`NotificationService`/`OrderReviewService` fallan a
propósito si el correo empieza con `fail@`. El consumidor reintenta 3 veces
(`x-retry-count`) y al agotarlas hace `basicNack(requeue=false)` → cae en la
DLX correspondiente (`payment.dlx` / `receipt.dlx`) → su DLQ.

```powershell
curl.exe -X POST http://localhost:8086/api/payments `
  -H "Authorization: Bearer $JWT" -H "Content-Type: application/json" `
  -d '{\"userEmail\":\"fail@demo.cl\",\"amount\":1000,\"method\":\"CREDIT_CARD\",\"items\":[{\"productId\":1,\"productName\":\"Teclado\",\"quantity\":1,\"unitPrice\":1000}]}'
```

Evidencia esperada:
- `payments-service`: `Fallo procesando el pago ... (intento 1..4)`, luego
  `Reintentos agotados ... Enviando a la DLQ 'payment.screen.dlq'` y
  `[DLQ:payment.screen] Mensaje no entregado registrado. ...`.
- En RabbitMQ UI, la cola `payment.screen.dlq` recibe **1 mensaje**.

Para la DLQ de stock (`stock.decrement.dlq`), envía un `productId` inexistente
o mayor al stock disponible: el `StockDecrementConsumer` agotará reintentos y el
mensaje terminará en `stock.decrement.dlq`.

### 3.8 Probar el microservicio administrador (8085)

```powershell
# Crear cola
curl.exe -X POST http://localhost:8085/api/rabbit/queues `
  -H "Authorization: Bearer $JWT" -H "Content-Type: application/json" `
  -d '{\"name\":\"demo.queue\",\"durable\":true,\"exclusive\":false,\"autoDelete\":false}'

# Listar colas
curl.exe http://localhost:8085/api/rabbit/queues -H "Authorization: Bearer $JWT"

# Crear exchange
curl.exe -X POST http://localhost:8085/api/rabbit/exchanges `
  -H "Authorization: Bearer $JWT" -H "Content-Type: application/json" `
  -d '{\"name\":\"demo.exchange\",\"type\":\"topic\",\"durable\":true}'

# Crear binding
curl.exe -X POST http://localhost:8085/api/rabbit/bindings `
  -H "Authorization: Bearer $JWT" -H "Content-Type: application/json" `
  -d '{\"exchange\":\"demo.exchange\",\"queue\":\"demo.queue\",\"routingKey\":\"demo.key\"}'

# Eliminar (404 si no existe la cola)
curl.exe -X DELETE http://localhost:8085/api/rabbit/queues/demo.queue -H "Authorization: Bearer $JWT"
```

Validación de entrada (debe fallar con 400 y mensaje claro):

```powershell
# Nombre vacio
curl.exe -X POST http://localhost:8085/api/rabbit/queues `
  -H "Authorization: Bearer $JWT" -H "Content-Type: application/json" `
  -d '{\"name\":\"\"}'

# Prefijo reservado amq.
curl.exe -X POST http://localhost:8085/api/rabbit/exchanges `
  -H "Authorization: Bearer $JWT" -H "Content-Type: application/json" `
  -d '{\"name\":\"amq.test\",\"type\":\"topic\"}'
```

### 3.9 Probar la vista frontend

```powershell
cd frontend
npm install
npm start   # http://localhost:4200
```

Inicia sesión como **admin** y abre `/admin/rabbitmq`: debes poder crear/listar/
eliminar colas, exchanges y bindings. Si usas backend local, `rabbitUrl` debe
apuntar a `http://localhost:8085/api/rabbit` (lo genera `generate-env.js`).

La página pública **`/register`** (sin sesión) crea cuentas de prueba en
users-service y dispara el flujo `user.registered` → correo de bienvenida.

### 3.10 Ejecutar las pruebas automáticas

```powershell
mvn -f backend\rabbitmq-admin-service\pom.xml test
mvn -f backend\payments-service\pom.xml test
mvn -f backend\orders-service\pom.xml test
mvn -f backend\users-service\pom.xml test
mvn -f backend\products-service\pom.xml test
mvn -f backend\categories-service\pom.xml test
```

> Los tests son unitarios con mocks: **no requieren RabbitMQ ni Azure**
> levantados.

---

## 4. Guion corto para la presentación

1. Mostrar `application.yml` y `MessagingProperties`: nombres centralizados.
2. Mostrar `RabbitMQConfig` de payments-service: 2 exchanges **fanout**
   (`payment.exchange`, `receipt.exchange`) + DLX/DLQ por cola.
3. `POST /api/payments` normal → mostrar que el fanout dispara 2 colas
   (aviso al usuario + simulación que genera la boleta PDF), y que al procesar
   el pago el segundo fanout dispara correo + revisión + descuento de stock.
4. `POST /api/payments` con `fail@` → mostrar reintentos, `x-retry-count`, NACK,
   llegada a `payment.screen.dlq` y el log `[DLQ:payment.screen]`.
5. Probar `POST/GET/DELETE /api/rabbit/*` y el error 400 con nombre inválido.
6. Abrir la vista `/admin/rabbitmq` y administrar recursos desde el frontend.
7. Correr `mvn test` y mostrar BUILD SUCCESS.
