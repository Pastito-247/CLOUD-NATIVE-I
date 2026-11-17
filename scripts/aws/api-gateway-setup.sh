#!/usr/bin/env bash
set -euo pipefail

REGION="${API_GATEWAY_REGION:-${AWS_REGION:-us-east-1}}"
API_ID="${API_GATEWAY_ID:-3cbwduklgb}"
BACKEND_HOST="${BACKEND_HOST:-http://184.192.252.196}"
STAGE_NAME='$default'
ENTRA_ISSUER="${ENTRA_ISSUER:-${ENTRA_AUTHORITY:-https://login.microsoftonline.com/e3e92dfe-ea59-4c42-a539-90e6fea570b6/v2.0}}"
ENTRA_AUDIENCE="${ENTRA_API_URI:-api://tuki-tech-api}"
COGNITO_ISSUER="${COGNITO_ISSUER:-}"
COGNITO_CLIENT_ID="${COGNITO_CLIENT_ID:-}"
CORS_ORIGIN="${CORS_ORIGIN:-https://pastito-247.github.io}"

if [[ -z "$COGNITO_ISSUER" || -z "$COGNITO_CLIENT_ID" ]]; then
  echo "Faltan COGNITO_ISSUER o COGNITO_CLIENT_ID. Copialos de .env." >&2
  exit 1
fi

aws2() {
  aws apigatewayv2 "$@" --region "$REGION"
}

aws sts get-caller-identity --region "$REGION" >/dev/null
aws2 get-api --api-id "$API_ID" >/dev/null

LAST_AUTHORIZER_ID=""
LAST_INTEGRATION_ID=""

upsert_authorizer() {
  local name="$1"
  local issuer="$2"
  local audience="$3"
  local id
  local jwt_config

  id=$(aws2 get-authorizers --api-id "$API_ID" \
    --query "Items[?Name=='$name'].AuthorizerId" --output text 2>/dev/null | head -n 1 || true)
  jwt_config="{\"Issuer\":\"$issuer\",\"Audience\":[\"$audience\"]}"

  if [[ -z "$id" || "$id" == "None" ]]; then
    id=$(aws2 create-authorizer \
      --api-id "$API_ID" \
      --name "$name" \
      --authorizer-type JWT \
      --identity-source '$request.header.Authorization' \
      --jwt-configuration "$jwt_config" \
      --query AuthorizerId --output text)
    echo "+ authorizer $name -> $id"
  else
    aws2 update-authorizer \
      --api-id "$API_ID" \
      --authorizer-id "$id" \
      --name "$name" \
      --authorizer-type JWT \
      --identity-source '$request.header.Authorization' \
      --jwt-configuration "$jwt_config" >/dev/null
    echo "= authorizer $name -> $id"
  fi
  LAST_AUTHORIZER_ID="$id"
}

upsert_integration() {
  local uri="$1"
  local id

  id=$(aws2 get-integrations --api-id "$API_ID" \
    --query "Items[?IntegrationUri=='$uri'].IntegrationId" --output text 2>/dev/null | head -n 1 || true)

  if [[ -z "$id" || "$id" == "None" ]]; then
    id=$(aws2 create-integration \
      --api-id "$API_ID" \
      --integration-type HTTP_PROXY \
      --integration-uri "$uri" \
      --integration-method ANY \
      --payload-format-version '1.0' \
      --query IntegrationId --output text)
    echo "+ integration $uri -> $id"
  else
    aws2 update-integration \
      --api-id "$API_ID" \
      --integration-id "$id" \
      --integration-type HTTP_PROXY \
      --integration-uri "$uri" \
      --integration-method ANY \
      --payload-format-version '1.0' >/dev/null
    echo "= integration $uri -> $id"
  fi
  LAST_INTEGRATION_ID="$id"
}

upsert_route() {
  local route_key="$1"
  local integration_id="$2"
  local auth_type="$3"
  local authorizer_id="${4:-}"
  local route_id
  local target
  local args

  target="integrations/$integration_id"
  route_id=$(aws2 get-routes --api-id "$API_ID" \
    --query "Items[?RouteKey=='$route_key'].RouteId" --output text 2>/dev/null | head -n 1 || true)

  args=(--authorization-type "$auth_type")
  if [[ "$auth_type" == "JWT" ]]; then
    args+=(--authorizer-id "$authorizer_id")
  fi

  if [[ -z "$route_id" || "$route_id" == "None" ]]; then
    aws2 create-route \
      --api-id "$API_ID" \
      --route-key "$route_key" \
      --target "$target" \
      "${args[@]}" >/dev/null
    echo "+ route $route_key [$auth_type]"
  else
    aws2 update-route \
      --api-id "$API_ID" \
      --route-id "$route_id" \
      --route-key "$route_key" \
      --target "$target" \
      "${args[@]}" >/dev/null
    echo "= route $route_key [$auth_type]"
  fi
}

add_resource() {
  local base="$1"
  local auth_type="$2"
  local authorizer_id="${3:-}"
  local bare_uri
  local proxy_uri
  local bare_id
  local proxy_id

  bare_uri="${BACKEND_HOST}${base}"
  proxy_uri="${bare_uri}/{proxy}"

  upsert_integration "$bare_uri"
  bare_id="$LAST_INTEGRATION_ID"
  upsert_integration "$proxy_uri"
  proxy_id="$LAST_INTEGRATION_ID"

  upsert_route "ANY $base" "$bare_id" "$auth_type" "$authorizer_id"
  upsert_route "ANY ${base}/{proxy+}" "$proxy_id" "$auth_type" "$authorizer_id"
  upsert_route "OPTIONS $base" "$bare_id" "NONE"
  upsert_route "OPTIONS ${base}/{proxy+}" "$proxy_id" "NONE"

  if [[ "$auth_type" == "NONE" ]]; then
    PUBLIC_ROUTE_KEYS+=("ANY $base" "ANY ${base}/{proxy+}")
  fi
}

PUBLIC_ROUTE_KEYS=()

echo "API Gateway: $API_ID ($REGION)"
echo "Backend: $BACKEND_HOST"

upsert_authorizer tuki-jwt "$ENTRA_ISSUER" "$ENTRA_AUDIENCE"
ENTRA_AUTH_ID="$LAST_AUTHORIZER_ID"
upsert_authorizer tuki-cognito "$COGNITO_ISSUER" "$COGNITO_CLIENT_ID"
COGNITO_AUTH_ID="$LAST_AUTHORIZER_ID"

add_resource /api/public/products NONE
add_resource /api/public/categories NONE
add_resource /api/public/users NONE
add_resource /api/public/orders NONE
add_resource /api/customer/orders JWT "$COGNITO_AUTH_ID"
add_resource /api/products JWT "$ENTRA_AUTH_ID"
add_resource /api/categories JWT "$ENTRA_AUTH_ID"
add_resource /api/users JWT "$ENTRA_AUTH_ID"
add_resource /api/orders JWT "$ENTRA_AUTH_ID"
add_resource /api/rabbit JWT "$ENTRA_AUTH_ID"
add_resource /api/payments JWT "$ENTRA_AUTH_ID"
add_resource /api/receipts JWT "$ENTRA_AUTH_ID"
add_resource /api/reports JWT "$ENTRA_AUTH_ID"
add_resource /api/audit JWT "$ENTRA_AUTH_ID"
add_resource /api/kafka JWT "$ENTRA_AUTH_ID"

aws2 update-api \
  --api-id "$API_ID" \
  --cors-configuration "{\"AllowOrigins\":[\"$CORS_ORIGIN\",\"http://localhost:4200\"],\"AllowMethods\":[\"GET\",\"POST\",\"PUT\",\"DELETE\",\"PATCH\",\"OPTIONS\"],\"AllowHeaders\":[\"authorization\",\"content-type\",\"x-requested-with\"],\"ExposeHeaders\":[\"*\"],\"AllowCredentials\":false}" >/dev/null

if ! aws2 get-stage --api-id "$API_ID" --stage-name "$STAGE_NAME" >/dev/null 2>&1; then
  aws2 create-stage --api-id "$API_ID" --stage-name "$STAGE_NAME" --auto-deploy >/dev/null
fi

route_settings='{'
first=1
for key in "${PUBLIC_ROUTE_KEYS[@]}"; do
  escaped="${key//\\/\\\\}"
  escaped="${escaped//\"/\\\"}"
  if [[ "$first" -eq 0 ]]; then
    route_settings+=','
  fi
  first=0
  route_settings+="\"$escaped\":{\"ThrottlingRateLimit\":20.0,\"ThrottlingBurstLimit\":40}"
done
route_settings+='}'

aws2 update-stage \
  --api-id "$API_ID" \
  --stage-name "$STAGE_NAME" \
  --auto-deploy \
  --default-route-settings '{"ThrottlingRateLimit":50.0,"ThrottlingBurstLimit":100}' \
  --route-settings "$route_settings" >/dev/null

BASE_URL="https://${API_ID}.execute-api.${REGION}.amazonaws.com"
echo ""
echo "API lista: $BASE_URL"
echo "Sin token:     curl -i $BASE_URL/api/public/products"
echo "Guest:         curl -i -X POST $BASE_URL/api/public/orders"
echo "Sin token:     curl -i $BASE_URL/api/orders  (esperado 401)"
echo "Con Cognito:   curl -H \"Authorization: Bearer <TOKEN>\" $BASE_URL/api/customer/orders"
