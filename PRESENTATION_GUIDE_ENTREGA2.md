# Guía de Presentación — Evaluación Parcial (Mensajería RabbitMQ)

Presentación grupal · evaluación individual · **5–10 min** · lenguaje técnico.
Integra la mensajería asíncrona (colas, exchanges, DLQ) en el sistema de la
Entrega 1 (Angular + API Gateway + Entra ID + microservicios Spring Boot).

---

## 1. Datos que siempre estarán a mano

| Elemento | Valor |
|---|---|
| Frontend (GitHub Pages) | `https://pastito-247.github.io/CLOUD-NATIVE-I/` |
| API Gateway (HTTP API) | `https://3cbwduklgb.execute-api.us-east-1.amazonaws.com` |
| Tenant Entra ID | `e3e92dfe-ea59-4c42-a539-90e6fea570b6` (tuki-tech.onmicrosoft.com) |
| Client ID (SPA) | `66c0f84b-3701-42fb-9189-3f4092cba201` |
| Scope | `api://tuki-tech-api/access_as_user` |
| Audience (JWT backend) | `api://tuki-tech-api` **y** `66c0f84b-...` (ambas) |
| Backend EC2 | products 8081, categories 8082, users 8083, orders 8084, **rabbitmq-admin 8085** |
| Cluster RabbitMQ | `docker compose up -d` → nodos `rabbitmq1` (:15672) y `rabbitmq2` (:15673), cred. `tuki/tuki` |
| Vista admin | `/admin/rabbitmq` |
| Exchange negocio (topic) | `orders.exchange`, `catalog.exchange` |
| Exchange DLX (direct) | `orders.dlx`, `catalog.dlx` |
| Colas + DLQ | `order.ticket.queue`→`.dlq`, `order.notification.queue`→`.dlq`, `product.sync.queue`→`.dlq`, `category.sync.queue`→`.dlq` |

---

## 2. Hilo conductor (resumen en 1 frase)

> "El frontend Angular (GitHub Pages) se autentica con Entra ID vía OAuth2/OIDC y
> consume las APIs a través del API Gateway, que valida el JWT; el backend Spring
> Boot en EC2 **desacopla tareas con RabbitMQ**: exchanges `topic` con DLX `direct`,
> consumidores agrupados por dominio con **ACK/NACK** y reintentos que drenan a
> **DLQ**, administrados por un microservicio REST; y toda la mensajería corre sobre
> un **clúster RabbitMQ de 2 nodos** levantado con **docker-compose**."

---

## 3. Mapa de la rúbrica (dónde se evidencia)

| # | Indicador (pauta) | % | Dónde está | Evidencia a mostrar |
|---|---|---|---|---|
| 1 | Nombres de colas/exchanges/bindings centralizados en `application.yml`/`@Configuration` | 12% | `messaging.*` en cada `application.yml` + `MessagingProperties` (`@ConfigurationProperties`) | `orders-service/.../application.yml` (sección `messaging`) y `config/MessagingProperties.java` |
| 2 | Beans `Queue`/`Exchange`/`Binding` por caso de uso | 13% | `RabbitMQConfig` en orders, users, products, categories | `orders/.../config/RabbitMQConfig.java` (exchanges, colas, DLQ y bindings) |
| 3 | Config de mensajería separada de la lógica de negocio | 10% | paquetes `config/` vs `messaging/` vs `service/` | Mostrar árbol de paquetes de `orders-service` |
| 4 | Consumidores con `@RabbitListener` agrupados por dominio | 15% | `orders.messaging` (ticket+DLQ), `users.messaging` (notification+DLQ), `products/categories.messaging` | `TicketGenerationConsumer`, `NotificationConsumer`, `DeadLetterLogConsumer` |
| 5 | ACK/NACK y manejo explícito de errores | 20% | `acknowledge-mode: manual`; `basicAck` / republish con `x-retry-count` / `basicNack(requeue=false)` | Logs de `fail@` (reintentos 1..3 → DLQ) + `RabbitMQConfig`/consumidores |
| 6 | Microservicio admin con endpoints REST bien definidos | 13% | `rabbitmq-admin-service` (8085), `/api/rabbit/**` | `POST/GET/DELETE /api/rabbit/queues|exchanges|bindings` |
| 7 | Lógica de admin encapsulada en `RabbitAdminService` | 10% | `rabbitadmin/service/RabbitAdminService.java` | El controller solo llama métodos de alto nivel |
| 8 | Validación de parámetros de entrada | 7% | DTOs `@NotBlank`/`@Pattern` + `validateName` + `GlobalExceptionHandler` | `POST /api/rabbit/queues` con nombre vacío → **400** |

---

## 4. Requisitos de la demo (instrucciones específicas)

| Requisito | Cómo se evidencia |
|---|---|
| API Manager en funcionamiento | Consola AWS → HTTP API `tuki-api`, stage `$default`, URL invoke |
| Config que llama a los endpoints del backend | Routes → Integrations al EC2 (`{proxy}`) |
| Frontend consume vía API Manager | DevTools → Network: la petición va a la URL del gateway |
| API Manager valida JWT | `curl /api/products` sin token → **401**; con token → **200** |
| Tenant IDaaS + usuarios | Consola Azure → tenant + usuarios admin/customer/staff |
| Login OAuth2/OIDC → JWT | Login en vivo + `jwt.ms` con `iss`/`aud`/`roles` |
| Backend y frontend desplegados | EC2 (5 servicios `active`) + GitHub Pages |
| **2 nodos RabbitMQ en clúster** | `docker exec rabbitmq1 rabbitmqctl cluster_status` → 2 nodos |
| **3 colas con DLQ** | UI/`GET /api/rabbit/queues`: ticket, notification y product.sync + sus `.dlq` |
| **Exchanges direct y topic** | `GET /api/rabbit/exchanges`: `orders.exchange`/`catalog.exchange` (topic) y `orders.dlx`/`catalog.dlx` (direct) |
| **docker-compose levanta RabbitMQ** | `docker compose up -d` + `docker ps` (rabbitmq1/rabbitmq2) |

---

## 5. Guion de 8 minutos

**1) Arquitectura — 1 min.** Diagrama + hilo conductor. Enfatiza que la mensajería
*no alteró* la lógica existente: los endpoints siguen igual y el evento se publica
después de persistir.

**2) Código (rúbrica 1–4) — 2 min.**
- `MessagingProperties` + `messaging.*` del `application.yml`: nombres centralizados.
- `RabbitMQConfig`: beans `Queue`/`Exchange`/`Binding` + DLX/DLQ por caso de uso.
- Paquetes `config/` separados de `messaging/` y `service/`.
- `@RabbitListener` agrupados por dominio.

**3) Flujo de pedido + DLQ (rúbrica 5) — 2 min.**
- `POST /api/orders` pedido normal → logs: `order.created publicado`, `Ticket generado`.
- `POST /api/orders` con `userEmail: fail@demo.cl` → logs de `users-service`:
  `reintento 1/3..3/3`, `Reintentos agotados ... Enviando a la DLQ`, `[DLQ:notification]`.
- Mostrar en la UI que `order.notification.dlq` recibió 1 mensaje.
- Explicar `basicNack(requeue=false)` → `orders.dlx` (direct) → DLQ.

**4) Microservicio admin (rúbrica 6–8) — 1 min.**
- `POST /api/rabbit/queues` (crear), `GET` (listar), `DELETE` (borrar).
- `POST /api/rabbit/exchanges` tipo `direct` **y** `topic`.
- Validación: `POST` con `name:""` → **400**; prefijo `amq.` → **400**.
- Abrir `/admin/rabbitmq` en el frontend.

**5) Cloud (API Gateway + IDaaS + despliegue) — 1 min.**
- Gateway: rutas, CORS, authorizer `tuki-jwt`; `curl` 401 vs 200.
- Azure: tenant + usuarios; login en vivo.

**6) RabbitMQ en la nube (cluster + docker-compose) — 1 min.**
- `docker compose up -d` → `docker ps` (rabbitmq1/rabbitmq2).
- `docker exec rabbitmq1 rabbitmqctl cluster_status` (2 nodos).
- Abrir :15672 y :15673: ambas UIs muestran el mismo topologías (cluster).

**Cierre — 30 s.** `mvn test` → BUILD SUCCESS; recorre la tabla de la rúbrica.

---

## 6. Backup de evidencias (si el demo en vivo falla)

1. Screenshot `cluster_status` con 2 nodos.
2. Screenshot `GET /api/rabbit/queues` (colas + DLQ) y `/exchanges` (direct + topic).
3. Logs de reintentos y DLQ de `users-service`.
4. Screenshot `POST /api/rabbit/queues` con nombre vacío → 400.
5. Screenshot Gateway (routes/authorizer) y `curl` 401/200.
6. Screenshot `/admin/rabbitmq` en el navegador.

Chequeo previo (PowerShell):
```powershell
docker compose up -d
docker exec rabbitmq1 rabbitmqctl cluster_status
curl https://3cbwduklgb.execute-api.us-east-1.amazonaws.com/api/public/products  # 200
curl https://3cbwduklgb.execute-api.us-east-1.amazonaws.com/api/rabbit/queues    # 401 sin token
```

---

## 7. Posibles preguntas (y respuesta)

**¿Por qué el exchange de negocio es `topic` y el de muertos `direct`?**
*"El topic permite enrutar por patrón (`order.created`, `product.*`) y escalar a
nuevos eventos sin tocar los bindings. Para la DLQ queremos enrutar por la routing
key exacta a la cola de muertos correspondiente, así que `direct` es suficiente y
más preciso."*

**¿Cómo funciona el reintento?**
*"El consumidor lee el header `x-retry-count`, reprograma el mensaje (republish) y
hace `basicAck` para no perderlo; al superar `retry.max-attempts` (3) hace
`basicNack(requeue=false)`, el broker lo enruta por el DLX `orders.dlx` a
`order.notification.dlq`."*

**¿Por qué `acknowledge-mode: manual`?**
*"Para decidir explícitamente entre ACK (procesado), republish + ACK (reintento) y
NACK a DLQ (fallo definitivo). Con ACK automático no podríamos diferenciar estos
casos ni evitar reprocesos."*

**¿Cómo se forma el clúster de 2 nodos?**
*"Ambos contenedores comparten la `RABBITMQ_ERLANG_COOKIE`; con peer discovery
(`rabbit_peer_discovery_classic_config`) el segundo nodo se une a
`rabbit@rabbitmq1`. `rabbitmqctl cluster_status` muestra ambos como `disc`."*

**¿Qué pasa si cae un nodo?**
*"Las conexiones del cliente se reconectan al otro nodo. Para HA real de colas se
recomienda usar *quorum queues* (replicadas); hoy usamos colas `durable` y el
clúster garantiza disponibilidad de brokers y del plano de administración."*

**¿Dónde validan los parámetros de entrada?**
*"En DTOs con Bean Validation (`@NotBlank`, `@Pattern`) y una validación adicional
de nombres en `RabbitAdminService` (no vacíos, sin prefijo reservado `amq.`);
`GlobalExceptionHandler` traduce a 400 con mensaje claro."*

**¿La mensajería afecta la lógica existente?**
*"No. El evento se publica tras confirmar el pedido; si falla la publicación se
registra el error, pero el CRUD sigue funcionando igual. La configuración vive en
`RabbitMQConfig`/`MessagingProperties`, no en controladores ni servicios."*
