# Preguntas y términos clave para la defensa EFT

Regla para responder: **respuesta corta → evidencia visible → limitación conocida**.
Si no sabes algo, di “esa parte la validé con esta evidencia y no la implementé en
esta entrega”; no inventes.

---

## Cómo funciona la página (pregunta obligatoria)

**Respuesta lista:**

> "La página es una SPA de Angular servida por GitHub Pages. El workflow
> `Deploy Frontend to GitHub Pages` compila el proyecto con la URL del API Gateway
> inyectada desde las Variables del repositorio y publica `index.html` + JS/CSS.
> En el navegador, Angular Router elige la ruta; `environment.prod.ts` apunta al
> gateway; el gateway valida CORS y JWT; y los servicios en EC2 consultan RDS. Pages
> no ejecuta backend: solo entrega archivos estáticos."

**Si piden el recorrido de un clic:**

1. Usuario abre `pastito-247.github.io/CLOUD-NATIVE-I`.
2. Home pide `/api/public/products` y `/api/public/categories`.
3. Gateway permite el origen Pages (CORS) y enruta a EC2.
4. EC2 consulta RDS y devuelve JSON.
5. Angular pinta productos; el carrito es estado del navegador.
6. Checkout guest hace `POST /api/public/orders`; el servidor calcula precios.
7. Se publican eventos a Kafka/Rabbit; el `trackingCode` permite consultar.

---

## Cómo funciona Kafka (explícalo en 30 segundos)

> "Kafka es un log de eventos distribuido y append-only. Orders o payments son
> *productores*: serializan el evento con una clave y lo envían a un *tópico*. El
> *broker* lo escribe en una *partición*. La clave garantiza que un mismo pedido
> siempre va a la misma partición, por lo que se conserva el orden de ese pedido.
> *Report* y *audit* son consumidores en grupos distintos: cada grupo lleva sus
> propios *offsets* y puede leer el mismo hecho sin competir. La diferencia entre
> el offset actual y el final es el *lag*. Si un consumidor falla tras los
> reintentos, el mensaje se publica en el tópico `*.DLT` con su metadata."

Preguntas típicas:

- **¿Por qué 3 particiones?** Para paralelismo y orden por clave dentro de cada
  partición.
- **¿Qué es RF 3 / ISR 2?** Tres copias del dato y al menos dos sincronizadas para
  aceptar escrituras. En EC2 usamos RF 1 por broker único.
- **¿Qué pasa si dos consumidores están en el mismo grupo?** Se reparten las
  particiones; por eso report y audit usan grupos distintos.
- **¿Dónde ves el lag?** Kafka UI → Consumers o
  `kafka-consumer-groups.sh --describe`.
- **¿DLT qué es?** Dead Letter Topic: versión de Kafka de la DLQ; guarda el mensaje
  que no se pudo procesar.

---

## Cómo funciona Cognito (explícalo en 30 segundos)

> "Cognito es el IDaaS de los clientes públicos. El usuario se registra en el User
> Pool `tuki-public-users`, confirma su email con un código y hace login. Cognito
> devuelve un ID token, un access token y un refresh token. El frontend guarda la
> sesión y envía el **ID token** como Bearer a `/api/customer/orders`. El authorizer
> `tuki-cognito` del gateway verifica firma, issuer y audience; después el backend
> valida otra vez y mapea el token siempre a `ROLE_customer`. El refresh token
> renueva la sesión y el logout la descarta."

Preguntas típicas:

- **¿ID token o access token?** ID token: incluye `aud` con el app client y es el
  que acepta el authorizer.
- **¿Puede un usuario Cognito ser admin?** No. El backend ignora `cognito:groups`
  y fuerza `ROLE_customer`.
- **¿Cómo renuevas la sesión?** Con el refresh token en el interceptor.
- **¿Y Entra?** MSAL con Authorization Code + PKCE para usuarios internos; rol
  `admin` desde el tenant.
- **¿Por qué dos authorizers?** Porque conviven dos proveedores; cada ruta declara
  cuál debe validar su JWT.

---

## Banco de preguntas

### Repositorio, CI y página

1. **¿Qué entregas?** El repositorio de GitHub y el ZIP `TUKI-TECH-EFT.zip` con
   código y documentación; el video es plan B.
2. **¿Dónde está el repo?** `github.com/Pastito-247/CLOUD-NATIVE-I`, rama `main`.
3. **¿Cómo se publica la página?** Push a `main` →
   `Deploy Frontend to GitHub Pages` → `dist/` → GitHub Pages.
4. **¿Qué hace `Backend CI`?** Matrix con tests Maven de los 9 microservicios.
5. **¿Por qué Pages no muestra una pantalla blanca?** El workflow reescribe rutas
   a `index.html` y el build incluye el polyfill de `global`.
6. **¿Hay secretos en el repo?** No. `.env` y `*.pem` están excluidos; los valores
   de producción son Variables de GitHub.

### Arquitectura y frontend

7. **¿Qué patrón usa el frontend?** SPA con Angular Router, servicios HTTP,
   guards e interceptors JWT.
8. **¿Cómo evitas manipular precios?** El checkout envía `productId` y cantidad;
   el servidor consulta el catálogo y calcula el total.
9. **¿Cómo es seguro el seguimiento guest?** Solo consulta por `trackingCode`;
   no expone listados de órdenes de terceros.
10. **¿Qué es CORS y dónde se aplica?** Política del navegador; el gateway permite
    el origen de Pages para que el JS pueda leer las respuestas.
11. **¿Qué es mixed content?** Una página HTTPS cargando recursos HTTP; por eso
    todo el frontend y el gateway usan HTTPS.

### API Gateway y seguridad

12. **¿Qué es un API Manager aquí?** API Gateway HTTP API: enrutamiento, CORS,
    throttling y validación JWT.
13. **¿Qué rutas son públicas?** `/api/public/products`, `/categories`, `/users`
    y `/orders` (crear guest).
14. **¿Cómo sabes que un JWT es válido?** Firma, `iss`, `aud` y expiración; lo
    validan el authorizer y el microservicio.
15. **¿Por qué validas dos veces?** Defensa en profundidad: el gateway filtra
    tráfico; el backend no confía ni siquiera en la red interna.
16. **¿Qué es un authorizer?** Función del gateway que decide si la petición pasa
    según el token.
17. **¿Qué es un claim?** Un dato dentro del JWT, por ejemplo `sub`, `roles`,
    `email` o `aud`.

### Kafka

18. **¿Productor y consumidor?** Orders/payments producen; report/audit consumen.
19. **¿Topic y partición?** Tópico = canal lógico; partición = subdivisión ordenada
    del log.
20. **¿Offset?** Posición de un mensaje dentro de una partición.
21. **¿Consumer group?** Conjunto de consumidores que comparten offsets y se
    reparten particiones.
22. **¿Lag?** Mensajes pendientes para un grupo; 0 significa al día.
23. **¿Broker y Zookeeper?** Broker = servidor Kafka; Zookeeper coordina brokers
    en la versión desplegada.
24. **¿Por qué DLT?** Para no perder ni bloquear mensajes defectuosos y poder
    reprocesarlos después.
25. **¿Por qué RF 1 en AWS?** MSK está denegado por IAM y la EC2 tiene un solo
    broker; el compose local sí demuestra RF 3.

### RabbitMQ

26. **¿Direct vs topic?** Direct enruta por routing key exacta; topic por patrón
    con `*` y `#`.
27. **¿Qué es DLQ/DLX?** Cola y exchange dead-letter para mensajes que fallan.
28. **¿Cómo reintentas?** Cabecera `x-retry-count`, 3 intentos y logs
    `[EFT-RETRY]` / `[EFT-DLQ]`.
29. **¿ACK manual?** El consumidor confirma solo después de procesar; si falla,
    el mensaje vuelve a la cola/DLQ.
30. **¿Por qué no solo Kafka?** RabbitMQ es mejor para comandos con confirmación;
    Kafka para eventos inmutables y múltiples lectores.

### Cloud y base de datos

31. **¿Dónde corre el backend?** EC2 `tuki-backend-ef`, servicios systemd, EIP
    `184.195.234.120`.
32. **¿Qué base usas en prod?** RDS PostgreSQL; H2 solo en desarrollo.
33. **¿Cuántas bases?** 7, una por dominio que lo necesita.
34. **¿Qué es un seed idempotente?** Datos iniciales que se insertan solo si no
    existen; reiniciar no duplica.
35. **¿Qué es `ddl-auto: update`?** JPA crea/actualiza el esquema desde entidades.

### Limitaciones (contéstalas con honestidad)

36. **¿Qué limitación conoces?** RF 3 solo local; en AWS Kafka es RF 1 por permisos
    IAM. El tenant Entra se configura manualmente.
37. **¿Usaste Testcontainers?** No; 47 tests unitarios sobre lógica y mensajería.
38. **¿El pago es real?** No, es simulado y genera boleta PDF.
39. **¿Qué harías con más tiempo?** MSK administrado con RF 3, Terraform e
    pruebas de contrato/contenedores.

---

## Glosario rápido

| Término | Significado para tu demo |
|---|---|
| **API Gateway** | Punto único de entrada; rutas, CORS, throttling y authorizers. |
| **Authorizer** | Validador de JWT en el gateway (Entra o Cognito). |
| **Bearer** | Esquema `Authorization: Bearer <token>` para enviar el JWT. |
| **Cognito User Pool** | Directorio de usuarios públicos con registro y confirmación. |
| **App client** | Identidad de la app ante Cognito; aparece en `aud` del ID token. |
| **ID token** | JWT con identidad del usuario; el que envía el frontend. |
| **Access token** | JWT para autorizar APIs de Cognito; aquí no lo usa el gateway. |
| **Refresh token** | Credencial de larga duración para renovar la sesión. |
| **Claim** | Campo del JWT (`sub`, `aud`, `roles`, `email`). |
| **JWT** | JSON Web Token firmado; se valida sin consultar al proveedor. |
| **OIDC / OAuth2** | Protocolos de autorización/login sobre los que funcionan Entra y Cognito. |
| **PKCE** | Protección del flujo de código de autorización para SPAs. |
| **MSAL** | Librería de Microsoft para login con Entra en Angular. |
| **Broker** | Servidor Kafka que guarda y sirve eventos. |
| **Zookeeper** | Coordinación de brokers/metadata en Kafka 3.7. |
| **Topic** | Canal lógico de eventos (`tuki.order.events`). |
| **Partition** | Fragmento ordenado de un topic; permite paralelismo. |
| **Key** | Valor usado para elegir partición y conservar orden por entidad. |
| **Offset** | Número secuencial del registro dentro de la partición. |
| **Consumer group** | Grupo de consumidores que comparten offsets. |
| **Lag** | Registros pendientes para un grupo de consumidores. |
| **RF / ISR** | Réplicas del dato / réplicas sincronizadas. |
| **DLT** | Dead Letter Topic de Kafka. |
| **DLQ / DLX** | Cola y exchange dead-letter de RabbitMQ. |
| **Routing key** | Clave por la que RabbitMQ enruta un mensaje. |
| **Binding** | Regla que une exchange con cola. |
| **ACK** | Confirmación de procesamiento de un mensaje. |
| **SPA** | Single Page Application; Angular corre en el navegador. |
| **CORS** | Permiso del servidor para que otro origen lea la respuesta. |
| **CI/CD** | Integración y despliegue automáticos con GitHub Actions. |
| **Healthcheck** | Prueba que decide si un contenedor está listo. |
| **DTO** | Objeto con la forma exacta del request/response. |
| **Seed** | Datos iniciales cargados de forma idempotente. |
| **Systemd** | Gestor de servicios del EC2 que mantiene los JARs arriba. |
| **EIP** | IP pública fija asociada a la EC2. |
| **RDS** | Base PostgreSQL administrada por AWS. |

Errores que cuestan nota: decir que Cognito da rol admin, confundir ID token con
access token, usar el mismo consumer group para report y audit, mostrar precios
calculados en el cliente o exponer secretos en pantalla.
