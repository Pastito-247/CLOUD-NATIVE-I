# Checklist final EFT

## Código y arquitectura

- [x] 9 microservicios Spring Boot con puertos 8081-8089.
- [x] Kafka: 3 Zookeeper + 3 brokers + UI + init de tópicos.
- [x] Kafka en EC2 `tuki-backend-ef`: `docker-compose.ec2-kafka.yml` y
      `docker/kafka/kafka-init-ec2.sh` (1 ZK + 1 broker, modo reducido RF 1).
- [x] Tópicos `tuki.order.events` y `tuki.payment.events` con 3 particiones y RF 3.
- [x] Tópicos EC2: 4 tópicos con 3 particiones, RF 1 y `min.insync.replicas=1`.
- [x] DLT `tuki.order.events.DLT` y `tuki.payment.events.DLT`.
- [x] Consumidores Kafka en report-service y audit-service.
- [x] Cognito multi-issuer sin escalada a `ROLE_admin`.
- [x] Registro local público desactivado (`410 Gone`).
- [x] Compra guest con `trackingCode`, ítems y precios calculados en servidor.
- [x] Endpoints `/api/public/orders`, `/api/customer/orders` y `/api/customer/payments`.
- [x] API Gateway idempotente con authorizers Entra/Cognito, CORS de 10 orígenes y throttling.
- [x] Payments valida propiedad de la orden con orders antes de cobrar (orden ajena → `403`).
- [x] RabbitMQ: `tuki.commands`, `tuki.catalog.events`, `tuki.dlx` y 4 DLQs.
- [x] Reintentos con `x-retry-count` y logs `[EFT-RETRY]` / `[EFT-DLQ]`.
- [x] PostgreSQL en perfil `prod` para 5 servicios JPA.
- [x] Seeds idempotentes de productos, categorías y usuarios demo.
- [x] Dockerfiles multi-stage para 9 servicios.
- [x] `docker-compose.full.yml` con stack completo.
- [x] Backend CI con matrix de 9 servicios.

## Brechas detectadas en la auditoría EFT (2026-11-20)

- [x] Kafka: `report-service` y `audit-service` usaban el mismo `group.id`
      hardcodeado (`tuki-report`); ahora leen `kafka.consumer.group.report` y
      `.audit`, y envían al DLT configurado de cada tópico.
- [x] `docker/kafka/kafka-init.sh`: ahora falla si los brokers no responden y
      verifica 3 particiones y réplica en los 3 brokers; `kafka-init` espera a
      brokers `healthy` en ambos compose.
- [x] `docker/kafka/kafka-init-ec2.sh`: verificación por `PartitionCount` y
      `ReplicationFactor: 1`; `kafka-init` en EC2 termina con `exited 0`.
- [x] RabbitMQ en EC2: `docker-compose.ec2.yml` pasa de 1 a 2 nodos con cookie y
      descubrimiento compartidos; `deploy-rabbitmq-ec2.ps1` copia la config y
      verifica `rabbitmqctl cluster_status` con los dos nodos.
- [x] `docker-compose.full.yml`: healthcheck agregado a `rabbitmq2`.
- [x] Documentación: guías EFT vueltas a versionar, `.gitignore` con allowlist y
      `create-delivery-archive.ps1` ya incluye los `.md`.
- [x] README actualizado al alcance EFT (público, customer, Cognito, Kafka,
      reportes/auditoría y stack local).
- [x] Frontend: `MyOrdersComponent` prioriza la sesión Entra sobre Cognito.
- [x] Frontend: sesión Cognito con control de `exp`, renovación con
      `refreshToken` en `CognitoInterceptor` y logout que descarta el token.
- [x] Frontend: build limpio (budget ampliado y dependencias CommonJS permitidas).
- [x] Generados `ESTUDIO_EFT.md` y `GUION_PRESENTACION_EFT.md`.

## Verificación posterior a las correcciones

- [x] `mvn -q test` en 9 servicios (incluidos report y audit) → `0`.
- [x] `mvn -q -DskipTests=true package` en 9 servicios → `0`.
- [x] `npm run build` → compila sin warnings ni errores.
- [x] `js-yaml` parsea los 4 docker-compose.
- [x] `Parser::ParseFile` de PowerShell sin errores en `deploy-rabbitmq-ec2.ps1`.
- [x] AWS (solo lectura): API `tuki-api`, Cognito `tuki-public-users`, EC2 activa,
      RDS `tuki-postgres` disponible; públicas 200, protegidas 401, Pages 200.
- [x] EC2 Kafka: swapfile 2 GiB, Docker Compose 2.40.3, `zookeeper`/`kafka`/
      `kafka-ui` activos, `kafka-init exited 0`, topics 3 particiones/RF 1,
      Kafka UI 200, consumidores `tuki-report`/`tuki-audit` unidos a grupos y
      health checks de los 8 backends respondiendo `401` sin token.
- [x] Flujo extremo a extremo en EC2: orden guest `201`
      (`TUKI-JAH9CLXN`), evento `ORDER_CREATED` en `tuki.order.events` y lag `0`
      en los grupos `tuki-report` y `tuki-audit`.
- [x] Acceso EC2 resuelto inyectando una clave pública temporal por `user-data`
      (backup del user-data original guardado fuera del repo); MSK descartado por
      `kafka:CreateCluster = explicitDeny` e `iam:PassRole = implicitDeny`.

## Verificación ejecutable

- [x] `npm run build --prefix frontend`.
- [x] Compilación Maven de los 9 servicios.
- [x] Tests Maven de los 9 servicios.
- [x] Validación YAML de `docker-compose.full.yml` y `application-prod.yml`.
- [x] Validación de sintaxis Bash de `api-gateway-setup.sh` y `rds-setup.sh`.

## Despliegue y verificación AWS / Pages

- [x] Ejecutar `scripts/aws/cognito-setup.sh`.
- [x] Ejecutar `scripts/aws/api-gateway-setup.sh`.
- [x] Ejecutar `scripts/aws/rds-setup.sh` y copiar `DB_HOST` en variables.
- [x] Desplegar Kafka en la EC2 reutilizando `tuki-backend-ef` (MSK no disponible
      por permisos IAM); backends apuntan a `127.0.0.1:9092`.
- [x] Configurar Variables de GitHub con Cognito y API Gateway.
- [x] Corregir beans duplicados en `orders-service` y redesplegar JAR.
- [x] Corregir beans duplicados en `payments-service` y redesplegar JAR.
- [x] Verificar login Cognito con ID token en `/api/customer/orders` → `200`.
- [x] Verificar GitHub Pages sin pantalla blanca tras el polyfill de `global`.
- [x] Rechequeo `2026-11-19`: Pages → `200`, `/api/customer/orders` sin token → `401`.
- [x] API Gateway sin `502/503`: causa identificada y corregida
  (`tuki-payments` en crash loop por beans duplicados). Rechequeo final:
  públicas `200`, customer/privadas sin token `401`.
- [x] Validar en Pages: login Cognito y ID token en `/api/customer/orders` → `200`.
- [x] Rechequeo `2026-11-25`: CORS 10 orígenes, preflight Kafka `200`, pago propio
  (orden `6`, pago `1`) `201`, pago con orden ajena `403`.
- [x] Corregir guardado inicial de `orders` con `total=0` y permitir `/error` en
  `SecurityConfig`; compra guest `201` y consulta por `trackingCode` `200`.
- [x] Validar en Pages: compra guest y consulta por `trackingCode`
  (`TUKI-J3FDNXL9` → `200`).
- [ ] Ensayo cronometrado de 10 minutos con `GUIA_DEFENSA_EFT.md`.
- [ ] Grabar `DEMO_VIDEO.mp4` como plan B.

## Entrega

- [x] Unificar IP de EC2, corregir guías de ID token Cognito y quitar el favicon roto.
- [x] Fallar el build si faltan variables obligatorias en `.env`.
- [x] Guía de defensa actualizada con repositorio GitHub y demo de la página.
- [x] Creados `PRESENTACION_EFT.md` y `PREGUNTAS_Y_TERMINOS_EFT.md`.
- [x] Diagrama de arquitectura versionado en `ARQUITECTURA.md` con fuente
      editable en `docs/arquitectura.mmd` (vista general + flujo de compra).
- [x] Generar ZIP limpio con `scripts/create-delivery-archive.ps1`
      (sin `.env`, `.pem`, `target`, `dist` ni `node_modules`).
- [ ] Hacer `git push` de estos cambios al repositorio y verificar los workflows
      en verde (la rúbrica pide entregar el repo de GitHub).
- [ ] Adjuntar el ZIP al envío.
