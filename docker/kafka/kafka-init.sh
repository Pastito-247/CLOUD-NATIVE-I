#!/usr/bin/env bash
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP_SERVERS:-kafka1:9094,kafka2:9094,kafka3:9094}"
PARTITIONS=3
REPLICATION=3
RETENTION_MS=604800000
DLT_RETENTION_MS=2592000000

echo "Esperando a los brokers en $BOOTSTRAP ..."
for i in $(seq 1 60); do
  if /opt/bitnami/kafka/bin/kafka-broker-api-versions.sh \
      --bootstrap-server "$BOOTSTRAP" >/dev/null 2>&1; then
    echo "Brokers listos."
    break
  fi
  sleep 2
done

create_topic () {
  local name="$1"
  local retention="$2"
  echo "Creando/verificando topico: $name"
  /opt/bitnami/kafka/bin/kafka-topics.sh \
    --bootstrap-server "$BOOTSTRAP" \
    --create \
    --if-not-exists \
    --topic "$name" \
    --partitions "$PARTITIONS" \
    --replication-factor "$REPLICATION" \
    --config "retention.ms=$retention" \
    --config "cleanup.policy=delete" \
    --config "min.insync.replicas=2"
}

create_topic "tuki.order.events"          "$RETENTION_MS"
create_topic "tuki.payment.events"        "$RETENTION_MS"
create_topic "tuki.order.events.DLT"      "$DLT_RETENTION_MS"
create_topic "tuki.payment.events.DLT"    "$DLT_RETENTION_MS"

echo "===== Topicos creados ====="
/opt/bitnami/kafka/bin/kafka-topics.sh \
  --bootstrap-server "$BOOTSTRAP" \
  --describe
