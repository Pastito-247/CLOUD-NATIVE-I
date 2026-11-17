# RabbitMQ - Mensajería asíncrona (Entrega 2)

Esta guía documenta la integración de RabbitMQ: topología del **flujo de pago**,
microservicio administrador, manejo de errores con DLQ y variables de entorno.

## 1. Topología de mensajería

El flujo se divide en dos fases, cada una con un **exchange fanout** y una
**DLX/DLQ por cola**.

```
FASE 1 - Pago
  POST /api/payments (payments-service)
        │  publish payment.requested
        ▼
  payment.exchange  (fanout, durable)
        ├── payment.notification.queue   (users-service)  → aviso "pago en proceso"
        └── payment.screen.queue         (payments-service) → simula el pago (retardo)
                                                              y genera la boleta PDF
  payment.dlx (direct, durable)
        ├── payment.notification.dlq
        └── payment.screen.dlq

FASE 2 - Boleta (tras procesar el pago)
  (payment.screen.queue genera la boleta y publica receipt.generated)
        ▼
  receipt.exchange  (fanout, durable)
        ├── receipt.email.queue    (users-service)    → correo confirmación + boleta
        ├── order.review.queue     (orders-service)   → pedido para revisión staff/admin
        └── stock.decrement.queue  (products-service) → descuento de stock
  receipt.dlx (direct, durable)
        ├── receipt.email.dlq
        ├── order.review.dlq
        └── stock.decrement.dlq

Catálogo (products-service / categories-service)  [sin cambios]
  catalog.exchange (topic, durable)
        ├── category.created|updated|deleted → product.sync.queue   (products-service)
        └── product.created|updated|deleted  → category.sync.queue  (categories-service)
  catalog.dlx (direct, durable) → product.sync.dlq / category.sync.dlq

FASE 3 - Pedido creado
  POST /api/orders (orders-service)
        │  publish order.created
        ▼
  order.exchange  (fanout, durable)
        └── order.created.queue   (users-service)  → aviso "pedido recibido"
  order.dlx (direct, durable) → order.created.dlq

Alta de usuario
  POST /api/public/users/register (users-service)
        │  publish user.registered
        ▼
  user.exchange   (fanout, durable)
        └── user.registered.queue (users-service)  → correo de bienvenida (async)
  user.dlx (direct, durable) → user.registered.dlq
```

| Recurso | Tipo | Nombre | Archivo central |
| --- | --- | --- | --- |
| Exchange | fanout | `payment.exchange` | `messaging.exchange.payment` |
| Exchange (DLX) | direct | `payment.dlx` | `messaging.exchange.payment-dead-letter` |
| Cola | durable | `payment.notification.queue` | `messaging.queue.payment-notification` |
| Cola | durable | `payment.screen.queue` | `messaging.queue.payment-screen` |
| Cola (DLQ) | durable | `payment.notification.dlq` | `messaging.queue.payment-notification-dlq` |
| Cola (DLQ) | durable | `payment.screen.dlq` | `messaging.queue.payment-screen-dlq` |
| Exchange | fanout | `receipt.exchange` | `messaging.exchange.receipt` |
| Exchange (DLX) | direct | `receipt.dlx` | `messaging.exchange.receipt-dead-letter` |
| Cola | durable | `receipt.email.queue` | `messaging.queue.receipt-email` |
| Cola | durable | `order.review.queue` | `messaging.queue.order-review` |
| Cola | durable | `stock.decrement.queue` | `messaging.queue.stock-decrement` |
| Cola (DLQ) | durable | `receipt.email.dlq` | `messaging.queue.receipt-email-dlq` |
| Cola (DLQ) | durable | `order.review.dlq` | `messaging.queue.order-review-dlq` |
| Cola (DLQ) | durable | `stock.decrement.dlq` | `messaging.queue.stock-decrement-dlq` |
| Exchange | topic | `catalog.exchange` | `messaging.exchange.catalog` |
| Exchange (DLX) | direct | `catalog.dlx` | `messaging.exchange.dead-letter` |
| Cola | durable | `product.sync.queue` | `messaging.queue.product-sync` |
| Cola | durable | `category.sync.queue` | `messaging.queue.category-sync` |
| Cola (DLQ) | durable | `product.sync.dlq` | `messaging.queue.product-sync-dlq` |
| Cola (DLQ) | durable | `category.sync.dlq` | `messaging.queue.category-sync-dlq` |
| Exchange | fanout | `order.exchange` | `messaging.exchange.order` |
| Exchange (DLX) | direct | `order.dlx` | `messaging.exchange.order-dead-letter` |
| Cola | durable | `order.created.queue` | `messaging.queue.order-created` |
| Cola (DLQ) | durable | `order.created.dlq` | `messaging.queue.order-created-dlq` |
| Exchange | fanout | `user.exchange` | `messaging.exchange.user` |
| Exchange (DLX) | direct | `user.dlx` | `messaging.exchange.user-dead-letter` |
| Cola | durable | `user.registered.queue` | `messaging.queue.user-registered` |
| Cola (DLQ) | durable | `user.registered.dlq` | `messaging.queue.user-registered-dlq` |

Los nombres **solo** viven en `application.yml` (cargados por
`MessagingProperties` con `@ConfigurationProperties`). Los beans
`Queue`/`Exchange`/`Binding` se declaran en `RabbitMQConfig` (ver
`payments-service`, `users-service`, `orders-service`, `products-service` y
`categories-service`).

> Nota: en un fanout el binding no usa routing key. La DLX es **direct**, por lo
> que cada cola define su `x-dead-letter-routing-key` (p. ej.
> `payment.screen.dlq`), que coincide con el binding de la DLQ.

## 2. Flujo asíncrono

1. `POST /api/payments` → `PaymentService.createPayment` guarda el pago en
   estado `PROCESSING` y publica `payment.requested` en `payment.exchange`.
2. `users-service` consume `payment.notification.queue` y crea la notificación
   "estamos procesando tu pago" (`PaymentNotificationConsumer` +
   `NotificationService`).
3. `payments-service` consume `payment.screen.queue`
   (`PaymentScreenConsumer`): **simula** el pago con un retardo
   (`messaging.processing.delay-ms`), marca el pago como `COMPLETED`, genera la
   **boleta PDF** (`ReceiptService`, OpenPDF) y publica `receipt.generated` en
   `receipt.exchange`.
4. A partir de `receipt.exchange`, en paralelo:
   - `users-service` (`receipt.email.queue`) → correo de confirmación + boleta.
   - `orders-service` (`order.review.queue`) → guarda el pedido para revisión
     (`OrderReviewService`, estado `PENDING_REVIEW`).
   - `products-service` (`stock.decrement.queue`) → descuenta el stock de los
     productos comprados (`ProductService.decrementStock`).

Flujo de catálogo (productor + consumidor en cada servicio), **sin cambios**:

5. `products-service` publica `product.created|updated|deleted`.
6. `categories-service` consume `category.sync.queue`.
7. `categories-service` publica `category.created|updated|deleted`.
8. `products-service` consume `product.sync.queue`.

Flujo de pedido y alta de usuario (cada microservicio es **productor y consumidor**):

9. `orders-service` publica `order.created` en `order.exchange` al crear un
   pedido (`OrderEventPublisher` desde `OrderService`).
10. `users-service` consume `order.created.queue` (`OrderCreatedConsumer`) y
    genera el aviso "pedido recibido" (`NotificationService`).
11. `users-service` publica `user.registered` en `user.exchange` al registrar
    una cuenta (`UserEventPublisher` desde `UserService`).
12. `users-service` consume `user.registered.queue` (`UserRegisteredConsumer`) y
    genera el correo de bienvenida de forma asincrona.

## 3. ACK/NACK, reintentos y DLQ

`acknowledge-mode: manual` en cada `application.yml`. Los consumidores:

- Ejecutan `channel.basicAck` al procesar correctamente.
- Ante un error, reintentan republicando el mensaje con la cabecera
  `x-retry-count` (hasta `messaging.retry.max-attempts`, por defecto 3).
- Agotados los intentos, hacen `channel.basicNack(..., requeue=false)`, lo que
  enruta el mensaje al **dead-letter exchange** y termina en la DLQ.
- Un `*DeadLetterLogConsumer` registra en el log los mensajes no entregados
  (payload + cabecera `x-death`).

### Probar la DLQ
Envía un pago cuyo correo empiece con `fail@` (fallo simulado en el
`PaymentScreenConsumer` y en las notificaciones), por ejemplo:

```bash
curl -X POST http://localhost:8086/api/payments \
  -H "Authorization: Bearer <JWT>" -H "Content-Type: application/json" \
  -d '{"userEmail":"fail@demo.cl","amount":1000,"method":"CREDIT_CARD",
       "shippingAddress":"Av. Siempre Viva 742",
       "items":[{"productId":1,"productName":"Teclado","quantity":1,"unitPrice":1000}]}'
```

Tras 3 reintentos verás el log `Reintentos agotados ... Enviando a la DLQ` y el
`DeadLetterLogConsumer` con `[DLQ:payment.screen]`. Para la DLQ de stock, envía
un `productId` inexistente (o mayor al stock disponible).

## 4. Microservicio administrador

`rabbitmq-admin-service` (puerto **8085**) administra recursos vía AMQP
(`AmqpAdmin`/canal) y lista vía la Management HTTP API. La lógica está
encapsulada en `RabbitAdminService`; los controladores no conocen RabbitMQ.

| Método | Endpoint | Descripción |
| --- | --- | --- |
| POST | `/api/rabbit/queues` | Crear cola |
| GET | `/api/rabbit/queues` | Listar colas |
| GET | `/api/rabbit/queues/{name}` | Detalle de una cola |
| DELETE | `/api/rabbit/queues/{name}` | Eliminar cola |
| POST | `/api/rabbit/exchanges` | Crear exchange (`direct`/`topic`/`fanout`/`headers`) |
| GET | `/api/rabbit/exchanges` | Listar exchanges |
| DELETE | `/api/rabbit/exchanges/{name}` | Eliminar exchange |
| POST | `/api/rabbit/bindings` | Crear binding |
| GET | `/api/rabbit/bindings` | Listar bindings |
| DELETE | `/api/rabbit/bindings` | Eliminar binding (body con exchange/queue/routingKey) |

Los cuerpos se validan con Bean Validation (`@NotBlank`, `@Pattern`) y hay un
`@RestControllerAdvice` que devuelve mensajes claros. No se permiten nombres
vacíos, con caracteres inválidos ni con el prefijo reservado `amq.`. Todos los
endpoints requieren rol **admin** (JWT de Entra ID).

### Ejemplos

```bash
# Crear cola
curl -X POST http://localhost:8085/api/rabbit/queues \
  -H "Authorization: Bearer <JWT>" -H "Content-Type: application/json" \
  -d '{"name":"demo.queue","durable":true,"exclusive":false,"autoDelete":false}'

# Crear exchange
curl -X POST http://localhost:8085/api/rabbit/exchanges \
  -H "Authorization: Bearer <JWT>" -H "Content-Type: application/json" \
  -d '{"name":"demo.exchange","type":"fanout","durable":true}'

# Crear binding
curl -X POST http://localhost:8085/api/rabbit/bindings \
  -H "Authorization: Bearer <JWT>" -H "Content-Type: application/json" \
  -d '{"exchange":"demo.exchange","queue":"demo.queue","routingKey":""}'
```

## 5. Configuración (CloudAMQP / nube)

Las credenciales se entregan por variables de entorno (o las que inyecte el
`application.yml`). Para CloudAMQP:

```
RABBITMQ_HOST=<instancia>.rmq.cloudamqp.com
RABBITMQ_PORT=5671
RABBITMQ_SSL=true
RABBITMQ_USERNAME=<usuario>
RABBITMQ_PASSWORD=<password>
RABBITMQ_VHOST=<vhost>
RABBITMQ_MANAGEMENT_URL=https://<instancia>.rmq.cloudamqp.com
```

> En CloudAMQP el broker AMQP está en el puerto **5671** (TLS) y expone la
> Management API por HTTPS para el mismo host. Si tu plan no incluye management,
> los `GET` de listado devolverán listas vacías sin afectar la creación/eliminación.

### Clúster local (docker-compose)

Para la demo, `docker-compose.yml` (raíz) levanta **2 nodos RabbitMQ en clúster**:

```powershell
docker compose up -d
docker exec rabbitmq1 rabbitmqctl cluster_status   # 2 nodos
```

- `rabbitmq1` → UI `http://localhost:15672`, AMQP `localhost:5672`
- `rabbitmq2` → UI `http://localhost:15673`, AMQP `localhost:5673`
- Integración del clúster: erlang cookie compartida + peer discovery
  (`docker/rabbitmq/rabbitmq.conf`).
- Credenciales locales `tuki / tuki` (ajústalas en `.env`).

### RabbitMQ en el EC2 (Docker, 1 nodo, solo local)

En producción el broker corre en la **misma instancia EC2** que el backend y
este se conecta por loopback (`127.0.0.1:5672`). No requiere abrir `5672` ni
`15672` en el Security Group ni tunel SSH inverso.

- Compose: `docker-compose.ec2.yml` (1 nodo, `restart: unless-stopped`,
  volumen `rabbitmq-data`, puertos publicados solo en `127.0.0.1`).
- Deploy automático desde tu PC:

  ```powershell
  .\scripts\deploy-rabbitmq-ec2.ps1 -Ec2Host IP_EC2 -ApplyBackendConfig
  ```

  `-ApplyBackendConfig` escribe los drop-ins de systemd
  (`/etc/systemd/system/tuki-*.service.d/override-rabbitmq.conf`) y reinicia
  los 6 servicios para que apunten a `127.0.0.1:5672` (`tuki / tuki`, vhost `/`).

- Manual dentro del EC2:

  ```bash
  mkdir -p ~/rabbitmq && cd ~/rabbitmq
  # copiar docker-compose.ec2.yml como docker-compose.yml
  docker compose up -d
  docker compose ps
  ```

- Management UI desde tu PC. Opción directa (sin túnel): abrir en el Security
  Group el puerto `15672` solo para tu IP y entrar a `http://IP_EC2:15672`
  (`tuki / tuki`). Opción segura sin abrir puertos: túnel SSH de *forward*:

  ```powershell
  ssh -i .\tuki-key.pem -L 15672:127.0.0.1:15672 ubuntu@IP_EC2
  # http://localhost:15672  ->  tuki / tuki
  ```

> Si el volumen ya existe, cambiar `RABBITMQ_DEFAULT_USER/PASS` no tiene efecto.
> Para reinicializar (borra colas y mensajes): `docker compose down -v && docker compose up -d`.
> El contenedor arranca solo tras un reboot si Docker está habilitado
> (`sudo systemctl enable docker`).

## 6. Variables propias del flujo de pago

| Variable | Default | Descripción |
| --- | --- | --- |
| `PAYMENT_DELAY_MS` | `3000` | Retardo (ms) de la simulación de la pasarela |
| `RECEIPT_STORAGE_DIR` | `receipts` | Directorio donde se guardan las boletas PDF |

## 7. Frontend

El panel admin incluye la vista **RabbitMQ** (`/admin/rabbitmq`), protegida por
MSAL + rol `admin`, que consume `rabbitmq-admin-service` a través de
`environment.rabbitUrl`.

La página **`/register`** (pública, sin login) crea cuentas llamando a
`POST /api/public/users/register` (`environment.usersPublicUrl`). El alta guarda
el perfil y publica `user.registered`; el correo de bienvenida se genera de
forma asincrona. El checkout del carrito crea el pedido
(`POST /api/orders`) y luego registra el pago (`POST /api/payments`,
`environment.paymentsUrl`), disparando todo el flujo asincrono.

## 8. Despliegue

- Levanta `payments-service` (puerto 8086) igual que los demás servicios
  (EC2 o contenedor). Requiere el mismo JWT de Entra ID.
- En el API Gateway agrega la integración y la ruta
  `/api/payments/{proxy+}` → `http://<host>:8086` y
  `/api/receipts/{proxy+}` → `http://<host>:8086` con el **JWT authorizer**.
- En el EC2 exporta las variables `RABBITMQ_*` para que `payments-service`,
  `orders-service`, `users-service`, `products-service` y
  `rabbitmq-admin-service` se conecten al broker. Si RabbitMQ corre en el mismo
  EC2 (recomendado), los valores son `RABBITMQ_HOST=127.0.0.1`,
  `RABBITMQ_PORT=5672`, `RABBITMQ_SSL=false`, `RABBITMQ_VHOST=/` y las
  credenciales del contenedor (`tuki / tuki` por defecto).
