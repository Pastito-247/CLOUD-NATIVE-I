# Presentación EFT — Tuki-Tech (12 diapositivas / 10 minutos)

Formato: defensa individual en video con compartir pantalla. Habla en primera
persona, mira a la cámara y usa las notas de cada diapositiva. Ten abiertas: repo
de GitHub, página publicada, consola AWS, Kafka UI, RabbitMQ UI y terminal.

Enlaces para el cierre y la descripción del video:

- Repo: `https://github.com/Pastito-247/CLOUD-NATIVE-I`
- Página: `https://pastito-247.github.io/CLOUD-NATIVE-I/`
- Gateway: `https://3cbwduklgb.execute-api.us-east-1.amazonaws.com`

Regla: si algo falla en vivo, muestra la captura/video y sigue. Nunca muestres
`.env`, contraseñas ni llaves `.pem`.

---

## Diapositiva 1 — Portada (0:00–0:25)

**En pantalla:** título, nombre, repo y URL de la página.

```text
Tuki-Tech — E-commerce distribuido
Frontend Angular · Spring Boot · AWS · Kafka · RabbitMQ
Repo: github.com/Pastito-247/CLOUD-NATIVE-I
Demo: pastito-247.github.io/CLOUD-NATIVE-I
```

**Notas:**
> "Hola, presento Tuki-Tech. Es un e-commerce distribuido con dos tipos de
> usuario: visitantes que compran sin cuenta y equipo interno que administra el
> catálogo. Hoy mostraré el repositorio, la página funcionando y cómo cada petición
> llega hasta la nube."

**Puente:** “Primero, dónde está el código y cómo se publica.”

---

## Diapositiva 2 — Mapa de la solución (0:25–1:00)

**En pantalla:**

```text
   GitHub Pages (Angular SPA)
              │  HTTPS + CORS
              ▼
     API Gateway (HTTP API)
      ├─ público → /api/public/**
      ├─ Cognito → /api/customer/**
      └─ Entra   → /api/orders, /api/kafka, /api/rabbit...
              │
              ▼
   EC2 tuki-backend-ef (9 servicios systemd)
      ├─ RDS PostgreSQL
      ├─ RabbitMQ (comandos + DLQ)
      └─ Kafka (eventos + DLT)
```

**Notas:**
> "La idea rectora es: RabbitMQ manda comandos que deben ejecutarse una vez; Kafka
> registra hechos que varios consumidores leen a su ritmo. El API Gateway es la
> única entrada y delante de los servicios hay dos proveedores de identidad."

**Puente:** “Esto que ves nace de un solo repositorio.”

---

## Diapositiva 3 — Repositorio y CI (1:00–1:45)

**En pantalla:** GitHub → `Code`, README, carpetas, `Actions`.

- `backend/` 9 módulos Maven · `frontend/` Angular 17.
- `docker-compose*.yml`, `scripts/`, `.github/workflows/`.
- `Backend CI`: matrix de 9 servicios con tests.
- `Deploy Frontend to GitHub Pages`: build → Pages.

**Notas:**
> "El repositorio es la fuente de verdad. Al hacer push a `main`, el workflow
> `Backend CI` corre los tests de los nueve servicios y
> `Deploy Frontend to GitHub Pages` genera el bundle estático y lo publica.
> Muestro el commit y los workflows en verde: eso es evidencia de que el proyecto
> es reproducible y no solo una demo local."

**Puente:** “Veamos exactamente qué se publica y cómo llama al backend.”

---

## Diapositiva 4 — Cómo funciona la página publicada (1:45–2:45)

**En pantalla:** diagrama corto + pestaña de la página.

```text
1. push a main → Actions build (Variables del repo)
2. dist/ → GitHub Pages (index.html + JS/CSS)
3. Angular Router decide la ruta (/publico, /mis-compras, /admin/**)
4. environment.prod.ts → URL del API Gateway
5. fetch con Origin: pastito-247.github.io → Gateway valida CORS
6. Gateway → EC2 → RDS
```

**Notas:**
> "GitHub Pages no ejecuta Java ni Node: sirve archivos estáticos. El bundle de
> Angular corre en el navegador y consume el gateway con la URL de
> `environment.prod.ts`, que se genera en CI desde las Variables del repositorio;
> por eso no hay secretos en el código. El gateway permite el origen de Pages con
> CORS y reenvía a EC2. Las rutas como `/mis-compras` las resuelve el Router de
> Angular, y el workflow reescribe 404 a `index.html` para que funcionen al
> refrescar."

**Puente:** “Con esa cadena lista, entro como visitante.”

---

## Diapositiva 5 — Demo en vivo: catálogo y compra guest (2:45–3:45)

**En pantalla:** `https://pastito-247.github.io/CLOUD-NATIVE-I/` (ventana privada).

1. Home: productos y categorías.
2. Agregar producto → `/checkout-invitado` → confirmar.
3. Copiar `TUKI-XXXXXXXX`.
4. `/seguir-pedido?code=...` → estado de la orden.

**Notas:**
> "Sin token veo el catálogo. El checkout invitado calcula el total en el
> servidor con los precios de la base de datos: el navegador no puede manipularlos.
> La confirmación entrega un código de seguimiento; esa consulta solo devuelve esa
> orden. Es público pero no expone listados de terceros."

**Puente:** “Esa orden ya publicó su evento; lo veo en Kafka.”

---

## Diapositiva 6 — Kafka: eventos, particiones y DLT (3:45–4:45)

**En pantalla:** Kafka UI → Topics → `tuki.order.events`; luego `/admin/reports` y
`/admin/audit`.

- 2 tópicos de negocio + 2 DLT.
- 3 particiones, RF 3 local / RF 1 en EC2.
- Grupos `tuki-report` y `tuki-audit` con lag 0.
- Evento `ORDER_CREATED` del pedido guest.

**Notas:**
> "Orders publica `tuki.order.events` con la orden como clave de partición, para
> que el mismo pedido siempre caiga en la misma partición y mantenga orden.
> Reportes y auditoría están en grupos distintos: los dos reciben el mismo hecho
> sin competir. Si un consumidor falla tras los reintentos, el registro se envía al
> tópico `*.DLT` con su metadata; el flujo principal no se bloquea."

**Puente:** “Ahora el otro tipo de usuario: el público registrado.”

---

## Diapositiva 7 — Identidad: Entra + Cognito (4:45–5:45)

**En pantalla:** `/publico` → localStorage → `/mis-compras`; luego intento de ruta
admin con token Cognito.

- Entra (MSAL): login interno, Authorization Code + PKCE, rol `admin`.
- Cognito: registro, confirmación por código, ID token, rol `customer`.
- El interceptor adjunta `Authorization: Bearer <ID token>`.

**Notas:**
> "Conviven dos proveedores. El interno usa Microsoft Entra con MSAL y PKCE.
> El público usa Cognito: se registra, confirma su email y obtiene un JWT que
> guardo en Local Storage. Envío el ID token porque el access token de Cognito no
> trae `aud` y el authorizer lo rechazaría. El backend mapea Cognito siempre a
> `ROLE_customer`, nunca a admin: es la defensa contra escalada de roles."

**Puente:** “Los dos tokens se validan antes de llegar al servicio.”

---

## Diapositiva 8 — API Gateway (5:45–6:30)

**En pantalla:** consola AWS → `tuki-api` → Routes/Authorizers; terminal con curl.

```bash
curl .../api/public/products              # 200
curl -H "Authorization: Bearer invalido" .../api/orders  # 401
```

**Notas:**
> "El gateway tiene rutas públicas sin authorizer, `/api/customer/orders` con
> `tuki-cognito` y el resto con `tuki-jwt` de Entra. Aplica CORS para el origen de
> Pages, throttling y auto-deploy del stage `$default`. Un token inválido muere
> aquí; y si pasa, el microservicio vuelve a validar issuer, audience y roles."

**Puente:** “El backend además conversa de forma asíncrona.”

---

## Diapositiva 9 — RabbitMQ: colas, exchanges y DLQ (6:30–7:20)

**En pantalla:** RabbitMQ UI → Overview → Exchanges → Queues.

- 2 nodos en clúster.
- `tuki.commands` (direct) y `tuki.catalog.events` (topic).
- `tuki.dlx` + colas `*.dlq`.
- Logs `[EFT-RETRY]` y `[EFT-DLQ]`.

**Notas:**
> "RabbitMQ se usa para comandos: notificación, stock, catálogo. El exchange
> direct enruta por routing key exacta y el topic por patrón. Cada cola tiene su
> DLQ; los consumidores reintentan tres veces con una cabecera de conteo y luego
> publican en la DLQ y dejan log. La respuesta HTTP al usuario nunca espera a
> estos reintentos."

**Puente:** “Todo esto se apoya en datos persistentes.”

---

## Diapositiva 10 — Nube y persistencia (7:20–8:05)

**En pantalla:** EC2 `tuki-backend-ef`, `systemctl`, RDS `tuki-postgres`.

- Backend en EC2 con EIP `184.195.234.120`.
- 5 servicios JPA con `SPRING_PROFILES_ACTIVE=prod`.
- 7 bases PostgreSQL; seeds idempotentes.
- Kafka reducido en la misma EC2 (RF 1) por restricciones de IAM para MSK.

**Notas:**
> "En producción no uso H2: el perfil `prod` apunta a RDS PostgreSQL. Los datos
> sobreviven al reinicio de un servicio porque los seeds son idempotentes y las
> migraciones las gestiona JPA. Reutilicé la EC2 del backend para Kafka en modo
> reducido: MSK no fue viable porque la cuenta tiene `kafka:CreateCluster` en
> `explicitDeny`."

**Puente:** “Y todo está probado y empaquetado.”

---

## Diapositiva 11 — Calidad y entrega (8:05–8:45)

**En pantalla:** terminal + `Actions` + contenido del ZIP.

- 47 tests Maven en 9 servicios; build Angular limpio.
- Dockerfiles multi-stage y CI en verde.
- `TUKI-TECH-EFT.zip`: 322 archivos, sin `.env`, `.pem`, `target`, `dist` ni
  `node_modules`.

**Notas:**
> "Cada microservicio tiene tests de servicios, publicadores, consumidores y
> reglas de seguridad. El ZIP que entrego se genera con un script que excluye
> secretos y artefactos de build. El repositorio queda como fuente de verdad y el
> ZIP es el paquete para el docente."

**Puente:** “Cierro con lo que debes recordar si me preguntan.”

---

## Diapositiva 12 — Cierre y preguntas (8:45–10:00)

**En pantalla:** rostro en cámara + links.

```text
Repo:   github.com/Pastito-247/CLOUD-NATIVE-I
Página: pastito-247.github.io/CLOUD-NATIVE-I
Entrego: repo + ZIP + video plan B
```

**Notas:**
> "Para cerrar: el repositorio contiene frontend y backend completos, dos
> identidades conviviendo, API Gateway con rutas públicas y protegidas, RDS,
> RabbitMQ con DLQ y Kafka con particiones, offsets y dead-letter. La página
> pública es el escaparate del flujo guest y el gateway es la puerta de entrada.
> Dejo el repo, el ZIP y el video de respaldo. Gracias."

**Si preguntan:** usa [PREGUNTAS_Y_TERMINOS_EFT.md](./PREGUNTAS_Y_TERMINOS_EFT.md).
**Si falla la demo:** reproduce `DEMO_VIDEO.mp4` o muestra las capturas; no
improvisar arreglos en vivo.

---

## Checklist 2 minutos antes de grabar

- [ ] `git push` realizado; commit visible en GitHub.
- [ ] Workflows `Backend CI` y `Deploy Frontend to GitHub Pages` en verde.
- [ ] Pestañas cargadas: repo, Pages, API Gateway, Kafka UI, RabbitMQ UI.
- [ ] `curl` de Pages `200`, público `200`, privado `401` ya ejecutado.
- [ ] `trackingCode` de prueba anotado.
- [ ] Sin `.env`, passwords ni `.pem` en pantalla.
- [ ] `TUKI-TECH-EFT.zip` regenerado tras el último cambio.
