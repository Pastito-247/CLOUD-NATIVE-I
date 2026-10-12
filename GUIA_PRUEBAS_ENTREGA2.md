# Guía de pruebas - Entrega 2 (RabbitMQ)

Checklist y procedimiento para demostrar la mensajería asíncrona: colas,
exchanges, bindings, DLQ, ACK/NACK y microservicio administrador.

---

## 1. Estado actual frente a la pauta

| Indicador (pauta) | Dónde está implementado | Estado |
| --- | --- | --- |
| Nombres centralizados en `application.yml` / `@Configuration` | `messaging.*` en cada `application.yml` + `MessagingProperties` (`@ConfigurationProperties`) | ✅ |
| Beans `Queue`/`Exchange`/`Binding` por caso de uso | `RabbitMQConfig` en orders, users, products y categories | ✅ |
| Separación config vs lógica de negocio | `config/` (RabbitMQConfig, MessagingProperties) vs `messaging/` y `service/` | ✅ |
| `@RabbitListener` agrupado por dominio | `orders.messaging` (ticket + DLQ), `users.messaging` (notification + DLQ), `products/categories.messaging` | ✅ |
| ACK/NACK explícito + reintentos + DLQ | `acknowledge-mode: manual`; `basicAck` / republish con `x-retry-count` / `basicNack(requeue=false)` | ✅ |
| Microservicio administrador con API REST | `rabbitmq-admin-service` (8085), `/api/rabbit/**` | ✅ |
| Lógica encapsulada en servicio | `RabbitAdminService` (controladores no conocen RabbitMQ) | ✅ |
| Validación de entrada | DTOs con `@NotBlank`/`@Pattern` + `GlobalExceptionHandler` | ✅ |
| Vista frontend | `/admin/rabbitmq` (`rabbitmq-admin.component.ts`) | ✅ |
| Pruebas básicas | 21 tests (orders, users, admin) ✅ | ✅ |

Resultado de `mvn test` (verificado localmente):

```
rabbitmq-admin-service : Tests run: 15, Failures: 0, Errors: 0
orders-service         : Tests run: 3,  Failures: 0, Errors: 0
users-service          : Tests run: 3,  Failures: 0, Errors: 0
products-service       : BUILD SUCCESS
categories-service     : BUILD SUCCESS
```

---

## 2. Qué falta / recomendaciones

### Crítico para la demo
1. **No hay RabbitMQ local levantado por el proyecto.** No existe `docker-compose.yml`.
   Hoy hay que instalar RabbitMQ a mano o apuntar a CloudAMQP. Agregar el
   `docker-compose.yml` de la sección 3.1 hace la demo reproducible en 1 comando.
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
5. `products-service` y `categories-service` **no tienen tests** de sus
   productores/consumidores de catálogo (solo orders/users/admin tienen).
   Agregarles un test subiría el indicador de "pruebas básicas".
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
   `order.created` fallaba con `Failed to convert Message content`
   (`LocalDateTime` no soportado por el ObjectMapper por defecto). Ahora el
   converter reusa el `ObjectMapper` de Spring Boot (registra `JavaTimeModule`)
   en los 4 servicios.
9. **Units sin `RABBITMQ_*`.** orders/users/products/categories arrancaban contra
   `localhost` (todas las colas con `consumers: 0`). Verifícalo con
   `sudo systemctl cat tuki-orders | grep -i RABBITMQ`. `EC2_DEPLOYMENT.md` ya
   incluye esas variables en los 5 units (CloudAMQP, puerto 5671 TLS).

---

## 3. Guía de pruebas paso a paso

### 3.0 Prerrequisitos
- Java 21, Maven 3.9+, Node 20+, Docker Desktop (para RabbitMQ local).
- En Windows usar `curl.exe` (no el alias `curl` de PowerShell).

### 3.1 Levantar RabbitMQ local (Docker)

Crear `docker-compose.yml` en la raíz del repo (si aún no existe):

```yaml
services:
  rabbitmq:
    image: rabbitmq:3.13-management
    container_name: tuki-rabbitmq
    ports:
      - "5672:5672"    # AMQP
      - "15672:15672"  # Management UI / HTTP API
    environment:
      RABBITMQ_DEFAULT_USER: guest
      RABBITMQ_DEFAULT_PASS: guest
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "-q", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
```

Levantar y verificar:

```powershell
docker compose up -d
# UI: http://localhost:15672  (guest / guest)
```

> Para la demo también sirve una instancia CloudAMQP. En ese caso ajusta
> `RABBITMQ_HOST/PORT=5671/SSL=true/VHOST` en `.env` y
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
mvn -f backend\rabbitmq-admin-service\pom.xml spring-boot:run
```

Opción B (carga `.env` automáticamente):

```powershell
.\scripts\run-backend.ps1 orders-service
.\scripts\run-backend.ps1 users-service
.\scripts\run-backend.ps1 rabbitmq-admin-service
```

Al arrancar, cada servicio declara sus colas/exchanges/bindings en RabbitMQ.
Verifícalo en la UI (http://localhost:15672 → *Queues* / *Exchanges*):
`orders.exchange`, `orders.dlx`, `order.ticket.queue`, `order.notification.queue`,
`order.ticket.dlq`, `order.notification.dlq`, `catalog.exchange`, `catalog.dlx`,
`product.sync.queue`, `category.sync.queue` y sus DLQ.

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

### 3.5 Probar el flujo asíncrono de pedidos (order.created)

`orders-service` publica `order.created` → `users-service` notifica y
`orders-service` genera el ticket.

```powershell
curl.exe -X POST http://localhost:8084/api/orders `
  -H "Authorization: Bearer $JWT" -H "Content-Type: application/json" `
  -d '{\"userEmail\":\"cliente@demo.cl\",\"total\":1000,\"shippingAddress\":\"Av. Siempre Viva 742\"}'
```

Evidencia esperada en logs:
- `orders-service`: `Evento order.created publicado para el pedido <id>` y
  `Ticket generado ...`
- `users-service`: `Notificacion ... enviada a cliente@demo.cl ...`

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

### 3.7 Probar la DLQ (mensajes no entregados)

`NotificationService` falla a propósito si el correo empieza con `fail@`.
El consumidor reintenta 3 veces (`x-retry-count`) y al agotarlas hace
`basicNack(requeue=false)` → cae en `orders.dlx` → `order.notification.dlq`.

```powershell
curl.exe -X POST http://localhost:8084/api/orders `
  -H "Authorization: Bearer $JWT" -H "Content-Type: application/json" `
  -d '{\"userEmail\":\"fail@demo.cl\",\"total\":1000,\"shippingAddress\":\"Av. Siempre Viva 742\"}'
```

Evidencia esperada:
- `users-service`: `Fallo enviando la notificacion ... (intento 1..3)`,
  luego `Reintentos agotados ... Enviando a la DLQ`.
- `users-service`: `[DLQ:notification] Mensaje no entregado registrado. motivo=... payload=...`
- En RabbitMQ UI, la cola `order.notification.dlq` recibe **1 mensaje**.

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

### 3.10 Ejecutar las pruebas automáticas

```powershell
mvn -f backend\rabbitmq-admin-service\pom.xml test
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
2. Mostrar `RabbitMQConfig`: beans Queue/Exchange/Binding + DLX/DLQ.
3. Crear un pedido normal → mostrar logs de notificación + ticket.
4. Crear un pedido con `fail@` → mostrar reintentos, `x-retry-count`, NACK,
   llegada a `order.notification.dlq` y el log `[DLQ:notification]`.
5. Probar `POST/GET/DELETE /api/rabbit/*` y el error 400 con nombre inválido.
6. Abrir la vista `/admin/rabbitmq` y administrar recursos desde el frontend.
7. Correr `mvn test` y mostrar BUILD SUCCESS.
