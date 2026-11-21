# Guía definitiva para presentar y defender la EFT — Tuki-Tech

Documento único de preparación. Reúne: **guion de presentación**, **explicación del
código en lenguaje de cliente**, **para qué sirve cada tecnología** y **banco de
preguntas con mapa de rúbrica**.

Material complementario que ya existe en el repo (no lo reemplaza, lo ordena):

- [PRESENTACION_EFT.md](./PRESENTACION_EFT.md) — 12 diapositivas con notas.
- [GUIA_DEFENSA_EFT.md](./GUIA_DEFENSA_EFT.md) — guion técnico minuto a minuto.
- [ESTUDIO_EFT.md](./ESTUDIO_EFT.md) — mapa mental y repaso rápido.
- [PREGUNTAS_Y_TERMINOS_EFT.md](./PREGUNTAS_Y_TERMINOS_EFT.md) — glosario largo.

---

## Datos fijos que debes decir en voz alta

| Recurso | Valor |
|---|---|
| Repositorio | `https://github.com/Pastito-247/CLOUD-NATIVE-I` |
| Frontend publicado | `https://pastito-247.github.io/CLOUD-NATIVE-I/` |
| API Manager (AWS API Gateway) | `https://3cbwduklgb.execute-api.us-east-1.amazonaws.com` |
| Backend | EC2 `tuki-backend-ef`, EIP `184.195.234.120` |
| Base de datos | RDS PostgreSQL `tuki-postgres` (7 bases) |
| Usuarios públicos | Cognito User Pool `tuki-public-users` |

**Reglas de oro**

1. Nunca muestres `.env`, contraseñas ni el archivo `.pem`. Di solo los nombres de los recursos.
2. Si algo falla en vivo, **no arregles en pantalla**: muestra la evidencia ya capturada y sigue el guion.
3. Orden fijo: infraestructura → visitante → Kafka → Cognito → JWT → RabbitMQ → cierre.
4. Después de cada bloque di una **frase de cierre**: ayuda al docente a marcar el ítem.
5. El pago es **simulado** y la orden guest **sí genera evento Kafka**. No lo olvides.

---

## Idea rectora (repítela 3 veces en la defensa)

> **Tuki-Tech es una tienda de tecnología. RabbitMQ manda avisos para que alguien
> los ejecute; Kafka escribe en un cuaderno lo que ya pasó, para que reportes y
> auditoría lo lean después. El portero (API Gateway) decide quién entra.**

- Entidad principal: **Orden / Pedido** (`Order.java:11`).
- Entidades secundarias: **Productos** (`Product.java:11`) y **Categorías** (`Category.java:8`).
- Estados del pedido: `PENDING → CONFIRMED → SHIPPED → DELIVERED` (o `CANCELLED`) (`Order.java:13-17`).
- Roles: `admin`, `staff` (operador) y `customer` (cliente).

### Mapa de la solución (diapositiva 1)

```text
  Visitante sin cuenta ──/api/public/**──┐
  Cliente Cognito ──────/api/customer/**─┼──► API Gateway (AWS) ──► EC2 (9 servicios)
  Admin/Operador Entra ─/api/orders, ...─┘         │                      │
                                          2 authorizers          RDS PostgreSQL
                                                                 ├─ RabbitMQ: avisos + DLQ
                                                                 └─ Kafka: hechos + DLT
```

---

# PARTE 1 — Guion de presentación (10 minutos)

Ejecuta el stack antes de empezar:

```bash
docker compose -f docker-compose.full.yml up -d --build
docker compose -f docker-compose.full.yml ps
```

Pestañas abiertas: repo en GitHub, página publicada, consola AWS (API Gateway y
Cognito), Kafka UI (`localhost:8080`), RabbitMQ UI (`localhost:15672`) y terminal.

| Tiempo | Bloque | Ítems de la rúbrica que cubre |
|---|---|---|
| 0:00–0:45 | Repo, CI y mapa de la solución | Encargo: backend/frontend; defensa 1, 7 |
| 0:45–1:30 | Infraestructura y API Manager | Defensa 1, 2, 8, 11, 12 |
| 1:30–2:50 | Catálogo y compra sin token | Defensa 3, 14 |
| 2:50–3:50 | Kafka: evento, offset/lag, reportes y auditoría | Defensa 11, 12; EP6 todo |
| 3:50–4:50 | Cognito: registro, confirmación, login | Defensa 13 |
| 4:50–5:50 | JWT, roles y bloqueo de admin | Defensa 4, 5, 6, 14 |
| 5:50–6:40 | API Gateway: rutas y validación | Defensa 1, 2, 4 |
| 6:40–7:50 | RabbitMQ: clúster, colas, DLQ, exchanges | Defensa 8, 9, 10 |
| 7:50–8:40 | Provocar un fallo → DLQ y DLT | Defensa 9; resiliencia |
| 8:40–9:20 | Pruebas, CI y paquete de entrega | Encargo: calidad |
| 9:20–10:00 | Cierre y links | — |

## 0:00–0:45 | Repo, CI y mapa

**Pantalla:** GitHub → `Code`, luego `Actions`.

**Acciones:** mostrar carpetas `backend/`, `frontend/`, `scripts/`, los
`docker-compose*.yml` y los workflows `Backend CI` y `Deploy Frontend to GitHub
Pages` en verde.

**Di:**

> "El repositorio es la fuente de verdad. Al hacer push a `main`, GitHub Actions
> corre los tests de los nueve microservicios y publica el frontend en Pages. Pages
> no es un servidor: entrega archivos estáticos y Angular llama al API Gateway.
> El caso es un e-commerce: la entidad principal es la orden y la secundaria el
> catálogo de productos y categorías."

**Cierre:** *"Repo → CI → Pages → Gateway → EC2 → RDS: ese es el camino de cada petición."*

## 0:45–1:30 | Infraestructura y API Manager

**Pantalla:** AWS → API Gateway → `tuki-api` → Routes; terminal con `docker compose ps`.

**Acciones:** mostrar el stage `$default`, los dos authorizers, las rutas públicas y
protegidas; en terminal, 2 nodos RabbitMQ, 3 Zookeeper, 3 brokers y Kafka UI.

**Di:**

> "Todo pasa por un solo API Manager: enruta, aplica CORS, limita peticiones y valida
> JWT. Las rutas públicas no piden token; `/api/customer/**` pide un JWT de Cognito;
> el resto pide un JWT del tenant interno. Localmente levanto el stack completo con
> Docker Compose; en AWS el backend corre en EC2 y los datos en RDS."

**Cierre:** *"Una sola puerta de entrada, tres niveles de acceso."*

**Plan B:** si AWS no carga, usar la captura de `get-routes` y el output de `docker compose ps`.

## 1:30–2:50 | Catálogo y compra sin token

**Pantalla:** página publicada en ventana privada (sin sesión).

**Acciones:**

1. Home: catálogo y categorías desde `/api/public/products` y `/api/public/categories`.
2. Agregar producto → `/checkout-invitado` → completar nombre, email y dirección → confirmar.
3. Copiar el `trackingCode` (`TUKI-XXXXXXXX`) e ir a `/seguir-pedido?code=...`.

**Di:**

> "Un visitante sin cuenta ve precios y stock, crea su orden como invitado y recibe
> un código de seguimiento. Los precios se recalculan en el servidor con los de la
> base de datos: el navegador no puede manipularlos. La consulta devuelve solo esa
> orden por su código, así que no se exponen datos de otros clientes."

**Cierre:** *"Público, pero acotado: catálogo, mi pedido y mi confirmación. Nada más."*

**Evidencia en código:** `PublicOrderController.java:28-44` y `GuestOrderService.java:49-93`.

## 2:50–3:50 | Kafka: evento, offset, lag, reportes y auditoría

**Pantalla:** Kafka UI → Topics → `tuki.order.events`; después `/admin/reports` y `/admin/audit`.

**Acciones:**

1. Mostrar `tuki.order.events` y `tuki.payment.events`: **3 particiones, RF 3** local
   (`min.insync.replicas=2`), y los dos `*.DLT`.
2. Buscar el evento `ORDER_CREATED` del pedido guest recién creado.
3. Mostrar en Consumers los grupos `tuki-report` y `tuki-audit` con lag 0.
4. Abrir `/admin/reports` (KPI actualizado) y `/admin/audit` (actor `guest`).

**Di:**

> "La creación de la orden publica un hecho en el tópico. Reportes y auditoría son
> consumidores en grupos distintos: los dos leen el mismo hecho sin competir y cada
> grupo lleva sus propios offsets. La diferencia entre offset actual y final es el
> lag; en 0 significa que están al día. Si un mensaje defectuoso agota los reintentos,
> se publica en el DLT con sus metadatos y el flujo principal no se bloquea."

**Cierre:** *"El pedido guest también genera evento: sin tocar la base transaccional, reportes y auditoría se actualizan."*

**Evidencia en código:** `OrderEventPublisher.java:28-43`, `ReportEventConsumer.java:67-113`, `AuditEventConsumer.java:21`.

## 3:50–4:50 | Federación pública con Cognito

**Pantalla:** `/publico` en modo registro.

**Acciones:** registrar un email nuevo → confirmar el código → login → mostrar en
DevTools → Application → Local Storage la sesión `tuki_cognito_session` → abrir
`/mis-compras` → probar "olvidé mi contraseña" y logout.

**Di:**

> "El usuario público se registra solo en Cognito, confirma su correo y obtiene un
> JWT de cliente. El frontend guarda la sesión y envía el **ID token** como Bearer
> solo a `/api/customer/orders`. El authorizer verifica firma, issuer y audience, y
> el backend valida otra vez y fuerza el rol `customer`. Cognito jamás emite rol de
> administrador."

**Cierre:** *"Registro, confirmación, login, refresh y logout: todo sin intervención de un admin."*

**Nota de honestidad:** si muestras la consola AWS, di el nombre del pool y del
authorizer `tuki-cognito`; no expongas el client secret ni contraseñas.

## 4:50–5:50 | JWT, roles y bloqueo de funciones admin

**Pantalla:** terminal con `curl` y consola EC2/RDS.

```powershell
$GW = "https://3cbwduklgb.execute-api.us-east-1.amazonaws.com"
curl -i "$GW/api/orders"                                   # 401 sin token
curl -i -H "Authorization: Bearer invalido" "$GW/api/orders"  # 401
curl -i -H "Authorization: Bearer $COGNITO_ID_TOKEN" "$GW/api/rabbit/queues"  # 401
curl -i -H "Authorization: Bearer $ENTRA_ADMIN_TOKEN" "$GW/api/orders"       # 200
```

**Di:**

> "La validación ocurre dos veces: el gateway rechaza tokens inválidos antes de
> enrutar, y cada microservicio verifica issuer, audience y roles. Un cliente
> Cognito, aunque tenga token válido, se mapea siempre a `ROLE_customer`: por eso
> no puede administrar colas ni tópicos. Aquí ves EC2 activa, RDS disponible y el
> frontend en Pages, integrados por el gateway."

**Cierre:** *"Token válido no es lo mismo que permiso: el rol viaja en el JWT y se exige en cada endpoint."*

**Evidencia en código:** `SecurityConfig.java:59` (público permitido), `:72-103`
(resolutor por issuer), `:160-176` (Cognito → `ROLE_customer`).

## 5:50–6:40 | API Gateway: rutas y validación

**Pantalla:** consola AWS → API Gateway → `tuki-api` → Routes y Authorizers.

- Públicas sin authorizer: `/api/public/products`, `/api/public/categories`,
  `/api/public/orders`, `/api/public/users`.
- Cliente con `tuki-cognito`: `/api/customer/orders`.
- Protegidas con `tuki-jwt` (Entra): `/api/products`, `/api/categories`,
  `/api/users`, `/api/orders`, `/api/payments`, `/api/receipts`, `/api/rabbit`,
  `/api/kafka`, `/api/reports`, `/api/audit`.

**Di:**

> "Dos authorizers conviven porque conviven dos proveedores de identidad. Cada ruta
> declara cuál debe validar su JWT. El stage tiene CORS, throttling y auto-deploy.
> Si el gateway deja pasar una petición, el microservicio vuelve a validar: defensa
> en profundidad."

**Cierre:** *"La misma puerta distingue visitante, cliente y administrador."*

## 6:40–7:50 | RabbitMQ: clúster, colas, DLQ y exchanges

**Pantalla:** RabbitMQ UI → Overview → Nodes, Queues y Exchanges; luego `/admin/rabbitmq`.

**Acciones:**

1. Mostrar los **2 nodos en clúster** (`docker exec rabbitmq1 rabbitmqctl cluster_status`).
2. Mostrar al menos **3 colas funcionales con su DLQ**, por ejemplo
   `order.created.queue` / `order.created.dlq`, `payment.notification.queue` / su DLQ y
   `receipt.email.queue` / su DLQ (los nombres exactos están en cada `application.yml`,
   por ejemplo `backend/orders-service/src/main/resources/application.yml:91-107`).
3. Mostrar exchanges **direct** y **topic** (el panel demo crea `tuki.commands` y
   `tuki.catalog.events`).
4. En `/admin/rabbitmq` crear una cola de prueba y eliminarla.

**Di:**

> "RabbitMQ se usa para comandos: notificar, descontar stock, sincronizar catálogo.
> El exchange direct enruta por clave exacta y el topic por patrón. Cada cola tiene
> una DLQ; el consumidor reintenta hasta 3 veces con una cabecera de conteo y al
> agotar los intentos publica en la DLQ y deja log. La respuesta HTTP al usuario
> nunca espera a estos reintentos."

**Cierre:** *"Avisos desacoplados: si el que avisa falla, el mensaje no se pierde."*

## 7:50–8:40 | Provocar un fallo → DLQ y DLT

**Acciones:** publicar un comando de prueba con fallo desde `/api/rabbit/demo/publish`
o con un email inválido en el checkout; mostrar los logs `[EFT-RETRY]` y `[EFT-DLQ]`,
el mensaje en la DLQ y, para Kafka, un evento mal formado en `*.DLT`.

**Di:**

> "Forzamos un error: el consumidor reintenta, agota los intentos y el mensaje
> original queda en la cola/tópico de errores con sus metadatos. Está registrado y
> se puede reprocesar. Ningún fallo de mensajería tumba la respuesta al usuario."

**Cierre:** *"DLQ en RabbitMQ, DLT en Kafka: mismo principio, dos Tecnologías distintas."*

## 8:40–9:20 | Pruebas, CI y entrega

**Di:**

> "Los nueve módulos backend compilan y pasan sus tests Maven; el frontend compila
> sin errores. Cada servicio tiene Dockerfile multi-stage, el repo incluye CI y el
> paquete `TUKI-TECH-EFT.zip` contiene solo código y documentación, sin secretos ni
> artefactos de build."

## 9:20–10:00 | Cierre

> "Para cerrar: frontend y backend completos, dos identidades conviviendo, API
> Gateway con rutas públicas y protegidas, RDS en la nube, RabbitMQ con DLQ y
> Kafka con particiones, réplicas, offsets y dead-letter. El flujo invitado genera
> evento y trazabilidad sin exponer datos sensibles. El código está en GitHub y las
> instrucciones de levantamiento en el README. Gracias."

---

# PARTE 2 — Explicación del código para un cliente que no sabe de informática

## La regla de los 3 pasos

Cada vez que el profesor señale código, responde siempre en este orden:

1. **¿Qué ve el usuario?** (lenguaje de tienda)
2. **¿Qué hace el sistema por detrás?** (sin palabras técnicas)
3. **¿Para qué sirve eso en el negocio?** (dinero, confianza, rapidez, seguridad)

Ejemplo: *"El cliente ve el catálogo. Por detrás, la tienda le pregunta a la bodega
qué productos hay y a cuánto están. Eso sirve para que nadie venda algo que no existe."*

## La analogía central: Tuki-Tech es una tienda

| Parte de la tienda | Qué es en el sistema |
|---|---|
| **Vitrina** | Frontend Angular: lo que ve el visitante |
| **Portero** | API Gateway: revisa quién entra y por qué puerta |
| **Carné** | JWT: dice quién eres, tu rol y cuándo vence |
| **Dependiente** | Microservicios: cada uno hace una tarea (pedidos, productos, pagos) |
| **Bodega** | RDS PostgreSQL: donde quedan los pedidos y los productos |
| **Repartidor con buzón** | RabbitMQ: lleva avisos ("avisa al cliente", "descuenta stock") |
| **Bandeja de devueltos** | DLQ de RabbitMQ: avisos que no se pudieron entregar |
| **Libro de actas** | Kafka: cuaderno donde se escribe lo que **ya pasó** |
| **Contador y fiscal** | Report y Audit: leen el libro de actas para KPIs y trazabilidad |
| **Botón de encendido** | Docker Compose: enciende toda la tienda a la vez |

## Traductor rápido: palabra del código → lenguaje del cliente

| Código | Traducción |
|---|---|
| `@RestController` / endpoint | "la ventanilla donde se hace el pedido" |
| `@Transactional` | "o se hace todo, o no se hace nada" |
| DTO | "el formulario con los campos exactos que se aceptan" |
| JWT / `Bearer` | "un carné con fecha de vencimiento" |
| claim (`sub`, `roles`, `aud`) | "un dato escrito en el carné: correo, rol, para quién es" |
| `@PreAuthorize("hasRole('admin')")` | "esta ventanilla es solo para el jefe" |
| Exchange / Queue (RabbitMQ) | "el buzón de avisos / la bandeja donde caen" |
| Routing key / Binding | "la dirección escrita en el aviso / la regla que lo envía a tal bandeja" |
| DLQ / DLX | "la bandeja de los avisos que nadie pudo leer" |
| Topic (Kafka) | "el cuaderno de actas de la tienda" |
| Partición | "el cuaderno dividido en cuadernillos para escribir más rápido" |
| Key | "el número de pedido, para que siempre caiga en el mismo cuadernillo" |
| Réplica | "copias del cuaderno por si se pierde una" |
| Offset / Lag | "la página donde vas leído / cuántas páginas te faltan" |
| Consumer group | "equipos de lectores: cada equipo lee el cuaderno completo" |
| Retention | "cuánto tiempo se guarda el cuaderno antes de borrarlo" |
| Docker Compose | "el botón que enciende todos los servicios a la vez" |
| CI/CD | "el robot que revisa y publica el código cuando lo subes" |

## Escenario 1 — El visitante mira el catálogo

- **Cliente:** "Entro a la tienda y veo productos con precio y stock."
- **Por detrás:** "La vitrina le pregunta a la bodega qué hay disponible. Esa pregunta
  pasa por el portero, que la deja pasar porque es información pública."
- **Código:** `ProductController.java` y `PublicProductController.java` en
  `backend/products-service/src/main/java/com/ecommerce/products/controller/`.
- **Para el negocio:** "El visitante puede comprar sin crear cuenta; eso reduce la fricción."

## Escenario 2 — El visitante compra sin cuenta (el más importante)

- **Cliente:** "Escojo un teclado, pongo mi correo y dirección y le doy Confirmar.
  Me dan un código tipo `TUKI-AB12CD34`."
- **Por detrás:** "La tienda **vuelve a consultar el precio real en la bodega**, suma
  los productos, guarda el pedido con su código y avisa a dos equipos: al de avisos
  y al de estadísticas. El navegador solo envió el **producto y la cantidad**, nunca
  el precio: así nadie puede escribir 'cuesta 1 peso'."
- **Código real, explicado:**

| Línea | Qué hace en cristiano |
|---|---|
| `PublicOrderController.java:28-36` | "Esta es la ventanilla pública: recibe el formulario del visitante y devuelve la confirmación." |
| `GuestOrderService.java:49` | "Todo esto se hace como una sola operación: si algo falla, no queda un pedido a medias." |
| `GuestOrderService.java:58` | "Genera el código de seguimiento `TUKI-` con 8 caracteres." |
| `GuestOrderService.java:64-76` | "Por cada producto, consulta la bodega, toma el precio real y suma el total." |
| `GuestOrderService.java:81` | "Avisa a los demás equipos: publica el comando y el hecho." |
| `PublicOrderController.java:38-44` | "Otra ventanilla: con el código de seguimiento devuelve solo ese pedido." |

- **Frase para decir en voz alta:**

> "El total lo calcula el servidor, no el navegador. El código `TUKI` es como el
> número de boleta: con él el cliente consulta su pedido, sin ver los pedidos de
> nadie más."

## Escenario 3 — El portero revisa el carné

- **Cliente:** "Hay dos tipos de entrada: el equipo interno entra con su carné de
  la empresa; el cliente registrado entra con su carné de la tienda."
- **Por detrás:** "El portero mira de dónde viene el carné. Si es de la empresa lo
  manda con los servicios de administración; si es de la tienda lo manda solo a
  'mis compras'. Si no trae carné, no pasa."
- **Código:** `SecurityConfig.java:59` permite `/api/public/**`; `:72-103` elige el
  validador según el `iss` del token; `:160-176` convierte cualquier token de Cognito
  en `ROLE_customer`, sin importar los grupos que traiga.
- **Para el negocio:** "Un cliente nunca puede darse orden de administrador."

## Escenario 4 — El repartidor avisa sin hacer esperar al cliente

- **Cliente:** "Después de confirmar, la página responde al instante; el aviso por
  correo y el descuento de stock llegan después."
- **Por detrás:** "La tienda deja el aviso en un buzón (RabbitMQ) y sigue atendiendo.
  Otro equipo toma el aviso del buzón cuando puede. Si no puede leerlo, lo deja en
  la bandeja de devueltos y queda registrado."
- **Código:** `RabbitMQConfig.java` en cada servicio y los `messaging/` (por ejemplo
  `backend/orders-service/src/main/java/com/ecommerce/orders/messaging/`).
- **Para el negocio:** "El cliente no espera; y si falla el correo, el pedido no se pierde."

## Escenario 5 — El libro de actas (reportes y auditoría)

- **Cliente:** "Cada cambio importante queda anotado: cuándo se creó el pedido, cuándo
  se pagó, quién lo hizo. Con eso el gerente ve las ventas y el auditor reconstruye
  la historia."
- **Por detrás:** "En vez de que el contador revise la bodega cada minuto, la tienda
  escribe una nota en el cuaderno (Kafka). El contador y el fiscal leen ese cuaderno
  por separado, cada uno a su ritmo."
- **Código:**

| Línea | Qué hace en cristiano |
|---|---|
| `OrderEventPublisher.java:30-43` | "Escribe la nota: número de nota, tipo 'pedido creado', datos del pedido y hora." |
| `OrderEventPublisher.java:43` | "Usa el número de pedido como clave para que las notas del mismo pedido queden juntas y en orden." |
| `ReportEventConsumer.java:67-71` | "El contador está suscrito al cuaderno, en su propio equipo de lectores." |
| `ReportEventConsumer.java:88-108` | "Según el tipo de nota, suma una venta, un pedido invitado o un pago aprobado." |
| `AuditEventConsumer.java:21` | "El fiscal guarda quién, qué, cuándo y desde dónde." |

- **Para el negocio:** "Reportes en tiempo real y trazabilidad sin recargar la base de datos de ventas."

## Escenario 6 — Si algo sale mal

- **Cliente:** "A veces un aviso está mal escrito o un correo no existe."
- **Por detrás:** "El sistema lo intenta unas veces; si sigue fallando, guarda el
  aviso original en una bandeja separada y deja una nota en el registro. Nada se
  borra y nada se cuelga."
- **Código:** `KafkaConfig.java` de report/audit con `DeadLetterPublishingRecoverer`
  y `FixedBackOff(2000L, 3L)`; en RabbitMQ la cabecera `x-retry-count` definida en
  `backend/orders-service/src/main/java/com/ecommerce/orders/config/MessagingHeaders.java:10`.

## Frases prohibidas y frases permitidas

| No digas | Di mejor |
|---|---|
| "El controller inyecta el service y serializa el DTO" | "La ventanilla recibe el formulario y se lo pasa al encargado" |
| "El productor hace `kafkaTemplate.send` con la clave" | "La tienda escribe una nota en el cuaderno, agrupada por número de pedido" |
| "El `authenticationManagerResolver` selecciona por issuer" | "El portero mira de dónde viene el carné y elige la lista correcta" |
| "DDL automático con `ddl-auto: update`" | "La bodega se acomoda sola cuando cambiamos lo que guardamos" |
| "Hice un microservicio para..." | "Puse a una persona encargada solo de..." |

---

# PARTE 3 — Para qué sirve cada tecnología

## Tabla resumen

| Tecnología | Para qué sirve **en este proyecto** | Cómo lo demuestras |
|---|---|---|
| **AWS** | Nube donde vive todo: puerta de entrada, servidor, base de datos y directorio de usuarios | Consola AWS con los recursos creados |
| **API Gateway (API Manager)** | Única entrada; rutas públicas vs protegidas, CORS, throttling y validación JWT | Routes + Authorizers + `curl` 200/401 |
| **EC2** | Servidor donde corren los 9 microservicios con Docker/systemd | Instancia `tuki-backend-ef` activa |
| **RDS PostgreSQL** | Bodega: guarda pedidos, productos, pagos, reportes y auditoría (7 bases) | Endpoint `tuki-postgres` y datos tras reiniciar |
| **AWS Cognito** | Registro, confirmación, login, refresh, forgot-password de clientes públicos | Flujo `/publico` + authorizer `tuki-cognito` |
| **Microsoft Entra ID (Azure AD)** | Directorio interno de Admin/Operador | Tenant con usuarios y roles `admin`/`staff` |
| **MSAL** | Librería que hace el login con Entra desde Angular y guarda el token |Redirect en `/login` + token en la app |
| **JWT / OAuth2 / OIDC** | Carné firmado con rol y vencimiento; OAuth2/OIDC son las reglas para obtenerlo | `Authorization: Bearer ...`, 401 sin token |
| **Kafka** | Cuaderno de eventos: hechos ya ocurridos para reportes y auditoría | 2 tópicos, 3 particiones, eventos visibles |
| **Zookeeper** | Coordinador de los brokers Kafka (quién manda, metadata, 3 nodos) | `docker compose ps` con 3 ZK |
| **Brokers Kafka** | Servidores que guardan y entregan los eventos (3 en local) | Kafka UI → Brokers |
| **Kafka UI** | Panel para ver tópicos, particiones, mensajes, consumidores y lag | Puerto 8080 |
| **RabbitMQ** | Buzón de comandos asíncronos: avisos que alguien debe ejecutar | 2 nodos, colas y exchanges en el panel |
| **DLQ / DLX** | Bandeja de avisos fallidos de RabbitMQ + log | Logs `[EFT-RETRY]`, `[EFT-DLQ]` |
| **DLT** | Versión Kafka de la bandeja de fallos: tópico `*.DLT` con metadata | `tuki.order.events.DLT` en Kafka UI |
| **Angular** | Vitrina: SPA con rutas públicas, cliente y admin | Página en GitHub Pages |
| **Spring Boot** | Encargados (microservicios) que atienden las ventanillas y hacen el trabajo | 9 módulos, puertos 8081–8089 |
| **Spring Security** | El que revisa el carné dentro de cada encargado | `SecurityConfig.java` por servicio |
| **JPA / Hibernate** | Traductor entre el código y la base de datos (entidades y repositorios) | Entidades `Order`, `Product`, etc. |
| **H2 / PostgreSQL** | H2 para probar en la laptop; PostgreSQL en la nube | Perfil `prod` en `application-prod.yml` |
| **Docker Compose** | Botón que enciende toda la tienda local (BD, RabbitMQ, Kafka, UI) | 5 archivos `docker-compose*.yml` |
| **GitHub Actions** | Robot que compila, prueba y publica al hacer push | Workflows en verde |
| **GitHub Pages** | Lugar donde se publica la vitrina estática | URL `/CLOUD-NATIVE-I/` con 200 |
| **DTO** | Formulario exacto que se acepta; evita que lleguen campos raros | `GuestOrderRequest`, `OrderEvent`, `PaymentEvent` |
| **systemd / EIP** | systemd mantiene los JARs arriba; EIP da IP fija a la EC2 | `systemctl` en la instancia |

## Fichas para responder en 30 segundos

### AWS

> "AWS es el proveedor de nube. Aquí nos da el API Gateway, la EC2, RDS y Cognito.
> Sin AWS tendríamos que mantener servidores propios; con él los recursos están
> creados, monitorizados y accesibles por HTTPS. Lo demuestro en la consola."

### API Gateway (API Manager)

> "Es el portero y el repartidor de rutas. Recibe todo lo que viene del navegador,
> permite el origen de Pages (CORS), limita peticiones, revisa el JWT con dos
> authorizers y manda cada llamada al microservicio correcto. Sin él, el frontend
> tendría que conocer nueve servidores distintos y la seguridad estaría repetida."

### EC2

> "Es la máquina en la nube donde corren los nueve servicios. Los mantengo vivos con
> systemd y tiene una IP fija (EIP) para que el gateway sepa dónde enviar. La muestro
> activa y con los servicios corriendo."

### RDS PostgreSQL

> "Es la bodega. Cada dominio tiene su base: productos, categorías, usuarios,
> pedidos, pagos, auditoría y reportes. En la laptop uso H2, pero en producción el
> perfil `prod` apunta a RDS. Reinicio un servicio y los pedidos siguen ahí."

### Cognito

> "Es el directorio de los clientes públicos. El visitante se registra solo,
> confirma su correo con un código y hace login. Cognito devuelve tokens; el
> frontend usa el **ID token** porque incluye el `aud` que exige el authorizer.
> Siempre se mapea a rol `customer`: no puede ser admin."

### Entra ID + MSAL

> "Entra es el directorio de los empleados; MSAL es la librería que en Angular hace
> el login con Authorization Code + PKCE. De ahí sale el JWT con los roles
> `admin`/`staff` que autorizan la administración."

### JWT, OAuth2 y OIDC

> "OAuth2/OIDC son las reglas del login; el JWT es el carné resultante: va firmado,
> trae quién eres, tu rol y cuándo vence. Se envía como `Bearer`. Se valida en el
> gateway y otra vez en el backend: firma, issuer, audience y rol."

### Kafka (+ Zookeeper, brokers, UI)

> "Kafka es el cuaderno de actas: eventos inmutables que varios lectores consultan.
> Zookeeper coordina los 3 brokers; los tópicos tienen 3 particiones para paralelismo
> y 3 réplicas para tolerancia a fallos. Kafka UI permite ver particiones, mensajes,
> offsets y lag. Si un consumidor falla, el evento va al DLT."

### RabbitMQ (+ DLQ)

> "RabbitMQ es el buzón de avisos: comandos que alguien debe ejecutar una vez, como
> notificar o descontar stock. Tiene 2 nodos en clúster, exchanges direct y topic,
> y cada cola su DLQ. Reintenta 3 veces y al fallar guarda el mensaje y lo loguea.
> La respuesta HTTP no espera a nada de esto."

**¿Por qué los dos?** RabbitMQ = comandos con confirmación; Kafka = hechos para
múltiples lectores. No son reemplazables entre sí.

### Docker Compose

> "Es el botón de encendido: con un comando levanta PostgreSQL, los 2 nodos
> RabbitMQ, 3 Zookeeper, 3 brokers, Kafka UI y los 9 microservicios. El docente puede
> reproducir la demo con `docker compose -f docker-compose.full.yml up -d`."

### Angular / Spring Boot

> "Angular es la vitrina que corre en el navegador; Spring Boot son los nueve
> encargados del backend. Cada encargado tiene una tarea y una base. Angular no
> habla directo con ellos: habla con el API Gateway."

### GitHub Actions y Pages

> "Actions es el robot que al hacer push compila y prueba el backend y genera el
> frontend. Pages publica ese frontend estático. Por eso el repo es la fuente de
> verdad y la página se actualiza sola."

---

# PARTE 4 — Preparación para preguntas

## Regla para responder cualquier pregunta

**Respuesta corta (1–2 frases) → evidencia visible → limitación conocida.**

Si no sabes algo, di: *"Eso lo validé con esta evidencia y no lo implementé en esta
entrega"*. No inventes.

## Mapa de la rúbrica → dónde está la evidencia

### Encargo — 20% de la EFT (código)

| Indicador | Peso | Evidencia |
|---|---:|---|
| Backend completo y sin errores | 11% | 9 módulos Maven, 47 tests, CRUD y DTOs |
| Frontend completo y sin errores | 9% | Angular 17, rutas `/`, `/publico`, `/checkout-invitado`, `/admin/**` |
| Frontend ↔ IDaaS (login interno) | 6% | MSAL + `MsalGuard` + interceptor Bearer (`app.module.ts:46`) |
| Backend valida JWT (IDaaS) | 6% | `SecurityConfig` multi-issuer en orders/users/payments |
| BD cloud | 6% | `application-prod.yml`, entidades y repositorios, RDS |
| Clúster RabbitMQ por Compose | 8% | `docker-compose.yml`, `docker-compose.ec2.yml`, 2 nodos |
| Microservicios producen/consumen colas | 12% | `messaging/` en 5 servicios |
| DLQ con log | 6% | `*.dlq`, logs `[EFT-DLQ]`, panel demo |
| Admin RabbitMQ | 6% | `/api/rabbit/**` + `/admin/rabbitmq` |
| Kafka 3 ZK + 3 brokers por Compose | 15% | `docker-compose.kafka.yml`, `docker-compose.full.yml` |
| Microservicios producen/consumen tópicos | 10% | orders/payments → report/audit |
| Admin Kafka | 5% | `/api/kafka/**` + `/admin/kafka` |

### Defensa — 80% de la EFT (demo en vivo, ítems 1–14)

| # | Qué mostrar | Peso | Minuto del guion |
|---|---|---:|---|
| 1 | Instancia del API Manager en la nube | 7% | 0:45 |
| 2 | API Manager configurado hacia todos los endpoints | 9% | 0:45 / 5:50 |
| 3 | Frontend consumiendo vía API Manager | 9% | 1:30 |
| 4 | API Manager valida JWT (rechaza inválido, acepta válido) | 10% | 4:50 / 5:50 |
| 5 | Tenant IDaaS creado con usuarios | 6% | 4:50 |
| 6 | Login OAuth 2.0 / OIDC con JWT válido | 11% | 4:50 |
| 7 | Backend + frontend desplegados e integrados | 9% | 0:00 / 4:50 |
| 8 | 2 nodos RabbitMQ en clúster | 2% + 5% | 6:40 |
| 9 | 3 colas con su DLQ | 6% + 6% | 6:40 / 7:50 |
| 10 | Exchanges direct + topic | 5% | 6:40 |
| 11 | 2 tópicos Kafka con 3 particiones y 3 réplicas | 9% | 2:50 |
| 12 | Kafka UI administrando brokers y tópicos | 6% | 2:50 |
| 13 | Federación pública Cognito: registro + confirmación + login | a confirmar en AVA | 3:50 |
| 14 | Transacción pública sin token + bloqueo de protegidas | a confirmar en AVA | 1:30 / 4:50 |

### Presentación EP6 — 18% (si te lo preguntan aparte)

| Indicador | Peso | Respuesta clave |
|---|---:|---|
| Arquitectura Kafka: 3 ZK + 3 brokers y tolerancia a fallos | 10% | ZK coordina; brokers guardan; con RF 3 aguanta fallo de broker |
| Justificación de 2 tópicos, 3 particiones y 3 réplicas | 10% | Particiones = paralelismo y orden por clave; réplicas = resiliencia (ISR 2) |
| Kafka UI | 5% | Brokers, tópicos, particiones, mensajes, consumidores y lag |
| Producción y consumo de eventos | 12% | orders/payments producen; report/audit consumen en grupos distintos |
| Diseño de los eventos | 6% | DTO con eventId, tipo, entidad, actor, hora, origen y metadata |
| Reacción en tiempo real | 12% | El consumidor actualiza KPIs/auditoría al llegar el evento |
| Administración Kafka (particiones, réplicas, retention, grupos) | 15% | Configurado en `kafka-init.sh` y `/api/kafka/**` |
| Métricas: offsets, lag, distribución | 15% | Kafka UI → Consumers; lag 0 = al día; permite anticipar cuellos |
| Casos de uso y retención | 15% | Negocio 7 días, DLT 30 días, dos consumidores con necesidades distintas |

## Banco de preguntas

### Repositorio, CI y página

1. **¿Qué entregas?** El repo de GitHub, el ZIP `TUKI-TECH-EFT.zip` y el video individual como respaldo.
2. **¿Cómo se publica la página?** Push a `main` → workflow → `dist/` → GitHub Pages.
3. **¿Por qué no sale pantalla blanca?** El workflow reescribe 404 a `index.html` y el build incluye el polyfill de `global`.
4. **¿Hay secretos en el repo?** No. `.env` y `*.pem` están en `.gitignore`; los valores son Variables de GitHub.
5. **¿Qué hace `Backend CI`?** Corre los tests Maven de los 9 microservicios.

### Arquitectura y frontend

6. **¿Qué patrón usa el frontend?** SPA de Angular con Router, servicios HTTP, guards e interceptors.
7. **¿Cómo evitas manipular precios?** El checkout envía `productId` y cantidad; el servidor consulta la BD y calcula el total (`GuestOrderService.java:64-79`).
8. **¿Cómo es seguro el seguimiento guest?** Solo consulta por `trackingCode`; no hay listado de órdenes de terceros (`PublicOrderController.java:38-44`).
9. **¿Qué es CORS y dónde se aplica?** Regla del navegador; el gateway y el backend permiten el origen de Pages.

### API Gateway y JWT

10. **¿Qué es el API Manager aquí?** API Gateway HTTP API de AWS: enrutamiento, CORS, throttling y authorizers JWT.
11. **¿Qué rutas son públicas?** `/api/public/products`, `/categories`, `/orders`, `/users`.
12. **¿Cómo sabes que un JWT es válido?** Firma, `iss`, `aud` y vencimiento; lo revisan el authorizer y el microservicio.
13. **¿Por qué validas dos veces?** Defensa en profundidad: el gateway filtra; el backend no confía ni en la red interna.
14. **¿Qué es un claim?** Un dato dentro del JWT: `sub`, `roles`, `email`, `aud`.

### Identidad (la pregunta más probable)

15. **¿Por qué dos proveedores de identidad?** Porque son dos poblaciones distintas: empleados (Entra) y clientes públicos (Cognito). Cada ruta declara cuál valida.
16. **¿Puede un usuario Cognito ser admin?** No. El backend ignora `cognito:groups` y fuerza `ROLE_customer` (`SecurityConfig.java:166-175`).
17. **¿ID token o access token?** ID token: trae `aud` con el app client y es el que acepta el authorizer. El access token de Cognito aquí se rechazaría.
18. **¿Cómo renuevas la sesión?** Con el refresh token en `PublicAuthService`; el logout descarta la sesión local.
19. **¿Qué es PKCE?** Protección del flujo de código de autorización para SPAs; la usa MSAL.

### Transacción pública

20. **¿Qué puede hacer un visitante sin token?** Ver catálogo, crear su orden y consultar su confirmación por código.
21. **¿Por qué no es inseguro?** No expone listados, KPIs ni auditoría; los precios los calcula el servidor; la consulta es por un código imprevisible.
22. **¿La orden guest genera evento Kafka?** Sí: `publishOrderCreated(..., true, ...)` en `GuestOrderService.java:108-110`. Aparece en reportes como `guestOrders`.

### RabbitMQ

23. **¿Direct vs topic?** Direct enruta por routing key exacta; topic por patrón con `*` y `#`.
24. **¿Qué son DLQ y DLX?** Cola y exchange de mensajes muertos; guardan lo que no se pudo entregar.
25. **¿Cómo reintentas?** Cabecera `x-retry-count`, hasta 3 intentos y logs `[EFT-RETRY]` / `[EFT-DLQ]`.
26. **¿ACK manual?** El consumidor confirma solo después de procesar; si falla, el mensaje va a la DLQ.
27. **¿Por qué no solo Kafka?** RabbitMQ confirma comandos de una sola ejecución; Kafka es un log para varios lectores.

### Kafka

28. **¿Productor y consumidor?** Orders y payments producen; report y audit consumen.
29. **¿Tópico y partición?** Tópico = canal lógico; partición = trozo ordenado del log que permite paralelismo.
30. **¿Por qué la clave?** Para que un mismo pedido siempre caiga en la misma partición y conserve su orden (`OrderEventPublisher.java:43`).
31. **¿Offset y lag?** Offset = posición leída; lag = mensajes pendientes. Lag 0 = al día.
32. **¿Qué son RF 3 e ISR 2?** Tres copias del dato y al menos dos sincronizadas para aceptar escrituras.
33. **¿Qué pasa si dos consumidores están en el mismo grupo?** Se reparten las particiones; por eso report y audit usan grupos distintos.
34. **¿Retention?** 7 días en tópicos de negocio y 30 días en los DLT (`docker/kafka/kafka-init.sh`).
35. **¿Qué es el DLT?** Dead Letter Topic: guarda el evento que falló tras los reintentos, con metadata.
36. **¿Cómo reprocesas un DLT?** Se consume el DLT y se reenvía al tópico original una vez corregido.

### Nube y base de datos

37. **¿Dónde corre el backend?** EC2 `tuki-backend-ef`, servicios systemd, EIP fija.
38. **¿Qué base usas en producción?** RDS PostgreSQL; H2 solo en desarrollo.
39. **¿Cuántas bases?** 7, una por dominio.
40. **¿Qué es un seed idempotente?** Datos iniciales que se insertan solo si no existen; reiniciar no duplica.
41. **¿Cómo reproduzco la demo?** `docker compose -f docker-compose.full.yml up -d` y seguir el README.

### Preguntas de honestidad (contéstalas sin rodeos)

42. **¿El pago es real?** No. Es simulado, genera boleta PDF y publica el evento `PAYMENT_APPROVED`.
43. **¿RF 3 también en AWS?** No. En EC2 usamos un broker único (RF 1) porque MSK está denegado por IAM (`kafka:CreateCluster` en `explicitDeny`). El RF 3 se demuestra con Compose local.
44. **¿Usaste Testcontainers?** No; 47 tests unitarios de lógica y mensajería.
45. **¿Qué mejorarías con más tiempo?** MSK administrado con RF 3, Terraform e2e y pruebas de contrato/contenedores.

## Errores que hacen caer la nota (repásalos antes de grabar)

- [ ] La transacción pública expone datos de otros usuarios o modifica estados sensibles.
- [ ] Un usuario Cognito puede llamar funciones de Admin.
- [ ] Ruta pública en el frontend pero protegida en el gateway (o al revés).
- [ ] Kafka levanta pero nadie produce/consume: tópico vacío.
- [ ] La orden guest no genera evento Kafka.
- [ ] Romper el login Entra al agregar Cognito.
- [ ] Demo sin guión: no alcanza a mostrar federación, guest, RabbitMQ y Kafka en 10 minutos.
- [ ] Decir que Cognito da rol admin, confundir ID token con access token o usar el mismo consumer group para report y audit.
- [ ] Mostrar `.env`, contraseñas o el `.pem` en pantalla.

---

# PARTE 5 — Checklist y plan B

## 2 minutos antes de grabar

- [ ] `git push` hecho; commit visible en GitHub.
- [ ] Workflows `Backend CI` y `Deploy Frontend to GitHub Pages` en verde.
- [ ] Pestañas cargadas: repo, Pages, API Gateway, Cognito, Kafka UI, RabbitMQ UI.
- [ ] `curl` de Pages `200`, público `200`, privado sin token `401`.
- [ ] Un `trackingCode` de prueba anotado.
- [ ] Sin secretos en pantalla.
- [ ] ZIP regenerado tras el último cambio.

## Verificación rápida

```powershell
curl -s -o /dev/null -w '%{http_code}\n' https://pastito-247.github.io/CLOUD-NATIVE-I/                                  # 200
curl -s -o /dev/null -w '%{http_code}\n' https://3cbwduklgb.execute-api.us-east-1.amazonaws.com/api/public/products   # 200
curl -s -o /dev/null -w '%{http_code}\n' https://3cbwduklgb.execute-api.us-east-1.amazonaws.com/api/orders            # 401
```

## Plan B si falla AWS, Docker o la red

1. Dejar abiertas las pestañas ya cargadas de GitHub, Pages, Kafka UI y RabbitMQ UI.
2. Reproducir el video de respaldo o mostrar capturas.
3. Mostrar el commit final y el workflow en verde.
4. Si aparece `502/503`, revisar `systemctl is-active tuki-payments`.
5. Si Cognito no responde, mostrar el fragmento de `SecurityConfig.java:166-175` donde
   se fuerza `ROLE_customer`.

## Índice rápido de archivos para ir directo

| Necesito mostrar... | Archivo |
|---|---|
| Contrato de la API pública | `backend/orders-service/.../controller/PublicOrderController.java:19` |
| Cálculo de precios y evento guest | `backend/orders-service/.../service/GuestOrderService.java:49-110` |
| Validación de JWT y roles | `backend/orders-service/.../config/SecurityConfig.java:55-176` |
| Productor Kafka de órdenes | `backend/orders-service/.../messaging/kafka/OrderEventPublisher.java:28-74` |
| Consumidor de reportes | `backend/report-service/.../kafka/ReportEventConsumer.java:67-113` |
| Consumidor de auditoría | `backend/audit-service/.../kafka/AuditEventConsumer.java:21` |
| Configuración de tópicos | `docker/kafka/kafka-init.sh` |
| Clúster Kafka local | `docker-compose.kafka.yml` |
| Clúster RabbitMQ local | `docker-compose.yml` |
| Stack completo | `docker-compose.full.yml` |
| Rutas y authorizers | `scripts/aws/api-gateway-setup.sh:159-206` |
| Registro/login público | `frontend/src/app/auth/public-auth.service.ts:31-160` |
| Login interno | `frontend/src/app/app.module.ts:46-93` |
| Rutas del frontend | `frontend/src/app/app-routing.module.ts:20-74` |
