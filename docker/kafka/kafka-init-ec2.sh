#!/usr/bin/env bash
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}"
PARTITIONS=3
REPLICATION=1
MIN_ISR=1
RETENTION_MS=604800000
DLT_RETENTION_MS=2592000000

echo "Esperando al broker en $BOOTSTRAP ..."
BROKER_READY=false
for i in $(seq 1 60); do
  if /opt/bitnami/kafka/bin/kafka-broker-api-versions.sh \
      --bootstrap-server "$BOOTSTRAP" >/dev/null 2>&1; then
    echo "Broker listo."
    BROKER_READY=true
    break
  fi
  sleep 2
done

if [[ "$BROKER_READY" != "true" ]]; then
  echo "ERROR: el broker no respondio despues de 120s." >&2
  exit 1
fi

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
    --config "min.insync.replicas=$MIN_ISR"
}

create_topic "tuki.order.events"          "$RETENTION_MS"
create_topic "tuki.payment.events"        "$RETENTION_MS"
create_topic "tuki.order.events.DLT"      "$DLT_RETENTION_MS"
create_topic "tuki.payment.events.DLT"    "$DLT_RETENTION_MS"

echo "===== Verificando topicos ====="
TOPICS=$(/opt/bitnami/kafka/bin/kafka-topics.sh \
  --bootstrap-server "$BOOTSTRAP" \
  --describe)

for name in "tuki.order.events" "tuki.payment.events" \
            "tuki.order.events.DLT" "tuki.payment.events.DLT"; do
  escaped="${name//./\\.}"
  details=$(printf '%s\n' "$TOPICS" | grep -E "^Topic: ${escaped}[[:space:]]" || true)
  count=$(printf '%s\n' "$details" | sed -n 's/.*PartitionCount: \([0-9][0-9]*\).*/\1/p')
  if [[ "$count" -ne "$PARTITIONS" ]]; then
    echo "ERROR: $name tiene $count particiones (esperadas $PARTITIONS)." >&2
    exit 1
  fi
  if ! printf '%s\n' "$details" | grep -q "ReplicationFactor: 1"; then
    echo "ERROR: $name no tiene replica en el broker." >&2
    exit 1
  fi
done

printf '%s\n' "$TOPICS"
