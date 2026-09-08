# Guía de Configuración de Azure AD (EntraID)

Esta guía explica cómo configurar Azure AD para la autenticación del proyecto E-Commerce Tech.

## 1. Crear Tenant de Azure AD

Si no tienes un tenant de Azure AD:

1. Ve a [Azure Portal](https://portal.azure.com)
2. Busca "Azure Active Directory" o "Microsoft Entra ID"
3. Si no tienes uno, crea un nuevo tenant:
   - Tipo: "Solo en la nube" o "Híbrido"
   - Nombre de organización: tu-organizacion
   - Dominio inicial: tu-dominio.onmicrosoft.com

## 2. Registrar Aplicación en Azure AD

### 2.1 Crear el registro de aplicación

1. En Azure Portal, ve a "Microsoft Entra ID" > "Registros de aplicaciones"
2. Haz clic en "Nuevo registro"
3. Configura:
   - **Nombre**: E-Commerce Tech Frontend
   - **Tipos de cuenta admitidos**: "Cuentas en cualquier directorio organizativo y cuentas personales de Microsoft"
   - **URI de redirección**: 
     - Desarrollo: `http://localhost:4200`
     - Producción: `https://tu-usuario.github.io/ecommerce-tech/`
4. Haz clic en "Registrar"

### 2.2 Configurar autenticación

1. En la aplicación registrada, ve a "Autenticación"
2. Agrega plataformas:
   - **Plataforma de aplicaciones de una sola página (SPA)**
   - **URI de redirección**: `http://localhost:4200` (desarrollo)
   - **URI de redirección**: `https://tu-usuario.github.io/ecommerce-tech/` (producción)
3. Marca "Tokens de ID" (opcional, para obtener información del usuario)
4. Guarda los cambios

### 2.3 Configurar permisos de API

1. Ve a "Permisos de API"
2. Haz clic en "Agregar un permiso" > "Mis API"
3. Si es la primera vez, crea una API:
   - **Nombre**: E-Commerce Tech API
   - **URI de ID de aplicación**: `api://ecommerce-tech-api`
4. Agrega permisos:
   - **Acceso de usuario**: `access_as_user`
5. Haz clic en "Conceder consentimiento de administrador para [tu organización]"

### 2.4 Crear Roles de Aplicación

1. Ve a "Manifesto" de la aplicación
2. Agrega roles en la sección `appRoles`:

```json
"appRoles": [
  {
    "allowedMemberTypes": ["User"],
    "description": "Administradores con acceso completo",
    "displayName": "Admin",
    "id": "GUID-UNICO-ADMIN",
    "isEnabled": true,
    "origin": "Application",
    "value": "admin"
  },
  {
    "allowedMemberTypes": ["User"],
    "description": "Clientes del e-commerce",
    "displayName": "Customer",
    "id": "GUID-UNICO-CUSTOMER",
    "isEnabled": true,
    "origin": "Application",
    "value": "customer"
  },
  {
    "allowedMemberTypes": ["User"],
    "description": "Personal del e-commerce",
    "displayName": "Staff",
    "id": "GUID-UNICO-STAFF",
    "isEnabled": true,
    "origin": "Application",
    "value": "staff"
  }
]
```

3. Genera GUIDs únicos para cada rol (puedes usar `New-Guid` en PowerShell o herramientas online)
4. Haz clic en "Guardar"

## 3. Obtener Valores de Configuración

### 3.1 Valores necesarios para el Frontend

En la página "Información general" de la aplicación registrada:

- **ID de cliente (Application ID)**: `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`
- **ID de directorio (Tenant ID)**: `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`

### 3.2 Valores necesarios para el Backend

1. Ve a "Certificados y secretos"
2. Haz clic en "Nuevo secreto de cliente"
3. Nombre: "Backend Secret"
4. Expira: 12 meses (o tu preferencia)
5. Haz clic en "Agregar"
6. **Copia el valor del secreto** (solo se muestra una vez)

## 4. Crear Usuarios de Prueba

### 4.1 Crear usuarios

1. Ve a "Usuarios" > "Todos los usuarios"
2. Haz clic en "Nuevo usuario" > "Crear nuevo usuario"
3. Para cada rol, crea un usuario:

**Usuario Admin:**
- Nombre: Admin User
- Nombre de usuario: admin@tu-dominio.onmicrosoft.com
- Contraseña: [Genera una segura]
- Roles: Asigna el rol "Admin" de la aplicación

**Usuario Customer:**
- Nombre: Customer User
- Nombre de usuario: customer@tu-dominio.onmicrosoft.com
- Contraseña: [Genera una segura]
- Roles: Asigna el rol "Customer" de la aplicación

**Usuario Staff:**
- Nombre: Staff User
- Nombre de usuario: staff@tu-dominio.onmicrosoft.com
- Contraseña: [Genera una segura]
- Roles: Asigna el rol "Staff" de la aplicación

### 4.2 Asignar roles de aplicación a usuarios

1. Ve al usuario creado
2. Haz clic en "Asignaciones de roles"
3. Haz clic en "Agregar asignación"
4. Selecciona el rol correspondiente
5. Guarda

## 5. Actualizar Configuración del Frontend

Edita `frontend/src/environments/environment.ts`:

```typescript
export const environment = {
  production: false,
  apiGatewayUrl: 'https://YOUR_API_GATEWAY_URL.execute-api.region.amazonaws.com/prod',
  productsUrl: 'http://localhost:8081/api/products',
  categoriesUrl: 'http://localhost:8082/api/categories',
  usersUrl: 'http://localhost:8083/api/users',
  ordersUrl: 'http://localhost:8084/api/orders',
  
  msalConfig: {
    auth: {
      clientId: 'TU_CLIENT_ID', // Reemplazar con el ID de cliente
      authority: 'https://login.microsoftonline.com/TU_TENANT_ID', // Reemplazar con el ID de tenant
      redirectUri: 'http://localhost:4200',
      postLogoutRedirectUri: 'http://localhost:4200'
    },
    cache: {
      cacheLocation: 'localStorage',
      storeAuthStateInCookie: false
    }
  },
  
  enableAuth: true,
  scopes: ['api://ecommerce-tech-api/access_as_user']
};
```

## 6. Actualizar Configuración del Backend

Edita `application.properties` en cada microservicio:

```properties
# Azure AD Configuration
spring.security.oauth2.resourceserver.jwt.issuer-uri=https://login.microsoftonline.com/TU_TENANT_ID/v2.0
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://login.microsoftonline.com/TU_TENANT_ID/discovery/v2.0/keys

# CORS Configuration
cors.allowed-origins=http://localhost:4200,https://tu-usuario.github.io/ecommerce-tech/
```

## 7. Configuración de Producción

Para producción en GitHub Pages:

1. Actualiza `frontend/src/environments/environment.prod.ts`:
```typescript
export const environment = {
  production: true,
  apiGatewayUrl: 'https://YOUR_API_GATEWAY_URL.execute-api.region.amazonaws.com/prod',
  // ... otras configuraciones
};
```

2. Actualiza el URI de redirección en Azure AD para incluir la URL de GitHub Pages

## 8. Verificar Configuración

### 8.1 Probar autenticación local

1. Inicia los microservicios backend
2. Inicia el frontend: `npm start`
3. Ve a `http://localhost:4200`
4. Haz clic en "Login"
5. Deberías ver la pantalla de login de Microsoft
6. Ingresa con uno de los usuarios de prueba
7. Verifica que los roles se asignen correctamente

### 8.2 Verificar roles en el token

1. Después del login, abre las DevTools del navegador
2. Ve a "Application" > "Local Storage"
3. Busca el token de acceso
4. Decodifica el token (puedes usar jwt.io)
5. Verifica que el claim `roles` contenga el rol correcto

## 9. Solución de Problemas

### Error AADSTS50011: La dirección URL de respuesta no coincide

- Verifica que el URI de redirección en Azure AD coincida exactamente con la URL de tu aplicación
- Incluye la barra diagonal final si es necesario

### Error AADSTS65001: El usuario debe consentir el uso de la aplicación

- Ve a "Permisos de API" y haz clic en "Conceder consentimiento de administrador"
- O agrega `prompt: 'consent'` en la configuración de MSAL

### Los roles no aparecen en el token

- Verifica que los roles estén configurados en el Manifesto
- Asegúrate de que el usuario tenga asignado el rol de aplicación
- Verifica que el token sea un token de ID (no solo access token)

### Error CORS en backend

- Verifica que la URL del frontend esté en `cors.allowed-origins`
- Asegúrate de que no haya barra diagonal extra

## 10. Recursos Útiles

- [Documentación de MSAL Angular](https://github.com/AzureAD/microsoft-authentication-library-for-js/tree/dev/lib/msal-angular)
- [Documentación de Spring Security OAuth2](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Azure AD App Roles](https://learn.microsoft.com/en-us/azure/active-directory/develop/howto-add-app-roles-in-azure-ad-apps)
