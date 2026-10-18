# Guía de Presentación — Evaluación Parcial N°4 (Mensajería RabbitMQ)

> Presentación del sistema con mensajería asíncrona. **Evaluación individual · 5–10 min · lenguaje técnico.**
> Esta guía tiene 3 partes: **(A) preparación**, **(B) paso a paso de la demo** y **(C) guion palabra por palabra**.
> Para la rúbrica detallada, la referencia de bolsillo es `PRESENTATION_GUIDE_ENTREGA2.md`.

---

## 0. Hilo conductor (la frase que abre y cierra)

> "El frontend Angular (GitHub Pages) se autentica con **Entra ID vía OAuth2/OIDC**, obtiene un
> **JWT** y consume las APIs a través del **API Gateway (API Manager)**, que **valida el token**.
> El backend Spring Boot en **EC2** desacopla el flujo de pago con **RabbitMQ**: exchanges
> **fanout** (`payment.exchange` → `receipt.exchange`) que ramifican el pago en aviso,
> simulación con boleta PDF, correo, revisión de pedido y descuento de stock; con DLX
> **direct** y **ACK/NACK** con reintentos que drenan a **DLQ**, administrados por un
> microservicio REST; y toda la mensajería corre sobre un **clúster RabbitMQ de 2 nodos**
> levantado con **docker-compose**."

---

## A. Preparación (hacer 20–30 min antes, fuera de cámara)

### A.1 Checklist de servicios encendidos

```powershell
# 1) Clúster RabbitMQ de 2 nodos
docker compose up -d
docker ps                                              # deben aparecer rabbitmq1 y rabbitmq2

# 2) Clúster realmente unido (2 nodos disc)
docker exec rabbitmq1 rabbitmqctl cluster_status
#   -> {nodes,[{disc,['rabbit@rabbitmq1','rabbit@rabbitmq2']}]}  (2 nodos)

# 3) Backend en EC2 (6 servicios activos)
ssh -i .\tuki-key.pem ubuntu@184.192.252.196 "systemctl is-active tuki-products tuki-categories tuki-users tuki-orders tuki-rabbit-admin tuki-payments"
#   -> active (x6)

# 4) API Gateway responde (endpoint público, sin token)
curl.exe https://3cbwduklgb.execute-api.us-east-1.amazonaws.com/api/public/products
#   -> 200 + JSON

# 5) API Gateway rechaza sin token (JWT authorizer)
curl.exe -i https://3cbwduklgb.execute-api.us-east-1.amazonaws.com/api/rabbit/queues
#   -> 401
```

### A.1b — Conectar el EC2 a tu RabbitMQ local (túnel SSH)  ⚠️ obligatorio

El backend corre en **EC2**, pero las colas de la demo deben verse en **tu** RabbitMQ
local (`localhost:15672`). Como el EC2 no puede alcanzar tu `localhost`, el EC2 se
conecta a tu PC por un **túnel SSH inverso** (`ssh -R`), sin instalar nada extra.

**1) Abrir el túnel** (ventana propia, **déjala abierta toda la demo**):

```powershell
ssh -i .\tuki-key.pem -N -R 127.0.0.1:5672:127.0.0.1:5672 -R 127.0.0.1:15672:127.0.0.1:15672 ubuntu@184.192.252.196
```

- `127.0.0.1:5672` = en el EC2 se abre el puerto AMQP y el tráfico va a tu PC.
- `127.0.0.1:15672` = igual para la Management API (la que consulta `/admin/rabbit`).

**2) Verificar que el EC2 llega al broker LOCAL:**

```powershell
ssh -i .\tuki-key.pem ubuntu@184.192.252.196 "curl -s -u tuki:tuki http://127.0.0.1:15672/api/overview | head -c 60"
#   -> {"management_version":"3.13.7",...}   (versión del broker LOCAL, no CloudAMQP)
```

> **La config de los 6 servicios en el EC2 ya quedó aplicada** para usar el broker
> local: `RABBITMQ_HOST=127.0.0.1`, `RABBITMQ_PORT=5672`, `RABBITMQ_SSL=false`,
> `tuki/tuki`, vhost `/`; el admin además `RABBITMQ_MANAGEMENT_URL=http://127.0.0.1:15672`.
> Vive en *drop-ins* de systemd
> (`/etc/systemd/system/tuki-<servicio>.service.d/override-rabbitmq.conf`).
> Si algún día se revierte a CloudAMQP, reaplica con el **Anexo G**.

**Si el túnel se cae** (se nota porque las colas dejan de recibir mensajes), vuelve a
ejecutar el comando del paso 1: los servicios se reconectan solos.

### A.2 Ventanas que dejar abiertas (en pestañas, en este orden)

1. Frontend: **https://pastito-247.github.io/CLOUD-NATIVE-I/** (logueado como `admin`).
2. Consola AWS → **API Gateway** → HTTP API `tuki-api` (stages `$default`, authorizer `tuki-jwt`).
3. Consola Azure → **Microsoft Entra ID** (tenant `tuki-tech` → Usuarios).
4. RabbitMQ UI nodo 1: **http://localhost:15672** (`tuki` / `tuki`).
5. RabbitMQ UI nodo 2: **http://localhost:15673** (`tuki` / `tuki`).
6. Editor con el árbol de `backend/payments-service/src/main/java/com/ecommerce/payments/`.
7. Frontend redirigido a **/admin/rabbitmq** (vista de administración).
8. Terminal del **túnel SSH** (A.1b) — **no cerrarla**; si se cae, las colas se dejan de llenar.

### A.3 Terminales

- **Terminal 0 (túnel):** el `ssh -R` de la sección **A.1b**, corriendo y sin cerrar.
- Terminal 1: PowerShell en la raíz del repo (comandos `docker`/`curl`).
- Terminal 2: SSH a EC2 para ver logs: `ssh -i .\tuki-key.pem ubuntu@184.192.252.196`
  y dentro `journalctl -u tuki-payments -f` (o `-u tuki-orders`, `-u tuki-users`).

### A.4 Datos siempre a mano

| Elemento | Valor |
|---|---|
| Frontend (GitHub Pages) | `https://pastito-247.github.io/CLOUD-NATIVE-I/` |
| API Gateway (HTTP API) | `https://3cbwduklgb.execute-api.us-east-1.amazonaws.com` |
| Tenant Entra ID | `e3e92dfe-ea59-4c42-a539-90e6fea570b6` (tuki-tech.onmicrosoft.com) |
| Client ID (SPA) | `66c0f84b-3701-42fb-9189-3f4092cba201` |
| Scope | `api://tuki-tech-api/access_as_user` |
| Audience (JWT backend) | `api://tuki-tech-api` **y** `66c0f84b-...` |
| Backend EC2 | products 8081, categories 8082, users 8083, orders 8084, **rabbitmq-admin 8085**, **payments 8086** |
| EC2 | `184.192.252.196` (user `ubuntu`, llave `tuki-key.pem`) |
| Cluster RabbitMQ | nodos `rabbitmq1` (:15672) y `rabbitmq2` (:15673), cred. `tuki/tuki` |
| Broker que usa el EC2 | `127.0.0.1:5672`/`:15672` **vía túnel SSH** (`ssh -R`) → RabbitMQ local |
| Vista admin | `/admin/rabbitmq` |

---

## B. Paso a paso de la demo (orden recomendado)

La pauta pide 10 evidencias concretas. Este orden las cubre todas sin ir y volver:

| Bloque | Evidencia de la pauta que cubre |
|---|---|
| 1. Arquitectura | (contexto) |
| 2. Código: config + beans + listeners | requisitos de implementación (rúbrica 1–4) |
| 3. Flujo de pago + DLQ | **ACK/NACK + DLQ**, exchanges fanout/direct |
| 4. Microservicio admin REST | **admin REST** (`POST/DELETE /queues`), validación |
| 5. Nube: API Gateway + IDaaS | **API Manager**, **JWT**, **tenant + usuarios**, **OAuth2/OIDC** |
| 6. Nube: RabbitMQ | **2 nodos en clúster**, **docker-compose**, exchanges direct + topic, colas + DLQ |
| 7. Cierre | **backend + frontend desplegados/integrados** |

### Bloque 1 — Arquitectura (30–45 s)

1. Muestra el diagrama / el hilo conductor.
2. Menciona: los endpoints REST **no cambiaron**; el evento se publica **después de persistir**.
3. Señala la topología: `payment.exchange`/`receipt.exchange` (fanout) → 5 colas + DLX (direct).

### Bloque 2 — Código (2 min) — evidencia de la rúbrica 1–4

1. **Nombres centralizados (12%)**: abre `backend/orders-service/src/main/resources/application.yml`
   y muestra la sección `messaging:` (exchange/queue/routing-key). Abre
   `.../config/MessagingProperties.java` (`@ConfigurationProperties`) — mismo patrón en los 5 servicios.
2. **Beans Queue/Exchange/Binding (13%)**: abre
   `backend/payments-service/.../config/RabbitMQConfig.java` y muestra:
   - `FanoutExchange paymentExchange()` / `receiptExchange()` (líneas 52–60)
   - `DirectExchange paymentDeadLetterExchange()` / `receiptDeadLetterExchange()` (62–70)
   - `Queue paymentScreenQueue()` con `.deadLetterExchange(...)` (85–90)
   - los `Binding` al final del archivo.
3. **Separación config / lógica (10%)**: muestra el árbol de paquetes de `payments-service`:
   `config/` (RabbitMQConfig, MessagingProperties) · `messaging/` (consumidores) · `service/` (negocio).
   *Frase:* "el servicio de dominio no importa nada de RabbitMQ".
4. **`@RabbitListener` por dominio (15%)**: muestra los consumidores agrupados:
   `payments.messaging.PaymentScreenConsumer`, `users.messaging.ReceiptEmailConsumer`,
   `orders.messaging.OrderReviewConsumer`, `products.messaging.StockDecrementConsumer`,
   y los `*DeadLetterLogConsumer` (uno por servicio escucha sus `.dlq`).

### Bloque 3 — Flujo de pago + DLQ en vivo (2 min) — **rúbrica 5 (20%)**

Con la Terminal 2 viendo logs: `journalctl -u tuki-payments -f` (y en otra `-u tuki-users -f`).

**3.1 Pago normal** (pasa por el API Gateway; si no tienes token, hazlo desde la UI):

```powershell
curl.exe -X POST https://3cbwduklgb.execute-api.us-east-1.amazonaws.com/api/payments `
  -H "Content-Type: application/json" `
  -d '{\"userEmail\":\"mar.cespedesg@duocuc.cl\",\"amount\":45990,\"method\":\"tarjeta\",\"shippingAddress\":\"Av. Siempre Viva 742\",\"items\":[{\"productId\":1,\"productName\":\"Polera\",\"quantity\":1,\"unitPrice\":45990}]}'
```

Logs que debes narrar:
- `payments`: `payment.requested publicado` → fanout a `payment.notification.queue` + `payment.screen.queue`.
- `payments` (`PaymentScreenConsumer`): simula el pago **5 s** (`PAYMENT_DELAY_MS`) → genera **boleta PDF** → publica `receipt.generated`.
- fanout a `receipt.email.queue` + `order.review.queue` + `stock.decrement.queue`.
- `users` (`ReceiptEmailConsumer`): **envía el correo** con la boleta adjunta.
- `orders` (`OrderReviewConsumer`): pedido `PENDING` → `CONFIRMED` (refresca `/my-orders`).

**3.2 Pago que falla → DLQ** (mismo endpoint, `userEmail` con `fail@`):

```powershell
curl.exe -X POST https://3cbwduklgb.execute-api.us-east-1.amazonaws.com/api/payments `
  -H "Content-Type: application/json" `
  -d '{\"userEmail\":\"fail@demo.cl\",\"amount\":1000,\"method\":\"tarjeta\",\"items\":[{\"productId\":1,\"productName\":\"X\",\"quantity\":1,\"unitPrice\":1000}]}'
```

Narra los logs de `PaymentScreenConsumer`:
- `Intentando procesar pago ... (intento 1/3)` … `intento 3/3`
- `Reintentos agotados ... Enviando a la DLQ`
- `[DLQ:payment.screen]` (lo imprime `DeadLetterLogConsumer`)

*Frase técnica:* "el consumidor lee el header `x-retry-count`, hace **republish + `basicAck`** para no
perder el mensaje, y al agotar los intentos hace **`basicNack(requeue=false)`**; el broker lo enruta por
`payment.dlx` (direct) a `payment.screen.dlq`". Si el `markFailed` falla, igual se hace NACK
(por eso el fix del poison message).

**3.3 Evidencia de la DLQ**: el mensaje aterriza en `payment.screen.dlq`, pero el
`DeadLetterLogConsumer` **la consume y hace ACK al instante**, así que en la UI casi no
queda `Ready = 1` (**es normal, no lo tomes como error**). La evidencia sólida es el
**log** en Terminal 2:

```bash
[DLQ:payment.screen] Mensaje no entregado registrado. motivo=[{reason=rejected, count=1,
  exchange=, routing-keys=[payment.screen.queue], queue=payment.screen.queue}]
  payload={"paymentId":999999,...,"userEmail":"fail@demo.cl",...}
```

*Frase:* "el consumidor hizo `basicNack(requeue=false)`, el broker enrutó por `payment.dlx`
(direct) hacia `payment.screen.dlq`, y el `DeadLetterLogConsumer` lo registró y confirmó
con ACK para no dejarlo atascado".

> **Truco** para ver `Ready = 1` en la UI: manda el pago `fail@` y abre la cola
> `payment.screen.dlq` **rápido** (antes de ~1 s) para pillar el mensaje antes de que el
> log-consumer lo tome.

### Bloque 4 — Microservicio admin REST (1 min) — rúbrica 6–8 (30%)

En la UI del frontend abre **/admin/rabbitmq** (requiere rol admin) y muestra listar/crear/borrar.
Alternativa por terminal (necesitas token del Gateway; en la UI ya está resuelto):

```powershell
# Crear cola -> 201
curl.exe -i -X POST https://3cbwduklgb.execute-api.us-east-1.amazonaws.com/api/rabbit/queues `
  -H "Content-Type: application/json" -d '{\"name\":\"demo.queue\",\"durable\":true}'

# Validación: nombre vacío -> 400
curl.exe -i -X POST https://3cbwduklgb.execute-api.us-east-1.amazonaws.com/api/rabbit/queues `
  -H "Content-Type: application/json" -d '{\"name\":\"\"}'

# Prefijo reservado amq. -> 400
curl.exe -i -X POST https://3cbwduklgb.execute-api.us-east-1.amazonaws.com/api/rabbit/queues `
  -H "Content-Type: application/json" -d '{\"name\":\"amq.test\"}'

# Listar / borrar
curl.exe https://3cbwduklgb.execute-api.us-east-1.amazonaws.com/api/rabbit/queues
curl.exe -i -X DELETE https://3cbwduklgb.execute-api.us-east-1.amazonaws.com/api/rabbit/queues/demo.queue
```

Puntos a destacar:
- Endpoints: `POST/GET/DELETE /api/rabbit/queues|exchanges|bindings` (`RabbitAdminController.java:58-125`).
- **Toda la lógica está en `RabbitAdminService`** (`.../service/RabbitAdminService.java`); el controller
  solo llama métodos de alto nivel (mide **10%**). El service soporta `direct`, `topic`, `fanout`, `headers`
  (líneas 161–164).
- **Validación (7%)**: `QueueDefinitionRequest` con `@NotBlank`/`@Pattern` + `validateName` (no vacío,
  sin `amq.`) → `GlobalExceptionHandler` → **400** con mensaje.

### Bloque 5 — Nube: API Gateway + IDaaS (1 min)

1. **API Manager funcionando**: consola AWS → API Gateway → `tuki-api` → stage `$default` → *Invoke URL*
   (es la URL que usa el frontend).
2. **Config que llama a los endpoints backend**: Routes → Integrations apuntando a EC2
   (`ANY /api/{proxy+}` → `http://184.192.252.196:808x`), authorizer JWT `tuki-jwt`.
3. **Frontend consume vía API Manager**: F12 → Network → recarga la home → la petición va a
   `https://3cbwduklgb.execute-api...` (no directo a EC2). `environment.prod.ts:7-16`.
4. **API Manager valida JWT**: ya mostraste `curl` **401** sin token (prep A.1). Ahora repite con token
   válido desde la consola del navegador (`sessionStorage`/`localStorage` → `accessToken`) → **200**.
5. **Tenant IDaaS + usuarios**: consola Azure → Entra ID → Usuarios (`admin`, `customer`, `staff`).
6. **OAuth2/OIDC → JWT**: en la app pulsa *Login* → redirige a `login.microsoftonline.com/{tenant}` →
   vuelve logueado. Pega el token en **https://jwt.ms** y muestra `iss`, `aud` (`api://tuki-tech-api`)
   y `roles` (el frontend usa MSAL: `environment.prod.ts:20-36`).

### Bloque 6 — Nube: RabbitMQ (1 min)

1. **docker-compose levanta RabbitMQ**: en Terminal 1, `docker compose up -d` → `docker ps`
   (rabbitmq1/rabbitmq2). `docker-compose.yml` (2 servicios `rabbitmq:3.13-management`, cookie compartida).
   *Nota técnica:* el backend en **EC2** consume este clúster local a través de un **túnel SSH
   inverso** (`ssh -R`, sección A.1b): en el EC2 los servicios apuntan a `127.0.0.1:5672` y el
   tráfico AMQP aterriza en este `rabbitmq1`.
2. **2 nodos en clúster**: `docker exec rabbitmq1 rabbitmqctl cluster_status` → 2 nodos `disc`.
   Abre :15672 y :15673: **ambas UIs muestran las mismas colas** (topología compartida del clúster).
3. **Exchanges direct y topic**: en la UI de RabbitMQ → Exchanges:
   - **topic**: `catalog.exchange` (routing por patrón: `product.created`, `category.*`).
   - **direct**: `payment.dlx`, `receipt.dlx`, `order.dlx`, `user.dlx`, `catalog.dlx`.
   - (además **fanout**: `payment.exchange`, `receipt.exchange`, `order.exchange`, `user.exchange`).
4. **Colas + DLQ**: en Queues muestra `payment.notification.queue`, `payment.screen.queue`,
   `receipt.email.queue`, `order.review.queue`, `stock.decrement.queue`, `order.created.queue`,
   `user.registered.queue`, `product.sync.queue`, `category.sync.queue` **+ sus `.dlq`**.
   Menciona que ya viste `payment.screen.dlq` recibir el mensaje del Bloque 3.

### Bloque 7 — Cierre (30 s)

- "Backend en **EC2** (6 servicios `active`) y frontend en **GitHub Pages**, **integrados** por el API Gateway."
- Recorre mentalmente la tabla de la rúbrica y cierra con la frase del hilo conductor.

---

## C. Guion palavra por palabra (versión 8–9 min)

> Sustituye `[X]` por lo que estés mostrando. Habla en presente, pausado, técnico.

**(0:00–0:45) Arquitectura.**
"Buenos días. Mi sistema es una tienda con frontend Angular, backend Spring Boot en EC2 y mensajería
asíncrona con RabbitMQ. El frontend se autentica con Entra ID vía OAuth2/OIDC y consume las APIs a
través del API Gateway, que valida el JWT. La novedad de esta entrega es la mensajería: el flujo de pago
se desacopla con dos exchanges *fanout*, que ramifican un pago en cinco casos de uso, con *dead-letter*
y ACK/NACK. Todo sobre un clúster de dos nodos RabbitMQ levantado con docker-compose."

**(0:45–2:45) Código.**
"Empiezo por mostrar que los nombres no están dispersos: están centralizados en `application.yml` y
tipados con `@ConfigurationProperties` en `MessagingProperties` —igual en los cinco microservicios—.
Los recursos se declaran como **beans** en `RabbitMQConfig`: aquí ven `paymentExchange` y `receiptExchange`
como *fanout*, los DLX como *direct*, y las colas con su `deadLetterExchange`. La configuración vive en
el paquete `config/`, separada de `messaging/` y de `service/`: el dominio no conoce RabbitMQ. Y los
consumidores están agrupados por dominio con `@RabbitListener`: pagos, usuarios, pedidos y productos,
cada uno con su consumidor de DLQ."

**(2:45–4:45) Flujo de pago + DLQ.**
"Ahora un pago real. Mando `POST /api/payments` a través del Gateway. Miren los logs: se persiste el pago
y se publica `payment.requested` al exchange *fanout* `payment.exchange`, que copia el mensaje a dos
colas: notificación y simulación. `PaymentScreenConsumer` simula la pasarela cinco segundos, genera la
boleta PDF y publica `receipt.generated`; ese segundo *fanout* dispara correo, revisión de pedido y
descuento de stock. El correo llega y el pedido pasa de *Pendiente* a *Confirmado*.
Ahora el caso de error: mando un pago con un correo `fail@`. El consumidor reintenta: lee `x-retry-count`,
republica y hace `basicAck` para no perder el mensaje. Al tercer intento agota y hace
`basicNack(requeue=false)`: el broker lo enruta por `payment.dlx`, que es *direct*, hacia
`payment.screen.dlq`. Aquí en la UI ven la DLQ con un mensaje."

**(4:45–5:45) Admin REST.**
"Para operar la mensajería hay un microservicio de administración en el puerto 8085 con endpoints REST:
crear, listar y borrar colas, exchanges y bindings. La lógica está toda encapsulada en `RabbitAdminService`;
el controlador solo delega. Creo una cola: 201. Si mando el nombre vacío, o un prefijo reservado `amq.`,
la validación responde 400. También está expuesto en esta vista de administración del frontend."

**(5:45–6:45) Nube: API Gateway + IDaaS.**
"En la nube: el API Gateway tiene las rutas que integran al backend en EC2 y un *authorizer* JWT
`tuki-jwt`. Sin token responde 401; con un JWT válido, 200. El frontend consume por esta URL, no directo
al backend. La identidad es Entra ID: en este tenant están los usuarios admin, customer y staff. Al hacer
login, MSAL hace el flujo OAuth2/OIDC, recibe el JWT y lo adjunta como Bearer; el `iss`, `aud` y los
`roles` se ven en jwt.ms."

**(6:45–7:45) Nube: RabbitMQ.**
"Finalmente la mensajería. `docker compose up -d` levanta dos nodos RabbitMQ 3.13 con una cookie compartida.
`rabbitmqctl cluster_status` confirma que están unidos en clúster: dos nodos *disc*. Ambas UIs, 15672 y
15673, muestran la misma topología. Aquí están los exchanges: `catalog.exchange` es *topic* porque enruta
por patrón `product.created` y `category.*`; los `.dlx` son *direct*; y los de pago y recibo son *fanout*.
Y aquí están las nueve colas con sus nueve DLQ."

**(7:45–8:15) Cierre.**
"En resumen: backend en EC2 y frontend en GitHub Pages, integrados por el API Gateway; identidad con
Entra ID; y un flujo de pago asíncrono, tolerante a fallos, con DLQ y administración REST, sobre un
clúster RabbitMQ de dos nodos levantado con docker-compose. Gracias."

---

## D. Preguntas del profesor (y respuesta)

- **¿Por qué fanout para el pago, topic para catálogo y direct para la DLQ?**
  *"El pago es un hecho que varios consumidores independientes necesitan: fanout copia sin routing keys.
  El catálogo necesita filtrar por patrón (`product.created`, `category.*`): topic. La DLQ necesita
  enrutar por la routing key exacta de cada cola: direct."*
- **¿Cómo funciona el reintento?** *"Header `x-retry-count`: republish + `basicAck`; al superar 3,
  `basicNack(requeue=false)` → DLX → DLQ."*
- **¿Por qué `acknowledge-mode: manual`?** *"Para distinguir ACK (ok), republish+ACK (reintento) y NACK
  (fallo definitivo); con ACK automático no se puede y hay reprocesos."*
- **¿Cómo se forma el clúster?** *"Cookie Erlang compartida + peer discovery
  (`rabbit_peer_discovery_classic_config`); el nodo 2 se une a `rabbit@rabbitmq1`."*
- **¿Qué pasa si cae un nodo?** *"El cliente se reconecta al otro; para HA de colas se usarían quorum
  queues; hoy las colas son durable y el clúster da disponibilidad de brokers y del plano de administración."*
- **¿La mensajería alteró la lógica existente?** *"No: los endpoints siguen igual y el evento se publica
  después de persistir; si falla la publicación se loguea pero el CRUD sigue."*
- **¿Dónde se validan los parámetros?** *"Bean Validation en los DTOs + validación de nombres en
  `RabbitAdminService`; `GlobalExceptionHandler` traduce a 400."*
- **¿La DLQ se reprocesa automáticamente?** *"No; hoy se monitorea (Consumers de DLQ loguean). El
  reproceso es manual, para evitar bucles infinitos."*

---

## E. Plan B (si falla el demo en vivo) y troubleshooting

**Evidencias de respaldo (capturas previas, tenerlas en una carpeta):**
1. `cluster_status` con 2 nodos.
2. `GET /api/rabbit/queues` (colas + DLQ) y `/exchanges` (fanout + direct + topic).
3. Logs de reintentos 1..3 y `[DLQ:payment.screen]`.
4. `POST /api/rabbit/queues` con `name:""` → 400.
5. Consola API Gateway (routes + authorizer) y `curl` 401 vs 200.
6. Vista `/admin/rabbitmq`.
7. `docker ps` con rabbitmq1/rabbitmq2.

**Problemas frecuentes:**

| Síntoma | Causa probable | Solución |
|---|---|---|
| `curl` al Gateway da 401 en `/api/public/...` | authorizer mal aplicado a la ruta pública | revisar la Route (debe ser `Auth: NONE`) |
| La DLQ no recibe el mensaje del `fail@` | el consumidor no hizo NACK (bug poison) | está corregido en `PaymentScreenConsumer`; revisar logs `Reintentos agotados` |
| `cluster_status` muestra 1 nodo | nodos sin unir / cookie distinta | `docker compose down -v` y `up -d`; verificar `RABBITMQ_ERLANG_COOKIE` |
| Las colas dejan de llenarse / están vacías en `localhost:15672` | **túnel SSH caído** | re-ejecutar el `ssh -R` de **A.1b**; los servicios se reconectan solos |
| No aparece nada y el EC2 no conecta (o va a CloudAMQP) | config del EC2 revertida | reaplicar **Anexo G** y `systemctl restart` de los 6 servicios |
| `/admin/rabbit` lista colas pero no coinciden con tu local | management apunta a CloudAMQP | en el admin, `RABBITMQ_MANAGEMENT_URL=http://127.0.0.1:15672` (Anexo G) |
| Correo no llega | SMTP / `MAIL_ENABLED` | es no bloqueante para el demo; muestra el log de envío |
| Payload en cola con error de tipo | falta header `__TypeId__` | publica desde la app, no a mano por la mgmt API |
| Token inválido en jwt.ms | audience incorrecta | el backend acepta `api://tuki-tech-api` **y** `66c0f84b-...` |

**Si no hay internet/EC2 caído:** muestra frontend en local (`ng serve`), luego las capturas del Bloque 5
y 6, y enfoca el guion en código + RabbitMQ local (que es lo evaluado).

---

## F. Mapa rápido de la rúbrica (dónde se evidencia)

| # | Indicador | % | Dónde |
|---|---|---|---|
| 1 | Nombres centralizados en `application.yml`/`@Configuration` | 12% | `messaging.*` + `MessagingProperties` |
| 2 | Beans `Queue`/`Exchange`/`Binding` por caso de uso | 13% | `config/RabbitMQConfig.java` (5 servicios) |
| 3 | Config separada de la lógica de negocio | 10% | paquetes `config/` vs `messaging/` vs `service/` |
| 4 | `@RabbitListener` agrupados por dominio | 15% | `*.messaging.*Consumer` |
| 5 | ACK/NACK + errores + DLQ | 20% | `ack.-mode: manual`; `basicAck`/republish/`basicNack` |
| 6 | Admin REST (`POST/DELETE /queues`) | 13% | `rabbitmq-admin-service` :8085, `/api/rabbit/**` |
| 7 | Lógica en `RabbitAdminService` | 10% | `rabbitadmin/service/RabbitAdminService.java` |
| 8 | Validación de entrada | 7% | DTOs `@NotBlank`/`@Pattern` + `validateName` + `GlobalExceptionHandler` |

---

## Anexo G — Config del EC2 hacia el broker (local ↔ CloudAMQP)

Los 6 servicios del EC2 toman su broker de un *drop-in* de systemd:
`/etc/systemd/system/tuki-<servicio>.service.d/override-rabbitmq.conf`
servicios: `tuki-products tuki-categories tuki-users tuki-orders tuki-rabbit-admin tuki-payments`.

### G.1 Apuntar al broker LOCAL (para la demo)

Contenido de `override-rabbitmq.conf` en **los 6** servicios:

```ini
[Service]
Environment=RABBITMQ_HOST=127.0.0.1
Environment=RABBITMQ_PORT=5672
Environment=RABBITMQ_SSL=false
Environment=RABBITMQ_USERNAME=tuki
Environment=RABBITMQ_PASSWORD=tuki
Environment=RABBITMQ_VHOST=/
```

Y en el admin, un drop-in extra
(`/etc/systemd/system/tuki-rabbit-admin.service.d/override-management.conf`):

```ini
[Service]
Environment=RABBITMQ_MANAGEMENT_ENABLED=true
Environment=RABBITMQ_MANAGEMENT_URL=http://127.0.0.1:15672
```

Luego:

```bash
sudo systemctl daemon-reload
sudo systemctl restart tuki-products tuki-categories tuki-users tuki-orders tuki-rabbit-admin tuki-payments
systemctl is-active tuki-products tuki-categories tuki-users tuki-orders tuki-rabbit-admin tuki-payments
```

> Requiere el **túnel SSH** de la sección A.1b corriendo. Verifica con
> `curl -s -u tuki:tuki http://127.0.0.1:15672/api/overview`.

### G.2 Volver a CloudAMQP (fuera de la demo)

En `override-rabbitmq.conf` de los 6 servicios usa:

```ini
[Service]
Environment=RABBITMQ_HOST=leopard.lmq.cloudamqp.com
Environment=RABBITMQ_PORT=5671
Environment=RABBITMQ_SSL=true
Environment=RABBITMQ_USERNAME=<tu-usuario-cloudamqp>
Environment=RABBITMQ_PASSWORD=<tu-password-cloudamqp>
Environment=RABBITMQ_VHOST=<tu-vhost>
```

En el admin, `RABBITMQ_MANAGEMENT_URL=https://leopard.lmq.cloudamqp.com`.
Repite `daemon-reload` + `restart`. (Credenciales originales respaldadas en
`/tmp/rabbit-bkp/` del EC2, si aún existen; si no, búscalas en el panel de CloudAMQP.)
