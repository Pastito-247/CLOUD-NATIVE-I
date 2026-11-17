# Checklist final EFT

## Código y arquitectura

- [x] 9 microservicios Spring Boot con puertos 8081-8089.
- [x] Kafka: 3 Zookeeper + 3 brokers + UI + init de tópicos.
- [x] Tópicos `tuki.order.events` y `tuki.payment.events` con 3 particiones y RF 3.
- [x] DLT `tuki.order.events.DLT` y `tuki.payment.events.DLT`.
- [x] Consumidores Kafka en report-service y audit-service.
- [x] Cognito multi-issuer sin escalada a `ROLE_admin`.
- [x] Registro local público desactivado (`410 Gone`).
- [x] Compra guest con `trackingCode`, ítems y precios calculados en servidor.
- [x] Endpoints `/api/public/orders` y `/api/customer/orders`.
- [x] API Gateway idempotente con authorizers Entra/Cognito, CORS y throttling.
- [x] RabbitMQ: `tuki.commands`, `tuki.catalog.events`, `tuki.dlx` y 4 DLQs.
- [x] Reintentos con `x-retry-count` y logs `[EFT-RETRY]` / `[EFT-DLQ]`.
- [x] PostgreSQL en perfil `prod` para 5 servicios JPA.
- [x] Seeds idempotentes de productos, categorías y usuarios demo.
- [x] Dockerfiles multi-stage para 9 servicios.
- [x] `docker-compose.full.yml` con stack completo.
- [x] Backend CI con matrix de 9 servicios.

## Verificación ejecutable

- [x] `npm run build --prefix frontend`.
- [x] Compilación Maven de los 9 servicios.
- [x] Tests Maven de los 9 servicios.
- [x] Validación YAML de `docker-compose.full.yml` y `application-prod.yml`.
- [x] Validación de sintaxis Bash de `api-gateway-setup.sh` y `rds-setup.sh`.

## Pendiente externo con credenciales AWS / Docker

- [x] Ejecutar `scripts/aws/cognito-setup.sh`.
- [x] Ejecutar `scripts/aws/api-gateway-setup.sh`.
- [x] Ejecutar `scripts/aws/rds-setup.sh` y copiar `DB_HOST` en variables.
- [ ] Configurar Variables de GitHub con Cognito y API Gateway.
- [x] Desplegar/verificar EC2 y API Gateway sin `503`.
- [x] Corregir beans duplicados en `orders-service` y redesplegar JAR.
- [x] Verificar login Cognito con ID token en `/api/customer/orders` → `200`.
- [ ] Ensayo cronometrado de 10 minutos.
- [ ] Grabar `DEMO_VIDEO.mp4` como plan B.
