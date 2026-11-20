# Estudio EFT — Tuki-Tech

Guía de study para la defensa individual. El caso es un e-commerce distribuido:
la entidad principal es la **orden** y la secundaria es el **catálogo**
(productos/categorías). RabbitMQ transporta **comandos** ("notifica", "descuenta
stock"); Kafka transporta **hechos ya ocurridos** ("orden creada", "pago
confirmado") para reportes y auditoría.

## 1. Mapa mental de la arquitectura

```
Visitante (sin token) ──► API Gateway /api/public/** ──► products / categories / orders
Usuario Cognito ───────► API Gateway /api/customer/** ─► orders (JWT Cognito)
Admin/Operador Entra ──► API Gateway /api/** ─────────► 9 microservicios (JWT Entra)

orders ──RabbitMQ──► notify / stock / revisión (comandos + DLQ + reintentos)
orders/payments ──Kafka──► report + audit (KPIs y trazabilidad) ──► DLT si falla
todos los servicios JPA ──► RDS PostgreSQL (perfil prod)
```

### Microservicios (Spring Boot 3.2 / Java 17)

| Servicio | Puerto | Responsabilidad |
|---|---:|---|
| products | 8081 | Catálogo público y administración de productos |
| categories | 8082 | Categorías públicas y administración |
| users | 8083 | Perfiles, roles y consumidores de notificaciones |
| orders | 8084 | Órdenes autenticadas, órdenes guest y tracking |
| rabbitmq-admin | 8085 | Colas, exchanges, bindings y demo de DLQ |
| payments | 8086 | Pago simulado y boleta PDF |
| report | 8087 | Consumidor Kafka de KPIs |
| audit | 8088 | Consumidor Kafka de trazabilidad |
| kafka-admin | 8089 | Tópicos, configuraciones, grupos y offsets |

## 2. Identidad: dos fuentes que conviven

| Fuente | Quién la usa | Frontend | Backend / Gateway |
|---|---|---|---|
| Entra ID (IDaaS interno) | Admin / Operador | MSAL, Authorization Code + PKCE | Authorizer JWT `tuki-jwt` + `SecurityConfig` con issuer/audience |
| Cognito User Pool | Usuario público | `/publico`: signup, confirmación, login, forgot-password, logout | Authorizer JWT `tuki-cognito` + validación multi-issuer en orders/payments/users |

**Concepto clave:** el frontend decide la URL; el **API Gateway y el backend** deciden
el acceso. Un JWT de cliente Cognito no habilita rutas de administración: el rol
viaja en claims y los endpoints admin exigen `ROLE_admin`.

Rutas:

- Públicas (sin JWT): `/api/public/products/**`, `/api/public/categories/**`,
  `/api/public/orders/**`, `/api/public/users/**`.
- Cliente (JWT Cognito): `/api/customer/orders/**`.
- Protegidas (JWT Entra + rol): `/api/products/**`, `/api/categories/**`,
  `/api/users/**`, `/api/orders/**`, `/api/payments/**`, `/api/receipts/**`,
  `/api/rabbit/**`, `/api/kafka/**`, `/api/reports/**`, `/api/audit/**`.

## 3. Transacción pública (invitado)

1. `/` consulta catálogo sin token.
2. `/checkout-invitado` envía nombre, email e ítems a `POST /api/public/orders`.
3. El servidor recalcula precios, persiste orden + ítems, genera `trackingCode`
   `TUKI-XXXXXXXX`, publica evento Kafka y comando RabbitMQ.
4. `/seguir-pedido?code=...` consulta el estado sin token.

**Límite de lo público:** solo catálogo, crear la propia orden y consultar por
código. No expone listados de otros clientes, KPIs, auditoría ni administración.
Si una acción modifica estado sensible o administra la plataforma, lleva token.

## 4. RabbitMQ — comandos

- Cluster: 2 nodos (`rabbitmq1`, `rabbitmq2`) en `docker-compose.full.yml`; en EC2
  `docker-compose.ec2.yml` también levanta 2 nodos con descubrimiento por config.
- Exchanges: `tuki.commands` (direct), `tuki.catalog.events` (topic),
  `tuki.dlx` (direct).
- Colas de comando: `tuki.notify.order.queue`, `tuki.notify.payment.queue`,
  `tuki.notify.receipt.queue` y `tuki.catalog.sync.queue`.
- Cada cola tiene su DLQ (`*.dlq`) vía `x-dead-letter-exchange` y routing key.
- Reintentos: hasta 3 con cabecera `x-retry-count`; al agotarse se publica en DLQ
  y se escribe log `[EFT-RETRY]` / `[EFT-DLQ]`. Los consumidores usan ack manual.
- Admin: `GET/POST/DELETE /api/rabbit/queues|exchanges|bindings` con rol admin.

**DLQ vs DLT:** DLQ es la cola de mensajes muertos de RabbitMQ; DLT es el tópico
dead-letter de Kafka. En ambos casos el mensaje original se conserva y se loguea.

## 5. Kafka — eventos

- Cluster: 3 Zookeeper + 3 brokers (`docker-compose.kafka.yml` y
  `docker-compose.full.yml`), Kafka UI en `:8080`.
- Tópicos: `tuki.order.events`, `tuki.payment.events` → **3 particiones, RF 3**,
  `min.insync.replicas=2`, `cleanup.policy=delete`, retention 7 días.
- DLT: `tuki.order.events.DLT`, `tuki.payment.events.DLT` → retention 30 días.
- Productores: DTO tipados en orders y payments; también publican órdenes guest.
- Consumidores: `report-service` ( grupo `tuki-report`) y `audit-service`
  (grupo `tuki-audit`), con `DeadLetterPublishingRecoverer` y 3 reintentos.
- Admin: `/api/kafka/**` permite crear/listar/eliminar tópicos, ver/actualizar
  configuración, grupos y offsets.

**Partición** = unidad de paralelismo y orden; **réplica** = copia para
tolerancia a fallos (RF 3 con ISR mínimo 2); **consumer group** = balanceo de
carga entre consumidores del mismo tópico; **offset** = posición leída; **lag** =
mensajes pendientes; **retention** = tiempo que el mensaje permanece.

## 6. Persistencia y despliegue

- H2 en desarrollo; `application-prod.yml` con PostgreSQL (`DB_HOST`, `DB_PORT`,
  `DB_NAME`, `DB_USER`, `DB_PASSWORD`) para los 7 servicios JPA. Entidades,
  repositorios y CRUD por dominio.
- RDS `tuki-postgres` Verificada en AWS.
- API Gateway `tuki-api` en stage `$default`, con dos authorizers JWT, CORS y
  auto-deploy. Verificados: públicas `200`, customer/privadas sin token `401`.
- Frontend en GitHub Pages `https://pastito-247.github.io/CLOUD-NATIVE-I/` (200).
- Backend en EC2 vía systemd; RabbitMQ en Docker en el mismo EC2.
- Kafka se demuestra con Docker Compose local (3 ZK + 3 brokers, RF 3) y también
  en la EC2 `tuki-backend-ef` con un modo reducido (1 ZK + 1 broker, RF 1,
  `docker-compose.ec2-kafka.yml`). MSK no fue viable por permisos IAM
  (`kafka:CreateCluster = explicitDeny`, `iam:PassRole = implicitDeny`); decirlo
  explícitamente si preguntan por "nube administrada".

## 7. Mapa rápido de la rúbrica (encargo 20%)

| Individuo evaluado | Peso | Dónde está la evidencia |
|---|---:|---|
| Backend completo | 11% | 9 módulos Maven, 47 tests, CRUD y DTO |
| Frontend completo | 9% | Angular 17, rutas públicas/cliente/admin |
| Frontend ↔ IDaaS | 6% | MSAL + guards + interceptor Bearer |
| Backend valida JWT | 6% | `SecurityConfig` multi-issuer por servicio |
| BD cloud | 6% | `application-prod.yml` + entidades/repositorios |
| Clúster RabbitMQ | 8% | `docker-compose*.yml`, 2 nodos, UI `:15672` |
| Prod/cons colas | 12% | publishers/consumers en 5 servicios |
| DLQ con log | 6% | `*.dlq`, `[EFT-DLQ]`, demo en rabbit-admin |
| Admin RabbitMQ | 6% | `/api/rabbit/**` + pantalla `/admin/rabbitmq` |
| Kafka 3 ZK + 3 brokers | 15% | `docker-compose.kafka.yml`, init de tópicos |
| Prod/cons tópicos | 10% | orders/payments → report/audit |
| Admin Kafka | 5% | `/api/kafka/**` + pantalla `/admin/kafka` |

Defensa (80%): API Manager, rutas y validación JWT, tenant IDaaS, login OAuth,
despliegue cloud, RabbitMQ (nodos/colas/DLQ/exchanges), Kafka (tópicos/UI),
federación pública Cognito y transacción guest. El guion
[GUION_PRESENTACION_EFT.md](./GUION_PRESENTACION_EFT.md) cubre esos 14 puntos.

## 8. Comandos que debes saber explicar

```bash
# Stack local completo
docker compose -f docker-compose.full.yml up -d --build
docker compose -f docker-compose.full.yml ps

# Estado del cluster RabbitMQ
docker exec rabbitmq1 rabbitmqctl cluster_status

# Tópicos y grupos Kafka
docker exec kafka1 kafka-topics.sh --bootstrap-server kafka1:9094 --describe
docker exec kafka1 kafka-consumer-groups.sh --bootstrap-server kafka1:9094 --list

# API pública / protegida
curl https://$API_GATEWAY_URL/api/public/products
curl -i https://$API_GATEWAY_URL/api/orders        # 401 sin token
curl -H "Authorization: Bearer $TOKEN" https://$API_GATEWAY_URL/api/orders

# Verificación local
mvn test                       # en cada backend/*-service
npm run build --prefix frontend
```

## 9. Preguntas probables y respuesta corta

1. **¿Por qué RabbitMQ y Kafka?** RabbitMQ para comandos que deben ejecutarse una
   vez y confirmarse (notificaciones, stock); Kafka para eventos inmutables que
   varios consumidores procesan a su ritmo (reportes, auditoría).
2. **¿Qué pasa si un consumidor falla?** RabbitMQ reintenta 3 veces y envía a DLQ
   con log; Kafka reintenta y publica el registro en el `*.DLT`.
3. **¿Cómo evitas escalada de roles?** El backend valida issuer/audience y el rol
   de los claims; Cognito solo produce `ROLE_customer`, nunca `ROLE_admin`.
4. **¿Por qué dos authorizers?** Porque conviven dos proveedores de identidad;
   cada ruta declara cuál valida su JWT.
5. **¿Qué es público y por qué es seguro?** Catálogo, crear orden guest y
   consultar por `trackingCode`; no expone datos de terceros ni funciones admin.
6. **¿Cómo se calcula el total de una orden guest?** En el servidor, desde la BD;
   el cliente no envía precios de confianza.
7. **¿Qué significa RF 3 / ISR 2?** Tres copias del dato; al menos dos sincronizadas
   para aceptar escrituras.
8. **¿Qué pasa si hay dos consumidores en el mismo grupo?** Se reparten las
   particiones; si necesitan procesar el mismo evento (report y audit), usan
   grupos distintos.
9. **¿Dónde ves el lag?** En Kafka UI → Consumers, o con
   `kafka-consumer-groups.sh --describe`.
10. **¿Cómo reproduces la demo?** `docker compose -f docker-compose.full.yml up -d`,
    frontend en `localhost:4200`, y las cuentas/credenciales de `.env` (nunca
    versionado).
11. **¿Qué es el API Manager aquí?** API Gateway HTTP API: enrutamiento, CORS,
    throttling y validación JWT antes de llegar al backend.
12. **¿Cómo se que el JWT es válido?** El gateway verifica firma/issuer/audience;
    el backend repite la validación con `NimbusJwtDecoder` y mapea roles.
13. **¿Qué limitación conoces?** El cluster RF 3 se levanta local con Compose; en
    AWS se usa un broker único RF 1 en la EC2 porque MSK está denegado por
    IAM. El tenant Entra se configura manualmente; el resto está verificado en nube.

## 10. Errores que cuestan nota

- Mostrar una ruta pública en el frontend pero protegida en el gateway (o al revés).
- Que la orden guest no genere evento Kafka.
- Usar el mismo consumer group para report y auditoría.
- Olvidar el `trackingCode` o exponer listados de órdenes sin token.
- Decir "el pago es real": es **simulado** y genera boleta PDF.
- Romper el login Entra al agregar Cognito: son dos flujos independientes.
