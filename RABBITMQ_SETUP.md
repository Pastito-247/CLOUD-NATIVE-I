# RabbitMQ - Mensajería asíncrona (Entrega 2)

Esta guía documenta la integración de RabbitMQ: topología, microservicio
administrador, manejo de errores con DLQ y variables de entorno.

## 1. Topología de mensajería

```
Pedido creado (orders-service)
        │  publish order.created
        ▼
  orders.exchange  (topic, durable)
        ├── routing key: order.created ──► order.ticket.queue       (orders-service)
        └── routing key: order.created ──► order.notification.queue (users-service)

  orders.dlx (direct, durable)  ◄── dead-letter de las colas de negocio
        ├── routing key: order.ticket.dlq       ──► order.ticket.dlq
        └── routing key: order.notification.dlq ──► order.notification.dlq

Catálogo (products-service / categories-service)
  catalog.exchange (topic, durable)
        ├── routing key: category.created|updated|deleted ──► product.sync.queue   (products-service)
        └── routing key: product.created|updated|deleted  ──► category.sync.queue  (categories-service)

  catalog.dlx (direct, durable)  ◄── dead-letter de las colas de negocio
        ├── routing key: product.sync.dlq  ──► product.sync.dlq
        └── routing key: category.sync.dlq ──► category.sync.dlq
```

| Recurso | Tipo | Nombre | Archivo central |
| --- | --- | --- | --- |
| Exchange | topic | `orders.exchange` | `application.yml` → `messaging.exchange.orders` |
| Exchange (DLX) | direct | `orders.dlx` | `messaging.exchange.dead-letter` |
| Cola | durable | `order.ticket.queue` | `messaging.queue.ticket` |
| Cola | durable | `order.notification.queue` | `messaging.queue.notification` |
| Cola (DLQ) | durable | `order.ticket.dlq` | `messaging.queue.ticket-dlq` |
| Cola (DLQ) | durable | `order.notification.dlq` | `messaging.queue.notification-dlq` |
| Routing key | | `order.created` | `messaging.routing-key.order-created` |
| Exchange | topic | `catalog.exchange` | `messaging.exchange.catalog` |
| Exchange (DLX) | direct | `catalog.dlx` | `messaging.exchange.dead-letter` |
| Cola | durable | `product.sync.queue` | `messaging.queue.product-sync` |
| Cola | durable | `category.sync.queue` | `messaging.queue.category-sync` |
| Cola (DLQ) | durable | `product.sync.dlq` | `messaging.queue.product-sync-dlq` |
| Cola (DLQ) | durable | `category.sync.dlq` | `messaging.queue.category-sync-dlq` |
| Routing key | | `product.created|updated|deleted` | `messaging.routing-key.product-*` |
| Routing key | | `category.created|updated|deleted` | `messaging.routing-key.category-*` |

Los nombres **solo** viven en `application.yml` (cargados por
`MessagingProperties` con `@ConfigurationProperties`). Los beans
`Queue`/`Exchange`/`Binding` se declaran en `RabbitMQConfig`
(ver `orders-service`, `users-service`, `products-service` y `categories-service`).

## 2. Flujo asíncrono

1. `POST /api/orders` → `OrderService.createOrder` guarda el pedido y publica
   `order.created` (vía `OrderEventPublisher`). Un fallo de mensajería **no**
   rompe la creación del pedido.
2. `users-service` consume `order.notification.queue` y genera una notificación
   (`NotificationConsumer` + `NotificationService`).
3. `orders-service` consume `order.ticket.queue` y genera el ticket/documento
   (`TicketGenerationConsumer` + `TicketService`).

Flujo de catálogo (productor + consumidor en cada servicio):

4. `products-service` publica `product.created|updated|deleted` (vía
   `ProductEventPublisher`) al crear/editar/eliminar un producto.
5. `categories-service` consume `category.sync.queue` (`ProductEventConsumer`).
6. `categories-service` publica `category.created|updated|deleted` (vía
   `CategoryEventPublisher`).
7. `products-service` consume `product.sync.queue` (`CategoryEventConsumer`).

## 3. ACK/NACK, reintentos y DLQ

`acknowledge-mode: manual` en cada `application.yml`. Los consumidores:

- Ejecutan `channel.basicAck` al procesar correctamente.
- Ante un error, reintentan republicando el mensaje con la cabecera
  `x-retry-count` (hasta `messaging.retry.max-attempts`, por defecto 3).
- Agotados los intentos, hacen `channel.basicNack(..., requeue=false)`, lo que
  enruta el mensaje al **dead-letter exchange** y termina en la DLQ.
- Un `DeadLetterLogConsumer` registra en el log los mensajes no entregados
  (payload + cabecera `x-death`).

### Probar la DLQ
Envía una orden cuyo correo empiece con `fail@` (fallo simulado en
`NotificationService`), por ejemplo:

```bash
curl -X POST http://localhost:8084/api/orders \
  -H "Authorization: Bearer <JWT>" -H "Content-Type: application/json" \
  -d '{"userEmail":"fail@demo.cl","total":1000,"shippingAddress":"Av. Siempre Viva 742"}'
```

Tras 3 reintentos verás el log `Reintentos agotados ... Enviando a la DLQ` y el
`DeadLetterLogConsumer` con `[DLQ:notification]`.

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
  -d '{"name":"demo.exchange","type":"topic","durable":true}'

# Crear binding
curl -X POST http://localhost:8085/api/rabbit/bindings \
  -H "Authorization: Bearer <JWT>" -H "Content-Type: application/json" \
  -d '{"exchange":"demo.exchange","queue":"demo.queue","routingKey":"demo.key"}'
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

> En CloudAMQP el brote AMQP está en el puerto **5671** (TLS) y expone la
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

## 6. Frontend

El panel admin incluye la vista **RabbitMQ** (`/admin/rabbitmq`), protegida por
MSAL + rol `admin`, que consume `rabbitmq-admin-service` a través de
`environment.rabbitUrl`.

## 7. Despliegue

- Levanta `rabbitmq-admin-service` (puerto 8085) igual que los demás servicios
  (EC2 o contenedor). Requiere el mismo JWT de Entra ID.
- En el API Gateway agrega la integración y la ruta
  `/api/rabbit/{proxy+}` → `http://<host>:8085` con el **JWT authorizer**,
  para que el frontend la consuma como `<PROD_BACKEND_URL>/api/rabbit`.
- En el EC2 exporta las variables `RABBITMQ_*` para que `orders-service`,
  `users-service` y `rabbitmq-admin-service` se conecten a CloudAMQP.

