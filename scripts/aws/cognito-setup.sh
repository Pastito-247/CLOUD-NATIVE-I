#!/usr/bin/env bash
# =====================================================================
# Crea el User Pool de Cognito para usuarios publicos (EFT).
# Requiere AWS CLI con credenciales configuradas (aws configure / SSO).
#
#   bash scripts/aws/cognito-setup.sh
#
# Al terminar, copia los IDs impresos a .env (COGNITO_USER_POOL_ID,
# COGNITO_CLIENT_ID, COGNITO_ISSUER).
# =====================================================================
set -euo pipefail

REGION="${COGNITO_REGION:-us-east-1}"
POOL_NAME="tuki-public-users"
CLIENT_NAME="tuki-web"

echo "Buscando User Pool '$POOL_NAME' en $REGION ..."
POOL_ID=$(aws cognito-idp list-user-pools \
  --region "$REGION" \
  --max-results 60 \
  --query "UserPools[?Name=='$POOL_NAME'].Id" --output text | head -n 1)

if [[ -z "$POOL_ID" || "$POOL_ID" == "None" ]]; then
  POOL_ID=$(aws cognito-idp create-user-pool \
    --region "$REGION" \
    --pool-name "$POOL_NAME" \
    --policies '{
      "PasswordPolicy": {
        "MinimumLength": 8,
        "RequireUppercase": true,
        "RequireLowercase": true,
        "RequireNumbers": true,
        "RequireSymbols": false
      }
    }' \
    --auto-verified-attributes email \
    --admin-create-user-config '{"AllowAdminCreateUserOnly": false}' \
    --query 'UserPool.Id' --output text)
  echo "+ User Pool creado: $POOL_ID"
else
  echo "= User Pool existente: $POOL_ID"
fi

echo "Buscando App Client '$CLIENT_NAME' ..."
CLIENT_ID=$(aws cognito-idp list-user-pool-clients \
  --region "$REGION" \
  --user-pool-id "$POOL_ID" \
  --max-results 60 \
  --query "UserPoolClients[?ClientName=='$CLIENT_NAME'].ClientId" --output text | head -n 1)

if [[ -z "$CLIENT_ID" || "$CLIENT_ID" == "None" ]]; then
  CLIENT_ID=$(aws cognito-idp create-user-pool-client \
    --region "$REGION" \
    --user-pool-id "$POOL_ID" \
    --client-name "$CLIENT_NAME" \
    --no-generate-secret \
    --explicit-auth-flows ALLOW_USER_SRP_AUTH ALLOW_REFRESH_TOKEN_AUTH ALLOW_USER_PASSWORD_AUTH \
    --query 'UserPoolClient.ClientId' --output text)
  echo "+ App Client creado: $CLIENT_ID"
else
  echo "= App Client existente: $CLIENT_ID"
fi

echo "Creando grupo 'customer' ..."
aws cognito-idp create-group \
  --region "$REGION" \
  --user-pool-id "$POOL_ID" \
  --group-name customer >/dev/null 2>&1 || echo "El grupo customer ya existe o no se pudo crear."

ISSUER="https://cognito-idp.${REGION}.amazonaws.com/${POOL_ID}"

echo ""
echo "===== Copiar en .env ====="
echo "COGNITO_REGION=$REGION"
echo "COGNITO_USER_POOL_ID=$POOL_ID"
echo "COGNITO_CLIENT_ID=$CLIENT_ID"
echo "COGNITO_ISSUER=$ISSUER"
echo ""
echo "Nota: la confirmacion por email usa el limite por defecto de Cognito (~50/dia)."
echo "Para produccion, configurar SES en el User Pool."
