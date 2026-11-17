# Kafka — infraestructura de la demo EFT

Arquitectura levantada con `docker-compose.kafka.yml`:

- 3 nodos Zookeeper (`zookeeper1..3`)
- 3 brokers (`kafka1..3`) con puertos host `9092`, `9093`, `9094`
- `kafka-init`: contenedor one-shot que crea los tópicos
- `kafka-ui`: http://localhost:8080

## Levantar

```bash
docker compose -f docker-compose.kafka.yml up -d
docker compose -f docker-compose.kafka.yml ps
docker logs -f kafka-init
```

> Requiere Docker Desktop con ~6 GB de RAM.

## Tópicos

| Tópico | Particiones | Réplicas | Retención | Propósito |
|---|---|---|---|---|
| `tuki.order.events` | 3 | 3 | 7 días | Eventos del ciclo de vida de pedidos |
| `tuki.payment.events` | 3 | 3 | 7 días | Eventos de pago |
| `tuki.order.events.DLT` | 3 | 3 | 30 días | Mensajes fallidos de pedidos |
| `tuki.payment.events.DLT` | 3 | 3 | 30 días | Mensajes fallidos de pagos |

Todos con `cleanup.policy=delete` y `min.insync.replicas=2`.

## Verificación

```bash
docker exec kafka1 kafka-topics.sh \
  --bootstrap-server kafka1:9094 \
  --describe
```

Debe mostrar 2 tópicos de negocio + 2 DLT, cada uno con 3 particiones y RF 3.

Ver el clúster en Kafka UI: http://localhost:8080 → `tuki-kafka` → **Brokers** (3),
**Topics** (particiones, mensajes, offsets) y luego **Consumers** (lag).

## Productor / consumidor de prueba

```bash
# Productor
docker exec -it kafka1 kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic tuki.order.events

# Consumidor (en otra terminal)
docker exec -it kafka2 kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic tuki.order.events \
  --from-beginning
```

## Bajar y limpiar

```bash
docker compose -f docker-compose.kafka.yml down -v
```
