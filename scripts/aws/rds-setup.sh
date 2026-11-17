#!/usr/bin/env bash
set -euo pipefail

REGION="${RDS_REGION:-us-east-1}"
IDENTIFIER="${RDS_INSTANCE_ID:-tuki-postgres}"
INSTANCE_CLASS="${RDS_INSTANCE_CLASS:-db.t4g.micro}"
ENGINE_VERSION="${RDS_ENGINE_VERSION:-16}"
MASTER_USER="${DB_USER:-tuki}"
MASTER_PASSWORD="${DB_PASSWORD:-}"
ALLOCATED_STORAGE="${RDS_ALLOCATED_STORAGE:-20}"
SUBNET_GROUP="${RDS_SUBNET_GROUP:-}"
SECURITY_GROUP="${RDS_SECURITY_GROUP_ID:-}"

if [[ -z "$MASTER_PASSWORD" ]]; then
  echo "Falta DB_PASSWORD (password maestro de RDS)." >&2
  exit 1
fi

if aws rds describe-db-instances \
    --region "$REGION" \
    --db-instance-identifier "$IDENTIFIER" >/dev/null 2>&1; then
  echo "= RDS $IDENTIFIER ya existe"
else
  options=()
  if [[ -n "$SUBNET_GROUP" ]]; then
    options+=(--db-subnet-group-name "$SUBNET_GROUP")
  fi
  if [[ -n "$SECURITY_GROUP" ]]; then
    options+=(--vpc-security-group-ids "$SECURITY_GROUP")
  fi
  aws rds create-db-instance \
    --region "$REGION" \
    --db-instance-identifier "$IDENTIFIER" \
    --db-instance-class "$INSTANCE_CLASS" \
    --engine postgres \
    --engine-version "$ENGINE_VERSION" \
    --master-username "$MASTER_USER" \
    --master-user-password "$MASTER_PASSWORD" \
    --allocated-storage "$ALLOCATED_STORAGE" \
    --backup-retention-period 1 \
    --publicly-accessible \
    "${options[@]}" >/dev/null
  echo "+ RDS $IDENTIFIER creada"
fi

aws rds wait db-instance-available \
  --region "$REGION" \
  --db-instance-identifier "$IDENTIFIER"

ENDPOINT=$(aws rds describe-db-instances \
  --region "$REGION" \
  --db-instance-identifier "$IDENTIFIER" \
  --query 'DBInstances[0].Endpoint.Address' \
  --output text)

CREATE_SQL="DO \$\$ DECLARE db text; BEGIN FOREACH db IN ARRAY ARRAY['tuki_products','tuki_categories','tuki_users','tuki_orders','tuki_payments','tuki_audit','tuki_reporting'] LOOP IF NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = db) THEN EXECUTE format('CREATE DATABASE %I', db); END IF; END LOOP; END \$\$;"

if command -v psql >/dev/null 2>&1; then
  PGPASSWORD="$MASTER_PASSWORD" psql \
    -h "$ENDPOINT" \
    -p 5432 \
    -U "$MASTER_USER" \
    -d postgres \
    -v ON_ERROR_STOP=1 \
    -c "$CREATE_SQL"
  echo "+ Bases creadas/verificadas"
else
  echo "psql no encontrado. Crea manualmente estas bases en $ENDPOINT:"
  echo "tuki_products tuki_categories tuki_users tuki_orders tuki_payments tuki_audit tuki_reporting"
fi

echo ""
echo "===== Copiar en .env ====="
echo "DB_HOST=$ENDPOINT"
echo "DB_PORT=5432"
echo "DB_USER=$MASTER_USER"
echo "DB_PASSWORD=$MASTER_PASSWORD"
echo "RDS_INSTANCE_ID=$IDENTIFIER"
