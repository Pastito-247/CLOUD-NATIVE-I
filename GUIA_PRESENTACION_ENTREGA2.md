# Guía de Presentación — Compra, pago y RabbitMQ en vivo

> **Evaluación individual · 5–10 min · lenguaje técnico.**
> Enfoque de esta demo: mostrar la página, explicar las colas, ejecutar una compra + pago,
> y terminar mostrando el panel de RabbitMQ en `localhost` para explicar cómo funcionó la mensajería.

---

## 0. Guion en una línea

| Tiempo | Qué haces |
|---|---|
| 0:00–0:45 | Abres la página y presentas el flujo en 3 frases |
| 0:45–2:15 | Explicas la topología de colas en el panel de RabbitMQ |
| 2:15–5:00 | Haces la compra y el pago desde la página |
| 5:00–7:00 | Vuelves a RabbitMQ y muestras el monitoreo de lo que ocurrió |
| 7:00–8:00 | Cierras y respondes preguntas |

**Frase de apertura:**
> "El frontend Angular en GitHub Pages se autentica con Entra ID y consume las APIs por el API Gateway.
> El backend Spring Boot en EC2 desacopla la compra y el pago con RabbitMQ: `payment.exchange` y
> `receipt.exchange` son *fanout*, los DLX son *direct* y las colas tienen ACK/NACK con reintentos y DLQ.
> RabbitMQ corre en Docker dentro del mismo EC2."

---

## 1. Antes de empezar (10–15 min antes)

### 1.1 Verificar backend y broker

```powershell
# RabbitMQ en Docker + los 6 servicios del backend
ssh -i .\tuki-key.pem ubuntu@184.192.252.196 "docker ps --filter name=rabbitmq --format '{{.Names}} {{.Status}}'; systemctl is-active tuki-products tuki-categories tuki-users tuki-orders tuki-rabbit-admin tuki-payments"
```

Debe mostrar `rabbitmq Up (healthy)` y `active` seis veces.

### 1.2 Abrir el panel RabbitMQ en `localhost` (túnel SSH)

El broker **no corre en tu PC**: corre en Docker dentro del EC2. Para verlo en `localhost` abre un túnel
de puerto local **en una ventana aparte** y déjalo abierto durante toda la demo:

```powershell
ssh -i .\tuki-key.pem -N -L 15672:127.0.0.1:15672 ubuntu@184.192.252.196
```

Luego abre:

- **http://localhost:15672**
- Usuario: `tuki` · Contraseña: `tuki`

> El túnel lleva `localhost:15672` de tu PC al `127.0.0.1:15672` del EC2 por SSH.
> **No hace falta abrir el puerto 15672 en el Security Group** y el puerto AMQP `5672` sigue cerrado a internet.
> Si el túnel se cae, **la mensajería sigue funcionando** (el backend habla con el broker por loopback en el EC2);
> solo dejas de ver el panel hasta que lo vuelvas a abrir.

### 1.3 Pestañas que debes tener abiertas

1. **Frontend:** https://pastito-247.github.io/CLOUD-NATIVE-I/ (logueado).
2. **RabbitMQ → Overview:** `http://localhost:15672/#/`.
3. **RabbitMQ → Queues:** `http://localhost:15672/#/queues`.
4. **Terminal de logs** (opcional, pero muy útil):

```powershell
ssh -i .\tuki-key.pem ubuntu@184.192.252.196 "sudo journalctl -u tuki-payments -u tuki-orders -u tuki-users -f"
```

### 1.4 Datos a mano

| Elemento | Valor |
|---|---|
| Frontend | `https://pastito-247.github.io/CLOUD-NATIVE-I/` |
| Panel RabbitMQ | `http://localhost:15672` (vía túnel SSH) |
| Credenciales RabbitMQ | `tuki` / `tuki` |
| EC2 | `184.192.252.196` · usuario `ubuntu` · llave `tuki-key.pem` |
| Servicios backend | 8081 products · 8082 categories · 8083 users · 8084 orders · 8085 rabbit-admin · 8086 payments |
| Colas | 18: 9 funcionales + 9 DLQ, todas con 1 consumidor |

---

## 2. Topología que vas a explicar

### 2.1 Los números

- **5 exchanges de negocio:** `catalog` (topic), `order`, `payment`, `receipt`, `user` (fanout).
- **5 exchanges de descarte:** `catalog.dlx`, `order.dlx`, `payment.dlx`, `receipt.dlx`, `user.dlx` (direct).
- **18 colas:** 9 funcionales + 9 DLQ.
- Cada cola tiene **1 consumidor** y es **durable**.

### 2.2 Qué hace cada exchange

| Exchange | Tipo | Colas | Para qué |
|---|---|---|---|
| `catalog.exchange` | topic | `product.sync.queue`, `category.sync.queue` | Sincronizar catálogo por patrón (`product.created`, `category.updated`, etc.) |
| `order.exchange` | fanout | `order.created.queue` | Avisar que se creó un pedido |
| `payment.exchange` | fanout | `payment.notification.queue`, `payment.screen.queue` | Aviso de pago + simulación de la pasarela |
| `receipt.exchange` | fanout | `receipt.email.queue`, `order.review.queue`, `stock.decrement.queue` | Correo, confirmación del pedido y descuento de stock |
| `*.dlx` | direct | `*.dlq` | Enrutar mensajes fallidos a su cola de descarte |

### 2.3 Flujo completo de una compra

```text
Checkout en la página
  │
  ├─ POST /orders ──► order.exchange (fanout)
  │                     └─► order.created.queue
  │                           └─► users-service: aviso de pedido nuevo
  │
  └─ POST /payments ─► payment.exchange (fanout)
                        ├─► payment.notification.queue: aviso "pago en proceso"
                        └─► payment.screen.queue: simula la pasarela 5 s
                              └─ genera boleta PDF y publica receipt.generated
                                    │
                                    └─► receipt.exchange (fanout)
                                          ├─► receipt.email.queue: correo con boleta
                                          ├─► order.review.queue: PENDING → CONFIRMED
                                          └─► stock.decrement.queue: descuenta stock
```

### 2.4 Frases clave (di estas ideas)

- **Fanout:** "copia el mismo evento a todos los consumidores independientes; no usa routing key".
- **Topic:** "filtra por patrón, por eso el catálogo usa `product.created` y `category.*`".
- **Direct:** "enruta por routing key exacta; se usa en los DLX para llegar a la DLQ correcta".
- **ACK/NACK:** "el consumidor confirma con ACK cuando termina; si falla, republica con `x-retry-count`
  hasta 3 intentos y al agotarlos hace `basicNack(requeue=false)`".
- **DLQ:** "el mensaje fallido se enruta por `*.dlx` a `*.dlq`; un consumidor de logs lo registra y lo
  confirma para que no quede atascado".

---

## 3. Demo paso a paso

### Paso 1 — Mostrar la página (1 min)

1. Abre la home: catálogo, login con Entra ID, carrito.
2. No te detengas en el CRUD: el profesor ya conoce la página.
3. Frase: "La página ya está desplegada y autenticada; lo nuevo es lo que pasa detrás al comprar".

### Paso 2 — Explicar las colas antes de ejecutar (2 min)

En **RabbitMQ → Overview**:

- Muestra **Totals**: mensajes, consumidores, conexiones.
- Las conexiones son los 6 microservicios + el admin.

En **Queues**:

- Ordena por nombre y muestra las 18 colas: 9 funcionales y 9 `.dlq`.
- Abre `payment.screen.queue` y muestra: consumidor `PaymentScreenConsumer`, `Ready`, `Unacked`.
- Abre una DLQ (`payment.screen.dlq`) y explica que normalmente está en 0 porque el consumidor de logs la vacía.

En **Exchanges**:

- `payment.exchange` y `receipt.exchange` son **fanout**.
- `catalog.exchange` es **topic**.
- `payment.dlx`, `receipt.dlx`, etc. son **direct**.
- Entra a un exchange y muestra sus **bindings** hacia las colas.

> Si el profesor pregunta por la vista de la app: `/admin/rabbitmq` muestra lo mismo desde el frontend,
> pero para esta demo el panel real es `localhost:15672`.

### Paso 3 — Hacer la compra y el pago (3 min)

1. En la página, agrega un producto al carrito.
2. Abre el carrito, escribe una **dirección de envío** y elige método de pago (por ejemplo, Tarjeta).
3. Pulsa **Finalizar compra**.
4. Narra lo que hace el frontend: **primero** `POST /orders` y **después** `POST /payments`.
5. Vuelve a **RabbitMQ → Queues** y observa la actividad:
   - `payment.notification.queue` y `payment.screen.queue` reciben el evento.
   - `payment.screen.queue` tarda ~5 s (simulación de la pasarela).
   - Luego aparecen `receipt.email.queue`, `order.review.queue` y `stock.decrement.queue`.
6. En la página, abre **Mis compras**: el pedido aparece **Pendiente** y en ~5 s pasa a **Confirmado**.
7. Si quieres, refresca el catálogo y muestra que el **stock bajó**.

> Importante: navega a **Mis compras** con clic dentro de la app. No pegues ni refresques
> `https://pastito-247.github.io/CLOUD-NATIVE-I/my-orders` directo: GitHub Pages devuelve 404 en rutas
> profundas de una SPA (la app igual carga, pero se ve feo en la consola).

### Paso 4 — Monitoreo: explicar qué ocurrió (2 min)

En **Overview**:

- **Message rates**: se ve el pico de publicación, entrega y ACK.
- **Queued messages**: sube y vuelve a 0; no se acumula porque los consumidores hacen ACK.
- **Connections / Channels**: los 6 servicios conectados.

En **Queues**:

- `Ready`: mensajes esperando consumidor.
- `Unacked`: mensajes entregados pero aún sin ACK.
- Después del pico, ambos vuelven a 0: **los consumidores confirmaron**.
- Si `Unacked` quedara alto, habría un consumidor colgado.

En **Exchanges → Bindings**:

- Muestra cómo `payment.exchange` está bindeado a las dos colas.
- Muestra cómo `payment.dlx` está bindeado a `payment.screen.dlq` y `payment.notification.dlq`.

Frase de cierre del bloque:
> "La página solo espera el `POST`; el resto es asíncrono. RabbitMQ distribuyó el evento, cada servicio
> procesó su parte, confirmó con ACK y las colas quedaron vacías. Si algo falla, el mensaje va a la DLQ
> en vez de perderse."

### Paso 5 — Cierre (30 s)

> "Backend en EC2 con 6 servicios y RabbitMQ en Docker; frontend en GitHub Pages; la compra y el pago se
> desacoplan con exchanges fanout y DLX direct; el panel de RabbitMQ se monitorea en `localhost` por un
> túnel SSH. El flujo es tolerante a fallos: ACK/NACK, reintentos y DLQ."

---

## 4. Preguntas probables

- **¿Por qué fanout en pago y recibo?**
  *"Porque varios consumidores independientes necesitan el mismo evento: notificación, simulación,
  correo, revisión y stock. Fanout copia el mensaje a todos sin routing key."*
- **¿Por qué topic en catálogo?**
  *"Porque necesita filtrar por patrón: `product.created`, `category.updated`, `category.deleted`."*
- **¿Por qué direct en las DLQ?**
  *"Porque cada cola tiene su routing key exacta: `payment.screen.dlq`, `order.review.dlq`, etc."*
- **¿Cómo funciona el reintento?**
  *"El consumidor lee el header `x-retry-count`, republica el mensaje y hace ACK para no perderlo.
  Al superar 3 intentos hace `basicNack(requeue=false)` y el broker lo manda por el DLX a la DLQ."*
- **¿Qué pasa si un consumidor está caído?**
  *"El mensaje queda en la cola durable; cuando el servicio vuelve, lo consume. No se pierde."*
- **¿La mensajería cambió los endpoints?**
  *"No. Los endpoints REST siguen igual; el evento se publica después de persistir."*
- **¿Por qué RabbitMQ en el EC2 y no en tu PC?**
  *"Porque el backend corre en EC2 y no hay Docker en mi PC. El broker queda en loopback del EC2 y el
  panel se ve en localhost con un túnel SSH."*

---

## 5. Plan B y problemas frecuentes

**Capturas de respaldo (tenerlas listas):**

1. Panel de RabbitMQ con las 18 colas y sus consumidores.
2. Pico de mensajes en `payment.screen.queue` y `receipt.email.queue`.
3. `Mis compras` con el pedido Confirmado.
4. Logs de `payment.requested publicado` y `receipt.generated publicado`.
5. Logs de reintentos y `[DLQ:payment.screen]` (si se muestra la DLQ).

| Síntoma | Causa probable | Solución |
|---|---|---|
| `localhost:15672` no abre | túnel SSH caído | volver a ejecutar `ssh -N -L 15672:127.0.0.1:15672 ...` |
| RabbitMQ muestra menos de 18 colas | servicios aún arrancando | esperar 15 s o reiniciar los servicios |
| La compra no genera actividad en las colas | backend apuntando a otro broker | revisar `journalctl -u tuki-orders -u tuki-payments` |
| El pedido queda en Pendiente | no llegó `receipt.generated` | revisar `order.review.queue` y logs de `tuki-orders` |
| `Mis compras` vacío | refrescaste `/my-orders` directo o se reinició orders (H2 en memoria) | navegar dentro de la app y hacer una compra nueva |
| La DLQ queda en 0 | el consumidor de DLQ hace ACK inmediato | la evidencia es el log `[DLQ:...]`, no el contador |
| Error 404 al abrir `/my-orders` | GitHub Pages no resuelve rutas profundas | entrar por la home y hacer clic en Mis compras |

---

## 6. Anexo A — Si el profesor pide ver la DLQ

No es necesario para el flujo normal, pero sirve para demostrar ACK/NACK y DLQ.

1. En **RabbitMQ → Exchanges**, abre `payment.exchange`.
2. Usa **Publish message** con este payload:

```json
{
  "paymentId": 999999,
  "orderId": 999999,
  "userEmail": "fail@demo.cl",
  "amount": 1000,
  "method": "CARD",
  "shippingAddress": "Demo",
  "items": [
    { "productId": 1, "productName": "Demo", "quantity": 1, "unitPrice": 1000 }
  ]
}
```

3. En **Properties** agrega:
   - `content_type`: `application/json`
   - Header `__TypeId__`: `com.ecommerce.payments.event.PaymentRequestedEvent`
4. Publica y abre `payment.screen.queue`: verás el mensaje procesarse y reintentarse.
5. A los ~17 s revisa los logs:

```bash
sudo journalctl -u tuki-payments -f | grep -E "reencolado|Reintentos agotados|DLQ:payment.screen"
```

Salida esperada:

```text
WARN  Mensaje del pago 999999 reencolado para reintento 1/3
WARN  Mensaje del pago 999999 reencolado para reintento 2/3
WARN  Mensaje del pago 999999 reencolado para reintento 3/3
ERROR Reintentos agotados para el pago 999999. Enviando a la DLQ 'payment.screen.dlq'
ERROR [DLQ:payment.screen] Mensaje no entregado registrado...
```

Frase:
> "El correo `fail@` hace fallar la simulación a propósito. El consumidor reintenta 3 veces con
> republicación y ACK, y al agotarse hace NACK sin requeue. El broker lo enruta por `payment.dlx`
> hacia `payment.screen.dlq`, donde el consumidor de logs lo registra."

---

## 7. Anexo B — Comandos de rescate

```powershell
# Ver el estado de RabbitMQ y los servicios
ssh -i .\tuki-key.pem ubuntu@184.192.252.196 "docker ps --filter name=rabbitmq; systemctl is-active tuki-products tuki-categories tuki-users tuki-orders tuki-rabbit-admin tuki-payments"

# Ver el panel en localhost
ssh -i .\tuki-key.pem -N -L 15672:127.0.0.1:15672 ubuntu@184.192.252.196

# Logs del flujo de pago y pedidos
ssh -i .\tuki-key.pem ubuntu@184.192.252.196 "sudo journalctl -u tuki-payments -u tuki-orders -u tuki-users -f"

# Confirmar que el broker tiene las 18 colas con consumidor
ssh -i .\tuki-key.pem ubuntu@184.192.252.196 "curl -s -u tuki:tuki http://127.0.0.1:15672/api/queues | jq '[.[] | select(.consumers > 0)] | length'"
```

---

## 8. Anexo C — Dónde está el código (solo si pregunta)

- Configuración de colas/exchanges: `backend/payments-service/src/main/java/com/ecommerce/payments/config/RabbitMQConfig.java`.
- Nombres centralizados: `application.yml` → sección `messaging` + `MessagingProperties`.
- Consumidores: `backend/*/src/main/java/com/ecommerce/*/messaging/*Consumer.java`.
- Reintentos y DLQ: `PaymentScreenConsumer.java` (`x-retry-count`, `basicAck`, `basicNack`).
- Admin REST: `rabbitmq-admin-service` (`/api/rabbit/**`, puerto 8085).
