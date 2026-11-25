# Guion de presentación EFT — Tuki-Tech (10 minutos)

Material complementario: [diapositivas y notas](./PRESENTACION_EFT.md) y
[banco de preguntas y glosario](./PREGUNTAS_Y_TERMINOS_EFT.md).

Formato: defensa individual en video. Habla mirando la cámara y comparte
pantalla; ten abiertas 4 pestañas: frontend, Kafka UI, RabbitMQ UI y AWS
(API Gateway + Cognito). Si algo falla, usa la alternativa indicada: **no te
detengas a arreglar en vivo**, muestra la evidencia ya capturada.

Antes de grabar: `docker compose -f docker-compose.full.yml up -d`, esperar
`healthy`, frontend en `http://localhost:4200`, y tener un `trackingCode` de
prueba anotado.

---

## 0:00–0:35 | Apertura y problema

**Pantalla:** portada del frontend o diagrama simple en papel/diapositiva.

**Di:**
> "Hola, soy [nombre]. Presento Tuki-Tech, una solución distribuida de e-commerce.
> El problema que resolvemos es vender sin fricción a visitantes, permitir que el
> equipo interno administre el catálogo y las órdenes, y que cada cambio del
> negocio quede registrado para reportes y auditoría. Hoy mostraré frontend,
> backend, identidad, API Gateway, RabbitMQ y Kafka de punta a punta."

**Cierre del bloque:**
> "La idea rectora es: RabbitMQ manda comandos, Kafka registra hechos."

---

## 0:35–1:40 | Infraestructura y API Manager (ítems 1, 2, 8, 11, 12)

**Pantalla:** AWS → API Gateway → `tuki-api` → routes; luego terminal `docker compose ps`.

**Acciones:**
1. Mostrar la instancia `tuki-api`, stage `$default`, auto-deploy.
2. Filtrar rutas: `/api/public/**` sin autorizador, `/api/customer/orders` y
   `/api/customer/payments` con `tuki-cognito`, `/api/orders` y `/api/kafka`
   con `tuki-jwt`.
3. Mostrar authorizers: issuer de Entra y de Cognito.
4. Terminal: `docker compose -f docker-compose.full.yml ps` (2 RabbitMQ, 3 ZK,
   3 brokers, Kafka UI, servicios).

**Di:**
> "El API Manager es el único punto de entrada: enruta, aplica CORS y throttling
> y valida JWT. Las rutas públicas pasan sin token; las de cliente exigen un JWT
> de Cognito; las administrativas exigen un JWT del tenant interno y rol admin.
> Localmente el stack completo levanta 9 microservicios, 2 nodos RabbitMQ y un
> cluster Kafka de 3 brokers con su UI."

**Alternativa:** si AWS no carga, mostrar la captura de `get-routes` y el output
de `docker compose ps` guardado.

---

## 1:40–3:00 | Catálogo y compra pública (ítems 14 y 3)

**Pantalla:** frontend sin sesión (cierra sesión o usa ventana privada).

**Acciones:**
1. Abrir `/`: catálogo con precios y stock desde `/api/public/products`.
2. Agregar producto al carrito, abrir `/checkout-invitado`, completar datos.
3. Copiar `trackingCode` y abrir `/seguir-pedido?code=...`.

**Di:**
> "Un visitante sin cuenta ve el catálogo, crea su orden como invitado y recibe
> un código de seguimiento. Los precios se recalculan en el servidor: el
> navegador no puede manipularlos. La consulta solo devuelve esa orden por su
> código, así que no se exponen datos de otros clientes."

**Si falla el checkout:** usar el `trackingCode` de prueba y mostrar el registro
en la BD/endpoint público; decir "la transacción ya quedó persistida y su evento
está en Kafka, lo vemos ahora".

---

## 3:00–4:05 | Kafka, reportes y auditoría (ítems 11, 12)

**Pantalla:** Kafka UI → topics → `tuki.order.events`.

**Acciones:**
1. Mostrar 3 particiones, RF 3, `min.insync.replicas=2`, mensaje del pedido guest.
2. Mostrar `tuki.payment.events` y los dos `*.DLT`.
3. Abrir `/admin/reports` y `/admin/audit`: KPI y registro con actor `guest`.

**Di:**
> "La creación de la orden publica un evento de dominio. Reportes y auditoría son
> consumidores distintos, con grupos `tuki-report` y `tuki-audit`, por lo que
> ambos procesan el mismo hecho sin competir. Cada grupo lleva sus offsets; en la
> UI podemos ver el lag. Si un mensaje defectuoso agota los reintentos, se publica
> en el tópico dead-letter con su metadata y no bloquea al cliente."

---

## 4:05–5:00 | Federación pública con Cognito (ítem 13)

**Pantalla:** `/publico` en modo registro.

**Acciones:**
1. Registrar email nuevo → confirmar código → login.
2. Mostrar en DevTools → Application → Local Storage la sesión `tuki_cognito_session`.
3. Abrir `/mis-compras`: solo las órdenes de ese usuario.
4. Probar "olvidé mi contraseña" y el logout.

**Di:**
> "El usuario público se registra solo en el User Pool de Cognito, confirma su
> email por código y obtiene un JWT de cliente. Ese JWT se envía como Bearer a
> `/api/customer/orders`. El token se renueva con refresh token y se descarta en
> el logout. Cognito jamás emite rol de administrador: el backend y el gateway
> son la segunda barrera."

**Nota de honestidad:** si muestras el pool en AWS, menciona nombre
`tuki-public-users` y el authorizer `tuki-cognito`; no expongas secretos.

---

## 4:50–6:10 | Validación JWT, roles y backend en nube (ítems 4, 5, 6, 7)

**Pantalla:** terminal con curl y AWS EC2.

**Acciones:**
1. `curl -i .../api/orders` sin token → **401**.
2. Con token inválido → **401**; con JWT Entra válido → **200**.
3. Con JWT Cognito intentar `/api/kafka/topics` → bloqueo por rol.
4. Mostrar instancia EC2 `tuki-backend-ef`, servicios systemd activos y RDS
   `tuki-postgres` disponible; frontend en Pages con HTTP 200.

**Di:**
> "La validación ocurre dos veces: el API Gateway rechaza tokens inválidos
> antes de enrutar, y cada microservicio verifica issuer, audience y roles.
> Un cliente autenticado no puede administrar la plataforma. El backend corre
> en EC2 contra RDS PostgreSQL y el frontend está publicado en GitHub Pages,
> consumiendo todo a través del gateway."

---

## 6:10–7:40 | RabbitMQ: clúster, colas, DLQ y admin (ítems 8, 9, 10)

**Pantalla:** RabbitMQ UI `localhost:15672` → Overview → nodes; Queues; Exchanges.

**Acciones:**
1. Mostrar 2 nodos en clúster y el `cluster_status` por terminal.
2. Mostrar `tuki.commands` (direct), `tuki.catalog.events` (topic) y `tuki.dlx`.
3. Mostrar las 3 colas de notificación + su DLQ.
4. En `/admin/rabbitmq` crear una cola de prueba y luego eliminarla.
5. Provocar fallo con `fail@demo.cl` o desde el panel demo; mostrar log
   `[EFT-DLQ]` y el mensaje en la DLQ.

**Di:**
> "El clúster tiene dos nodos y la topología se declara desde el servicio
> administrador: colas, exchanges direct/topic, bindings y sus DLQ. Los
> consumidores reintentan con una cabecera de conteo; cuando agotan los
> intentos, el mensaje va a la DLQ y queda en logs. Nada se pierde y la
> respuesta HTTP al usuario nunca se bloquea."

**Alternativa:** si el entorno cloud no responde, usar `localhost:15672` y
decir "esta es la misma topología levantada con docker-compose".

---

## 7:40–8:40 | Administración Kafka y resiliencia (ítems 11, 12, 21)

**Pantalla:** `/admin/kafka` y Kafka UI.

**Acciones:**
1. Listar tópicos y configs; mostrar cómo crear/eliminar un tópico de prueba.
2. Mostrar grupos y offsets/lag.
3. Enviar un evento mal formado al DLT y mostrarlo en Kafka UI.

**Di:**
> "El microservicio administrador crea, modifica y elimina tópicos y
> configuraciones con `AdminClient`. La UI nos da supervisión de brokers,
> particiones, consumidores y lag. El dead-letter topic conserva el mensaje
> original y sus metadatos para reprocesarlo después."

---

## 8:40–9:25 | Pruebas y calidad

**Pantalla:** terminal con resultados.

**Di:**
> "Los 9 módulos backend compilan y pasan sus pruebas Maven; el frontend compila
> sin errores. Las pruebas cubren servicios, publicadores, consumidores y
> seguridad del administrador RabbitMQ, incluido el flujo de reintento a DLQ.
> Cada servicio tiene Dockerfile multi-stage y el repositorio incluye CI."

**Si preguntan por cobertura:** responder "47 tests unitarios sobre lógica de
negocio y mensajería; no usamos Testcontainers en esta entrega".

---

## 9:25–10:00 | Cierre

**Pantalla:** rostro en cámara.

**Di:**
> "Para cerrar: Tuki-Tech demuestra frontend y backend completos, dos identidades
> conviviendo, API Gateway con rutas públicas y protegidas, base de datos cloud,
> RabbitMQ con DLQ y Kafka con tópicos, particiones, réplicas y dead-letter.
> El flujo invitado genera evento y trazabilidad sin exponer datos sensibles.
> El código está en GitHub y las instrucciones de levantamiento en el README.
> Gracias."

---

## Plan B: evidencias que deben quedar grabadas por si falla la demo

- `200` en `/api/public/products` y `401` en `/api/orders`.
- `trackingCode` con su consulta pública `200`.
- Kafka UI: `tuki.order.events`, 3 particiones/RF 3, DLT (RF 1 en EC2).
- RabbitMQ UI: 2 nodos, colas `tuki.notify.*` y `*.dlq`, exchanges direct/topic.
- Cognito: registro + confirmación + login y sesión en Local Storage.
- AWS: API Gateway routes/authorizers, EC2 activa, RDS disponible, Pages 200.
- Logs `[EFT-RETRY]` y `[EFT-DLQ]`.

## Reglas durante la defensa

- Nunca compartas credenciales ni el `.pem`; muestra solo nombres de recursos.
- Si el video es individual, habla en primera persona y no menciones a tu pareja
  salvo cuando expliques que el encargo fue grupal.
- Orden fijo: infra → público → Kafka → Cognito → JWT → RabbitMQ → cierre.
- Usa frases de cierre después de cada bloque; ayudan al docente a marcar ítems.
