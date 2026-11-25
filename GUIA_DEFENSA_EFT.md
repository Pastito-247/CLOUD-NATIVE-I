# Guion de defensa EFT — 10 minutos

## Objetivo

Mostrar, en orden, **el repositorio de GitHub**, **la página publicada y cómo
funciona**, la infraestructura, la compra pública con seguimiento, Kafka con DLT,
Cognito con roles, API Gateway, RabbitMQ con DLQ y persistencia PostgreSQL. Cada
bloque tiene una evidencia visible y una frase de cierre para que el docente
pueda marcar el ítem correspondiente.

## Antes de empezar

Pestañas abiertas: GitHub (repo), página publicada, consola AWS (API Gateway y
Cognito), Kafka UI, RabbitMQ UI y una terminal.

Enlaces que debes decir en voz alta:

- Repositorio: `https://github.com/Pastito-247/CLOUD-NATIVE-I`
- Página publicada: `https://pastito-247.github.io/CLOUD-NATIVE-I/`
- API Gateway: `https://3cbwduklgb.execute-api.us-east-1.amazonaws.com`

Reglas: no muestres `.env`, passwords ni el `.pem`; muestra solo nombres de
recursos. Si AWS o Docker fallan, no arregles en vivo: usa la evidencia ya
capturada o el video.

Verificación rápida (10 segundos):

```bash
curl -s -o /dev/null -w '%{http_code}\n' https://pastito-247.github.io/CLOUD-NATIVE-I/                         # 200
curl -s -o /dev/null -w '%{http_code}\n' https://3cbwduklgb.execute-api.us-east-1.amazonaws.com/api/public/products  # 200
curl -s -o /dev/null -w '%{http_code}\n' https://3cbwduklgb.execute-api.us-east-1.amazonaws.com/api/orders          # 401
```

---

## 0:00 - 1:00 | Repositorio GitHub y página publicada

**Pantalla 1:** pestaña del repo → pestaña `Code`. Mostrar README, carpetas
`backend/`, `frontend/`, `scripts/`, `docker-compose*.yml` y `.github/workflows`.

**Acciones:**

1. Mostrar el último commit y los workflows `Backend CI` y
   `Deploy Frontend to GitHub Pages` en verde.
2. Abrir la página publicada y mostrar el catálogo sin iniciar sesión.
3. Explicar el recorrido de una petición:

```text
Navegador
  → GitHub Pages (Angular estático)
  → API Gateway (CORS, throttling, authorizer JWT)
  → EC2 tuki-backend-ef (systemd)
  → RDS PostgreSQL
```

**Di:**

> "El repositorio es la fuente de verdad. Al hacer push a `main`, GitHub Actions
> compila el backend con sus tests y publica el frontend en GitHub Pages. La
> página no es un servidor: es una SPA estática que llama al API Gateway con la
> URL de `environment.prod.ts`, generada desde las Variables del repositorio.
> El gateway aplica CORS y valida JWT antes de enrutar hacia los servicios en EC2
> contra RDS."

Frase de cierre: **"Repo → CI → Pages → Gateway → EC2 → RDS: ese es el camino de
cada petición."**

---

## 1:00 - 1:45 | Infraestructura local y cloud

```bash
docker compose -f docker-compose.full.yml up -d --build
docker compose -f docker-compose.full.yml ps
```

**Evidencias:** PostgreSQL `5432`, RabbitMQ `15672`, Kafka UI `8080`, 2 nodos
RabbitMQ, 3 ZK + 3 brokers, 9 microservicios. En AWS: EC2 `tuki-backend-ef` con
EIP `184.195.234.120` y RDS `tuki-postgres` disponible.

Frase: “Localmente demo el cluster RF 3; en AWS reutilicé la EC2 para Kafka en
modo reducido RF 1 porque MSK está denegado por permisos IAM”.

---

## 1:45 - 3:00 | Catálogo y compra sin token (en la página publicada)

**Pantalla:** `https://pastito-247.github.io/CLOUD-NATIVE-I/` en ventana privada.

**Acciones:**

1. Home: catálogo y categorías desde `/api/public/products` y
   `/api/public/categories`.
2. `/checkout-invitado`: nombre, email, dirección, productos y confirmar.
3. Copiar el `trackingCode` con formato `TUKI-XXXXXXXX`.
4. `/seguir-pedido`: consultar el código.

Frase: “El flujo público calcula precios en el servidor, persiste orden e ítems,
publica a RabbitMQ y Kafka y permite seguimiento sin exponer datos de otros
clientes”.

---

## 3:00 - 4:15 | Kafka y DLT

**Evidencias en Kafka UI:**

- `tuki.order.events` y `tuki.payment.events`: 3 particiones, RF 3,
  `min.insync.replicas=2` en el compose local; RF 1 en EC2.
- `tuki.order.events.DLT` y `tuki.payment.events.DLT`.
- Buscar el evento del pedido guest recién creado; mostrar
  `CURRENT-OFFSET = LOG-END-OFFSET` (lag 0) en `tuki-report` y `tuki-audit`.

**Evidencias en frontend:** `/admin/reports` con KPI actualizado y `/admin/audit`
con registro de actor `guest`.

Frase: “Orders y payments publican hechos; report y audit son grupos de
consumidores distintos, por lo que ambos procesan el mismo evento. Si falla un
consumidor, tras los reintentos el mensaje va al DLT con sus metadatos”.

---

## 4:15 - 5:45 | Cognito y roles

**Acciones:**

1. Abrir `/publico`, registrar usuario, confirmar código y hacer login.
2. DevTools → Application → Local Storage: mostrar `tuki_cognito_session`.
3. Copiar el **ID token** (no el access token).
4. Abrir `/mis-compras`: solo las órdenes de ese usuario.
5. Con el ID token Cognito llamar a una ruta Entra: debe dar `401`.

```powershell
$GW = "https://3cbwduklgb.execute-api.us-east-1.amazonaws.com"
curl -H "Authorization: Bearer $COGNITO_TOKEN" "$GW/api/rabbit/queues"
# 401: el authorizer Entra no acepta un token de Cognito
```

Cierre: “El authorizer Cognito solo protege `/api/customer/*`; en el backend el
token Cognito siempre se mapea a `ROLE_customer` y se ignora `cognito:groups`,
por eso un cliente público nunca escala a admin”.

Si AWS no está disponible, mostrar el fragmento de `SecurityConfig.java` donde
Cognito se mapea solo a `ROLE_customer`.

---

## 5:45 - 6:45 | API Gateway

**Pantalla:** consola AWS → API Gateway → API `3cbwduklgb`.

- Authorizers `tuki-jwt` (Entra; audiencias `api://tuki-tech-api` y
  `66c0f84b-3701-42fb-9189-3f4092cba201`) y `tuki-cognito` (audiencia
  `a00kh0fv055nqc8mqqmge24p7`).
- Rutas públicas: `/api/public/products`, `/api/public/categories`,
  `/api/public/users`, `/api/public/orders`.
- Rutas customer con ID token Cognito: `/api/customer/orders` y
  `/api/customer/payments`.
- Rutas privadas: `/api/orders`, `/api/rabbit`, `/api/kafka`, `/api/reports`,
  `/api/audit`.
- Stage `$default` con CORS de 10 orígenes (incluye
  `https://pastito-247.github.io`), throttling y auto-deploy.
- Preflight `OPTIONS` sin autorizador en todas las rutas;
  `OPTIONS /api/kafka/{proxy+}` verificado en `200`.

```powershell
curl "$GW/api/public/products"                         # 200
curl -H "Authorization: Bearer invalido" "$GW/api/orders"  # 401
```

Frase: “El gateway es la única entrada: enruta, limita, aplica CORS y valida el
JWT; después cada microservicio valida otra vez issuer, audience y roles”.

---

## 6:45 - 7:45 | RabbitMQ direct + topic y DLQ

```powershell
curl -u tuki:tuki http://localhost:15672/api/overview
curl -H "Authorization: Bearer $ENTRA_ADMIN_TOKEN" "$GW/api/rabbit/demo/topology"
```

Publicar un comando con fallo:

```powershell
$body = @{
  exchange = "commands"
  routingKey = "notify.order"
  body = "demo fail"
  fail = $true
} | ConvertTo-Json

curl -X POST -H "Content-Type: application/json" -H "Authorization: Bearer $ENTRA_ADMIN_TOKEN" -Body $body "$GW/api/rabbit/demo/publish"
```

**Evidencias:** exchange direct `tuki.commands`, exchange topic
`tuki.catalog.events`, DLX `tuki.dlx`, cola `tuki.notify.order.queue` y DLQ
`tuki.notify.order.dlq`; logs `[EFT-RETRY]` y después `[EFT-DLQ]`.

Frase: “Los comandos se confirman; si el consumidor agota los reintentos, el
mensaje va a la DLQ y queda en logs. La respuesta HTTP al usuario no se bloquea”.

---

## 7:45 - 8:30 | Persistencia PostgreSQL y seed

```bash
bash scripts/aws/rds-setup.sh
```

**Evidencias:** endpoint RDS y bases `tuki_products`, `tuki_categories`,
`tuki_users`, `tuki_orders`, `tuki_payments`, `tuki_audit`, `tuki_reporting`.
Perfil `prod` en los servicios JPA; reiniciar un servicio y verificar que la
orden guest sigue existiendo.

Frase: “El perfil `prod` cambia H2 por PostgreSQL con `ddl-auto: update`; los
seeds son idempotentes y no se pierden al reiniciar”.

---

## 8:30 - 9:20 | Pruebas, CI y paquete de entrega

**Pantalla:** terminal y pestaña `Actions` del repo.

- `mvn -q test` en 9 servicios → `0` (47 tests).
- `npm run build --prefix frontend` → limpio.
- Workflow `Backend CI` en verde.
- `TUKI-TECH-EFT.zip` generado con `scripts/create-delivery-archive.ps1` (322
  archivos, sin `.env`, `.pem`, `target`, `dist` ni `node_modules`).

Frase: “Cada servicio tiene Dockerfile multi-stage, el repo incluye CI y el ZIP
que entrego contiene solo código y documentación”.

---

## 9:20 - 10:00 | Cierre y plan B

Resumen final:

- Repo y página: `https://github.com/Pastito-247/CLOUD-NATIVE-I` y
  `https://pastito-247.github.io/CLOUD-NATIVE-I/`.
- Kafka: 2 tópicos + DLT, 3 particiones (RF 3 local / RF 1 en EC2).
- Cognito: login público, siempre `customer`.
- Guest: orden con `trackingCode` y consulta segura.
- API Gateway: rutas públicas, customer y admin con dos authorizers.
- RabbitMQ: exchanges direct/topic, reintentos y DLQ.
- RDS: persistencia por microservicio.

Plan B si falla AWS o Docker:

1. Dejar abiertas las pestañas de GitHub, Pages, Kafka UI y Rabbit Management ya
   cargadas.
2. Reproducir `DEMO_VIDEO.mp4` con el flujo completo (si ya fue grabado; si no,
   mostrar capturas de Pages, Kafka UI y RabbitMQ).
3. Mostrar el commit final y el workflow `Backend CI` en verde.

### Datos reales del despliegue

- Gateway: `https://3cbwduklgb.execute-api.us-east-1.amazonaws.com`
- EC2 EIP: `184.195.234.120`
- RDS: `tuki-postgres.cmvwnxo9nvlo.us-east-1.rds.amazonaws.com`
- Usuario Cognito: `cliente@tuki.cl` (password en `.env` local, variable
  `COGNITO_DEMO_PASSWORD`)
- Cliente Cognito: `a00kh0fv055nqc8mqqmge24p7`

Para la ruta customer, el frontend envía el **ID token** de Cognito porque el
access token no incluye `aud` y el authorizer lo rechazaría.

Rechequeo `2026-11-20`: Pages `200`, público `200`, privado sin token `401`,
Kafka UI `200`, evento guest con lag `0`.

Rechequeo `2026-11-25`: CORS con 10 orígenes en gateway y en los 9 units de
systemd; preflight `OPTIONS /api/kafka/topics` desde
`https://tuki-tech.vercel.app` → `200`; `/api/customer/orders` con ID token
Cognito → `200`, sin token → `401`; `POST /api/customer/payments` con orden
ajena → `403`; pago propio (orden `6`, pago `1`) → `201`; `/api/audit` sin token
→ `401`. Si aparece `502/503`, espera ~70 s tras reiniciar `tuki-kafka-admin` y
revisa `systemctl is-active tuki-payments`.

Kafka UI en la nube: `http://184.195.234.120/kafka-ui/` (nginx → contenedor
`kafka-ui` con `SERVER_SERVLET_CONTEXT_PATH=/kafka-ui`). Para que reportes y
auditoría muestren datos, los consumidores usan `tuki-report-v2` /
`tuki-audit-v2` con `auto-offset-reset=earliest` y perfil `prod`; al cambiar el
estado de una orden desde admin se publica el evento correspondiente.

## Material de apoyo

- Diapositivas y notas: [PRESENTACION_EFT.md](./PRESENTACION_EFT.md).
- Preguntas y glosario: [PREGUNTAS_Y_TERMINOS_EFT.md](./PREGUNTAS_Y_TERMINOS_EFT.md).
- Guion hablado: [GUION_PRESENTACION_EFT.md](./GUION_PRESENTACION_EFT.md).
- Estudio y mapa de rúbrica: [ESTUDIO_EFT.md](./ESTUDIO_EFT.md).
