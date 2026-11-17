# Guion de defensa EFT — 10 minutos

## Objetivo

Mostrar, en orden, la infraestructura local, la compra pública con seguimiento,
Kafka con DLT, Cognito con roles, API Gateway, RabbitMQ con DLQ y persistencia
PostgreSQL. Cada bloque tiene una evidencia visible y una frase de cierre.

---

## 0:00 - 1:00 | Infraestructura local

```bash
docker compose -f docker-compose.full.yml up -d --build
docker compose -f docker-compose.full.yml ps
```

Evidencias:

- PostgreSQL en `5432`, RabbitMQ en `15672`, Kafka UI en `8080`.
- RabbitMQ: `http://localhost:15672` con `tuki` / `tuki`.
- Kafka UI: `http://localhost:8080`.

Frase: “La arquitectura completa levanta 9 microservicios, 5 bases PostgreSQL,
2 nodos RabbitMQ y un cluster Kafka de 3 brokers con UI”.

---

## 1:00 - 2:30 | Catálogo y compra sin token

```bash
npm start --prefix frontend
```

Evidencias:

1. Abrir `http://localhost:4200` sin login: catálogo con productos y categorías.
2. Ir a `/checkout-invitado`, llenar nombre/email, agregar productos y confirmar.
3. Copiar el `trackingCode` con formato `TUKI-XXXXXXXX`.
4. Ir a `/seguir-pedido` y consultar el código.

Frase: “El flujo público calcula precios en el servidor, persiste orden e ítems,
publica a RabbitMQ/Kafka y permite seguimiento sin exponer datos de otros
clientes”.

---

## 2:30 - 4:00 | Kafka y DLT

Evidencias en Kafka UI:

- `tuki.order.events`: 3 particiones, RF 3, `min.insync.replicas=2`.
- `tuki.payment.events`: misma configuración.
- `tuki.order.events.DLT` y `tuki.payment.events.DLT`.
- Buscar el evento del pedido guest recién creado.

Evidencias en frontend:

- `/admin/reports`: KPI actualizado.
- `/admin/audit`: registro con actor `guest`.

Frase: “Los eventos de dominio se publican con clave de partición y reintentos;
si el consumidor falla, el mensaje termina en el DLT y queda trazable”.

---

## 4:00 - 6:00 | Cognito y roles

```bash
bash scripts/aws/cognito-setup.sh
```

Evidencias:

1. Abrir `/publico`, registrar usuario, confirmar código y hacer login.
2. En DevTools → Application → Local Storage, mostrar `tuki_cognito_session`.
3. Copiar el access token JWT.
4. Abrir `/mis-compras`: solo las órdenes del usuario autenticado.
5. Con token Cognito llamar a una ruta admin: debe dar `403`.

```powershell
$GW = "https://3cbwduklgb.execute-api.us-east-1.amazonaws.com"
curl -H "Authorization: Bearer $COGNITO_TOKEN" "$GW/api/orders"
```

Cierre: “El token Cognito siempre se mapea a `ROLE_customer`; el claim
`cognito:groups` se ignora, por eso un cliente público nunca escala a admin”.

Si AWS no está disponible, usar el video/screenshot del flujo y el fragmento de
`SecurityConfig.java` donde Cognito se mapea solo a `ROLE_customer`.

---

## 6:00 - 7:30 | API Gateway

Evidencias en consola AWS → API Gateway → API `3cbwduklgb`:

- Authorizers `tuki-jwt` (Entra) y `tuki-cognito`.
- Rutas públicas: `/api/public/products`, `/api/public/categories`,
  `/api/public/users`, `/api/public/orders`.
- Ruta customer: `/api/customer/orders`.
- Rutas privadas: `/api/orders`, `/api/rabbit`, `/api/reports`, `/api/audit`, etc.
- Stage `$default` con CORS y throttling.

```powershell
curl "$GW/api/public/products"
curl -H "Authorization: Bearer invalido" "$GW/api/orders"
```

Resultado esperado: catálogo `200`, ruta privada con token inválido `401`.

---

## 7:30 - 8:30 | RabbitMQ direct + topic y DLQ

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

Evidencias:

- Exchange direct `tuki.commands`, exchange topic `tuki.catalog.events`, DLX
  `tuki.dlx`.
- Colas `tuki.notify.order.queue` y DLQ `tuki.notify.order.dlq`.
- Log `[EFT-RETRY]` y después `[EFT-DLQ]`.

---

## 8:30 - 9:15 | Persistencia PostgreSQL y seed

```bash
bash scripts/aws/rds-setup.sh
```

Evidencias:

- Endpoint RDS y bases `tuki_products`, `tuki_categories`, `tuki_users`,
  `tuki_orders`, `tuki_payments`, `tuki_audit`, `tuki_reporting`.
- `SPRING_PROFILES_ACTIVE=prod` en los 5 servicios JPA.
- Reiniciar un servicio y verificar que el pedido guest sigue existiendo.

Frase: “El perfil `prod` cambia H2 por PostgreSQL con `ddl-auto: update`; los
seeds son idempotentes y no se pierden al reiniciar”.

### Datos reales del despliegue

- Gateway: `https://3cbwduklgb.execute-api.us-east-1.amazonaws.com`
- EC2 EIP: `184.195.234.120`
- RDS: `tuki-postgres.cmvwnxo9nvlo.us-east-1.rds.amazonaws.com`
- Usuario Cognito: `cliente@tuki.cl` (password en `.env` local, variable `COGNITO_DEMO_PASSWORD`)
- Cliente Cognito: `a00kh0fv055nqc8mqqmge24p7`

Verificación rápida en vivo:

```bash
curl https://3cbwduklgb.execute-api.us-east-1.amazonaws.com/api/public/products
# 200

curl https://3cbwduklgb.execute-api.us-east-1.amazonaws.com/api/customer/orders
# 401 sin token
```

Para la ruta customer, el frontend envía el **ID token** de Cognito porque el
access token no incluye `aud` y el authorizer lo rechazaría.

---

## 9:15 - 10:00 | Cierre y plan B

Resumen final:

- Kafka: 2 tópicos + DLT, 3 particiones, RF 3.
- Cognito: login público, siempre `customer`.
- Guest: orden con `trackingCode` y consulta segura.
- API Gateway: rutas públicas, customer y admin con authorizers.
- RabbitMQ: exchanges direct/topic, reintentos y DLQ.
- RDS: persistencia por microservicio.

Plan B si falla AWS o Docker:

1. Dejar abiertas las pestañas de Kafka UI y Rabbit Management ya cargadas.
2. Reproducir `DEMO_VIDEO.mp4` con el flujo completo.
3. Mostrar el commit final y el workflow `Backend CI` en verde.
