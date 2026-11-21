# Arquitectura — Tuki-Tech (Entrega Final EFT)

Diagrama de arquitectura del sistema. La fuente editable en Mermaid está en
[`docs/arquitectura.mmd`](./docs/arquitectura.mmd); se puede exportar a PNG/SVG con:

```bash
npx @mermaid-js/mermaid-cli -i docs/arquitectura.mmd -o docs/arquitectura.png --size 1800
```

Las imágenes ya generadas están en [`docs/arquitectura.png`](./docs/arquitectura.png)
y [`docs/flujo-compra.png`](./docs/flujo-compra.png), listas para insertar en
diapositivas.

## 1. Vista general

![Vista general de la arquitectura](./docs/arquitectura.png)

```mermaid
flowchart TB
    subgraph CLIENTES["Clientes"]
        V["Visitante<br/>sin token"]
        C["Cliente publico<br/>Cognito"]
        A["Admin / Operador<br/>Entra ID"]
    end

    subgraph IDENTIDAD["Proveedores de identidad"]
        ENTRA[("Microsoft Entra ID<br/>Authorization Code + PKCE")]
        COG[("Amazon Cognito<br/>tuki-public-users")]
    end

    subgraph ENTRADA["Capa de entrada"]
        PAGES["GitHub Pages<br/>Angular 17 SPA<br/>deploy-pages.yml"]
        GW["AWS API Gateway - HTTP API<br/>tuki-api · stage $default<br/>CORS · throttling · auto-deploy"]
    end

    subgraph AWS["AWS · EC2 tuki-backend-ef"]
        direction TB
        MS["9 microservicios Spring Boot 3.2 / Java 17<br/>products 8081 · categories 8082 · users 8083 · orders 8084<br/>rabbit-admin 8085 · payments 8086<br/>report 8087 · audit 8088 · kafka-admin 8089"]
        MQ["RabbitMQ - Docker · 2 nodos<br/>tuki.commands · tuki.catalog.events<br/>tuki.dlx + 4 DLQ · reintentos x-retry-count"]
        KK["Kafka - Docker<br/>local: 3 ZK + 3 brokers RF3<br/>EC2: 1 ZK + 1 broker RF1<br/>tuki.order.events · tuki.payment.events + *.DLT"]
    end

    DB[("Amazon RDS PostgreSQL<br/>tuki-postgres<br/>7 bases por dominio")]

    V -->|HTTPS| PAGES
    C -->|HTTPS| PAGES
    A -->|HTTPS| PAGES
    A -.->|"login MSAL"| ENTRA
    C -.->|"signup / confirm / login"| COG
    PAGES -->|"HTTPS + CORS"| GW
    ENTRA -.->|"JWT tuki-jwt"| GW
    COG -.->|"ID token tuki-cognito"| GW

    GW -->|"/api/public/** sin authorizer"| MS
    GW -->|"/api/customer/** Cognito"| MS
    GW -->|"/api/** Entra + rol"| MS

    MS -->|"JPA · SPRING_PROFILES_ACTIVE=prod"| DB
    MS <-->|"comandos + ACK/NACK + DLQ"| MQ
    MS <-->|"hechos inmutables + DLT"| KK
```

## 2. Flujo de compra pública y pago

![Flujo de compra pública y pago](./docs/flujo-compra.png)

```mermaid
sequenceDiagram
    autonumber
    actor U as Visitante
    participant P as Pages / Angular
    participant G as API Gateway
    participant O as orders-service
    participant R as RabbitMQ
    participant K as Kafka
    participant W as users / products
    participant RP as report + audit
    participant D as RDS PostgreSQL

    U->>P: /checkout-invitado con items
    P->>G: POST /api/public/orders
    G->>O: sin authorizer (ruta publica)
    O->>D: orden + items + trackingCode TUKI-XXXXXXXX
    O->>R: comando notify.order
    O->>K: ORDER_CREATED
    K->>RP: KPIs y auditoria (grupos distintos)
    R->>W: notificacion / descuento de stock
    O-->>P: 201 + trackingCode
    U->>P: /seguir-pedido?code=TUKI-XXXXXXXX
    P->>G: GET /api/public/orders/{code}
    G->>O: sin authorizer (ruta publica)
    O->>D: consulta por trackingCode
    O-->>P: estado de la orden

    Note over O,R: Pago autenticado: POST /api/payments → payment.exchange → boleta PDF → receipt.exchange → correo, revision y stock. fail@demo.cl fuerza la DLQ.
```

## 3. Servicios y responsabilidad

| Servicio | Puerto | Responsabilidad | Persistencia |
|---|---:|---|---|
| products | 8081 | Catálogo público y administración; descuento de stock | `tuki_products` |
| categories | 8082 | Categorías públicas y administración | `tuki_categories` |
| users | 8083 | Perfiles, roles y consumidores de notificaciones | `tuki_users` |
| orders | 8084 | Órdenes autenticadas, guest y tracking | `tuki_orders` |
| rabbitmq-admin | 8085 | Colas, exchanges, bindings y demo de DLQ | — |
| payments | 8086 | Pago simulado y boleta PDF (OpenPDF) | `tuki_payments` |
| report | 8087 | Consumidor Kafka de KPIs | `tuki_reporting` |
| audit | 8088 | Consumidor Kafka de trazabilidad | `tuki_audit` |
| kafka-admin | 8089 | Tópicos, configuraciones, grupos y offsets | — |

## 4. Decisiones de diseño

- **Dos brokers de mensajería con roles distintos.** RabbitMQ transporta
  *comandos* que deben ejecutarse una vez y confirmarse (notificación, stock,
  revisión), con ACK manual, 3 reintentos (`x-retry-count`) y DLQ por cola.
  Kafka transporta *hechos ya ocurridos* (`ORDER_CREATED`, `PAYMENT_APPROVED`)
  que varios consumidores leen a su ritmo, con DLT y 3 reintentos.
- **Dos proveedores de identidad.** Entra ID para admin/operador (MSAL + PKCE)
  y Cognito para el usuario público. El API Gateway declara un authorizer por
  familia de rutas; el backend repite la validación de issuer/audience y mapea
  los tokens Cognito siempre a `ROLE_customer`, nunca a `ROLE_admin`.
- **Doble validación JWT.** El gateway valida firma, issuer y audience antes de
  enrutar; cada microservicio vuelve a validar con `NimbusJwtDecoder` y aplica
  `@PreAuthorize` por rol.
- **Una base por dominio.** H2 con `create-drop` en local; PostgreSQL en el
  perfil `prod` (`ddl-auto: update`) con una base por servicio JPA en RDS.
- **SPA estática.** GitHub Pages solo sirve archivos; la URL del gateway se
  genera en CI desde Variables del repositorio, por lo que no hay secretos en
  el bundle.

## 5. Topología según el entorno

| Componente | Local / `docker-compose.full.yml` | AWS |
|---|---|---|
| RabbitMQ | 2 nodos, UI `:15672` | 2 nodos en EC2, UI pública |
| Kafka | 3 Zookeeper + 3 brokers, RF 3, ISR 2, Kafka UI `:8080` | 1 ZK + 1 broker en EC2, RF 1, ISR 1 (MSK denegado por IAM) |
| PostgreSQL | Contenedor `postgres:16` | RDS `tuki-postgres`, 7 bases |
| Backend | 9 contenedores Spring Boot | systemd en `tuki-backend-ef` |
| Frontend | `http://localhost:4200` | GitHub Pages |

## 6. Guías relacionadas

- [README.md](./README.md) — descripción general, instalación y endpoints.
- [ESTUDIO_EFT.md](./ESTUDIO_EFT.md) — mapa mental y mapa de rúbrica.
- [GUIA_DEFENSA_EFT.md](./GUIA_DEFENSA_EFT.md) — guion de defensa de 10 minutos.
- [RABBITMQ_SETUP.md](./RABBITMQ_SETUP.md) y [KAFKA_SETUP.md](./KAFKA_SETUP.md).
- [API_GATEWAY_DEPLOYMENT.md](./API_GATEWAY_DEPLOYMENT.md) y [EC2_DEPLOYMENT.md](./EC2_DEPLOYMENT.md).
