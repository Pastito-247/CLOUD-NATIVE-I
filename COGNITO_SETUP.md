# Cognito — federación pública (EFT)

Dos identidades conviven en el sistema:

| Identidad | Uso | Frontend | Backend |
|---|---|---|---|
| Microsoft Entra ID (IDaaS) | Admin / Operador | MSAL | JWT issuer Entra |
| AWS Cognito User Pool | Clientes públicos | `amazon-cognito-identity-js` | JWT issuer Cognito → `ROLE_customer` |

## 1. Crear el User Pool

Con AWS CLI configurado (`aws configure` o SSO):

```bash
bash scripts/aws/cognito-setup.sh
```

El script crea:

- User Pool `tuki-public-users` con verificación automática de email, política de
  contraseña (8+, mayúscula, minúscula, número) y auto-registro habilitado.
- App Client `tuki-web` sin secret (SPA) con SRP + refresh + user-password auth.
- Grupo `customer`.

Copia los valores impresos en `.env`:

```dotenv
COGNITO_REGION=us-east-1
COGNITO_USER_POOL_ID=us-east-1_xxxxxxx
COGNITO_CLIENT_ID=xxxxxxxxxxxxxxxxxxxxxxxxxx
COGNITO_ISSUER=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_xxxxxxx
```

Luego:

```bash
npm run generate-env --prefix frontend
```

> El email de confirmación usa el límite por defecto de Cognito (~50/día). Para
> producción, configurar SES en el User Pool. Para la demo, usa cuentas de prueba.

## 2. Flujo en el frontend

- Ruta `/publico` (también `/register` redirige ahí): registro → confirmación por
  código → login → JWT guardado en `localStorage` (`tuki_cognito_session`).
- "Olvidé mi contraseña": código por email + nueva contraseña.
- "Salir (cliente)": limpia la sesión local y el token.
- `CognitoInterceptor` agrega `Authorization: Bearer <access_token>` solo a rutas
  `/api/customer/**`. Las rutas Entra siguen usando `MsalInterceptor`.

## 3. Flujo en el backend

`orders`, `payments` y `users` usan un `AuthenticationManagerResolver` que lee el
claim `iss` del JWT y elige el decoder correcto:

- issuer Entra → audience `api://tuki-tech-api` / client id.
- issuer Cognito → audience `<COGNITO_CLIENT_ID>`.
- cualquier otro issuer → `BadCredentialsException` (401).

Mapeo de roles (`SecurityConfig.jwtAuthenticationConverter`):

- Entra: claim `roles` → `ROLE_admin|staff|customer`.
- Cognito: si trae `cognito:groups=admin` → `ROLE_admin`; si no → `ROLE_customer`.
- **Un token Cognito sin grupo nunca obtiene `ROLE_admin`**.

## 4. Rutas

| Ruta | Auth | Rol |
|---|---|---|
| `/api/public/**` | sin token | cualquiera |
| `/api/customer/**` | JWT Cognito | `customer` |
| `/api/orders`, `/api/payments/**`, `/api/receipts/**` | JWT Entra | según endpoint |
| `/api/rabbit/**`, `/api/kafka/**` | JWT Entra | `admin` |

## 5. Verificación

```bash
# Sin token: público pasa
curl -i <GATEWAY>/api/public/products

# Sin token: privado rechaza
curl -i <GATEWAY>/api/orders        # 401

# Con access_token de Cognito: cliente
curl -H "Authorization: Bearer <TOKEN>" <GATEWAY>/api/customer/orders   # 200

# Con token Cognito en ruta admin: bloqueado
curl -H "Authorization: Bearer <TOKEN>" <GATEWAY>/api/rabbit/queues     # 403
```

> El registro local con selector de rol admin fue eliminado:
> `/api/public/users/register` responde `410 Gone`.
