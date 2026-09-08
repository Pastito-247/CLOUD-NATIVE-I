# Guía Completa de Configuración de Azure AD (EntraID) para Tuki-Tech

Esta guía explica paso a paso cómo configurar Azure AD para la autenticación del proyecto **Tuki-Tech**.

> **Nota:** Esta guía funciona perfectamente con la suscripción de estudiante de Azure. Azure AD (EntraID) es gratuito para hasta 50,000 autenticaciones mensuales.

## 📋 Tabla de Contenidos

1. [Crear Tenant de Azure AD](#1-crear-tenant-de-azure-ad)
2. [Registrar Aplicación en Azure AD](#2-registrar-aplicación-en-azure-ad)
3. [Configurar Roles de Aplicación](#3-configurar-roles-de-aplicación)
4. [Exponer API y Configurar Scopes](#4-exponer-api-y-configurar-scopes)
5. [Crear Usuarios de Prueba](#5-crear-usuarios-de-prueba)
6. [Asignar Roles a Usuarios](#6-asignar-roles-a-usuarios)
7. [Obtener Valores de Configuración](#7-obtener-valores-de-configuración)
8. [Configurar Frontend](#8-configurar-frontend)
9. [Configurar Backend](#9-configurar-backend)
10. [Verificar Configuración](#10-verificar-configuración)
11. [Solución de Problemas](#11-solución-de-problemas)

---

## 1. Crear Tenant de Azure AD

Si ya tienes un tenant de Azure AD (incluido el que viene con tu suscripción de estudiante), puedes saltar este paso.

### Pasos:

1. Ve a [Azure Portal](https://portal.azure.com)
2. En el buscador, escribe "Azure Active Directory" o "Microsoft Entra ID"
3. Si no tienes un tenant, haz clic en "Crear inquilino"
4. Configura:
   - **Tipo de organización**: "Solo en la nube" (recomendado para pruebas)
   - **Nombre de organización**: `tuki-tech-org` (o tu preferencia)
   - **Dominio inicial**: `tuki-tech.onmicrosoft.com` (o tu preferencia)
   - **País o región**: Tu país
5. Haz clic en "Siguiente" y completa la configuración
6. Haz clic en "Crear"

> **Nota:** El proceso de creación puede tardar unos minutos. Una vez creado, asegúrate de cambiar al nuevo tenant en Azure Portal (haz clic en tu perfil > "Cambiar directorio").

---

## 2. Registrar Aplicación en Azure AD

### 2.1 Crear el registro de aplicación

1. En Azure Portal, ve a **Microsoft Entra ID** > **Registros de aplicaciones**
2. Haz clic en **Nuevo registro**
3. Configura:
   - **Nombre**: `Tuki-Tech Frontend`
   - **Tipos de cuenta admitidos**: "Cuentas en cualquier directorio organizativo y cuentas personales de Microsoft"
   - **URI de redirección**: 
     - Tipo: **Aplicación de una sola página (SPA)**
     - URL: `http://localhost:4200`
4. Haz clic en **Registrar**

### 2.2 Configurar autenticación adicional

1. En la aplicación registrada, ve a **Autenticación**
2. Verifica que la plataforma **Aplicación de una sola página (SPA)** esté configurada
3. Agrega URI de redirección adicionales:
   - Para producción: `https://tu-usuario.github.io/tuki-tech/`
4. En **Tokens de ID configurables**:
   - Marca las casillas para emitir tokens de ID
5. Haz clic en **Guardar**

> **IMPORTANTE:** Para producción, actualiza el URI de redirección con tu URL real de GitHub Pages.

---

## 3. Configurar Roles de Aplicación

Los roles se definen en el **Manifesto** de la aplicación y aparecerán en el claim `roles` del token JWT.

### Pasos:

1. En tu aplicación registrada, ve a **Manifesto**
2. Busca la sección `"appRoles"` (estará vacía o con `[]`)
3. Reemplaza con el siguiente JSON:

```json
"appRoles": [
  {
    "allowedMemberTypes": ["User"],
    "description": "Administradores con acceso completo al sistema",
    "displayName": "Admin",
    "id": "54283e78-6c7e-436d-82a1-3ad1e5d1e53e",
    "isEnabled": true,
    "origin": "Application",
    "value": "admin"
  },
  {
    "allowedMemberTypes": ["User"],
    "description": "Clientes regulares del e-commerce",
    "displayName": "Customer",
    "id": "2462dbad-6057-4221-819c-c494bf4a09ce",
    "isEnabled": true,
    "origin": "Application",
    "value": "customer"
  },
  {
    "allowedMemberTypes": ["User"],
    "description": "Personal del e-commerce con acceso limitado",
    "displayName": "Staff",
    "id": "e662acbf-a2f1-4a4b-8f26-a19d99a07483",
    "isEnabled": true,
    "origin": "Application",
    "value": "staff"
  }
]
```

> **Nota:** Los GUIDs ya están generados. Puedes usar estos directamente o generar los tuyos propios con `New-Guid` en PowerShell.

4. Haz clic en **Guardar**

> **IMPORTANTE:** Si el guardado falla, verifica que el JSON sea válido (comas, comillas, etc.).

---

## 4. Exponer API y Configurar Scopes

### 4.1 Exponer la API

1. En tu aplicación, ve a **Exponer una API**
2. En **URI de ID de aplicación**, haz clic en **Establecer**
3. Configura:
   - **URI de ID de aplicación**: `api://tuki-tech-api`
4. Haz clic en **Guardar**

### 4.2 Agregar scope

1. En la misma sección, haz clic en **Agregar un ámbito**
2. Configura:
   - **Nombre del ámbito**: `access_as_user`
   - **Quién puede dar su consentimiento**: "Administradores y usuarios"
   - **Nombre para mostrar del consentimiento de administrador**: "Access Tuki-Tech API"
   - **Descripción del consentimiento de administrador**: "Allows the app to access Tuki-Tech API as the signed-in user"
   - **Nombre para mostrar del consentimiento de usuario**: "Access Tuki-Tech API"
   - **Descripción del consentimiento de usuario**: "Allow the application to access Tuki-Tech API on your behalf"
3. Haz clic en **Agregar ámbito**

**El scope completo será:** `api://tuki-tech-api/access_as_user`

### 4.3 Configurar permisos de API

1. Ve a **Permisos de API**
2. Haz clic en **Agregar un permiso**
3. Selecciona **Mis API**
4. Selecciona tu aplicación "Tuki-Tech Frontend"
5. Marca el scope `access_as_user`
6. Haz clic en **Agregar permisos**
7. Haz clic en **Conceder consentimiento de administrador para [tu organización]**

> **IMPORTANTE:** El consentimiento de administrador es necesario para que los usuarios no tengan que aprobar permisos manualmente.

---

## 5. Crear Usuarios de Prueba

### 5.1 Crear usuario Admin

1. Ve a **Microsoft Entra ID** > **Usuarios** > **Todos los usuarios**
2. Haz clic en **Nuevo usuario** > **Crear nuevo usuario**
3. Configura:
   - **Nombre principal de usuario**: `admin@tuki-tech.onmicrosoft.com` (reemplaza con tu dominio)
   - **Nombre para mostrar**: `Admin Tuki-Tech`
   - **Contraseña**: Genera una segura (anótala)
   - **Roles**: "Usuario" (por defecto)
   - **Licencias**: Ninguna
4. Haz clic en **Revisar + crear**
5. Haz clic en **Crear**

### 5.2 Crear usuario Customer

1. Repite el proceso anterior con:
   - **Nombre principal de usuario**: `customer@tuki-tech.onmicrosoft.com`
   - **Nombre para mostrar**: `Customer Tuki-Tech`
   - **Contraseña**: Genera una segura (anótala)

### 5.3 Crear usuario Staff

1. Repite el proceso anterior con:
   - **Nombre principal de usuario**: `staff@tuki-tech.onmicrosoft.com`
   - **Nombre para mostrar**: `Staff Tuki-Tech`
   - **Contraseña**: Genera una segura (anótala)

> **IMPORTANTE:** Anota las credenciales de cada usuario en un lugar seguro.

---

## 6. Asignar Roles a Usuarios

Los roles de aplicación se asignan a través de **Enterprise Applications**, no directamente desde el usuario.

### 6.1 Buscar la Enterprise Application

1. Ve a **Microsoft Entra ID** > **Aplicaciones empresariales**
2. En el buscador, busca "Tuki-Tech Frontend"
3. Haz clic en la aplicación

### 6.2 Asignar rol Admin

1. En la aplicación, ve a **Usuarios y grupos**
2. Haz clic en **Agregar usuario/grupo**
3. En **Usuarios**, busca y selecciona `Admin Tuki-Tech`
4. Haz clic en **Seleccionar**
5. En **Seleccionar un rol**, selecciona **Admin**
6. Haz clic en **Asignar**

### 6.3 Asignar rol Customer

1. Repite el proceso anterior:
   - Usuario: `Customer Tuki-Tech`
   - Rol: **Customer**

### 6.4 Asignar rol Staff

1. Repite el proceso anterior:
   - Usuario: `Staff Tuki-Tech`
   - Rol: **Staff**

> **IMPORTANTE:** Los roles solo aparecerán en el token JWT después de asignarlos aquí. Si no haces este paso, los usuarios no tendrán roles en el token.

---

## 7. Obtener Valores de Configuración

### 7.1 Valores para el Frontend

1. Ve a tu aplicación registrada (**Registros de aplicaciones**)
2. En **Información general**, copia:
   - **ID de cliente (Application ID)**: `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`
   - **ID de directorio (Tenant ID)**: `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`

### 7.2 Valores para el Backend

El backend solo necesita el **Tenant ID** para validar los tokens JWT. No necesita un client secret porque es un Resource Server que valida tokens emitidos por Azure AD.

**Valores necesarios:**
- **Tenant ID**: Ya obtenido arriba
- **Issuer URI**: `https://login.microsoftonline.com/TU_TENANT_ID/v2.0`
- **JWK Set URI**: `https://login.microsoftonline.com/TU_TENANT_ID/discovery/v2.0/keys`

---

## 8. Configurar Frontend

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
      clientId: 'TU_CLIENT_ID_AQUI', // Reemplazar con tu Application ID
      authority: 'https://login.microsoftonline.com/TU_TENANT_ID_AQUI', // Reemplazar con tu Tenant ID
      redirectUri: 'http://localhost:4200',
      postLogoutRedirectUri: 'http://localhost:4200'
    },
    cache: {
      cacheLocation: 'localStorage',
      storeAuthStateInCookie: false
    }
  },
  
  enableAuth: true,
  scopes: ['api://tuki-tech-api/access_as_user']
};
```

### Para producción:

Edita `frontend/src/environments/environment.prod.ts`:

```typescript
export const environment = {
  production: true,
  apiGatewayUrl: 'https://YOUR_API_GATEWAY_URL.execute-api.region.amazonaws.com/prod',
  productsUrl: 'https://YOUR_API_GATEWAY_URL.execute-api.region.amazonaws.com/prod/products',
  categoriesUrl: 'https://YOUR_API_GATEWAY_URL.execute-api.region.amazonaws.com/prod/categories',
  usersUrl: 'https://YOUR_API_GATEWAY_URL.execute-api.region.amazonaws.com/prod/users',
  ordersUrl: 'https://YOUR_API_GATEWAY_URL.execute-api.region.amazonaws.com/prod/orders',
  
  msalConfig: {
    auth: {
      clientId: 'TU_CLIENT_ID_AQUI',
      authority: 'https://login.microsoftonline.com/TU_TENANT_ID_AQUI',
      redirectUri: 'https://tu-usuario.github.io/tuki-tech/',
      postLogoutRedirectUri: 'https://tu-usuario.github.io/tuki-tech/'
    },
    cache: {
      cacheLocation: 'localStorage',
      storeAuthStateInCookie: false
    }
  },
  
  enableAuth: true,
  scopes: ['api://tuki-tech-api/access_as_user']
};
```

> **IMPORTANTE:** No olvides agregar la URL de producción como URI de redirección en Azure AD.

---

## 9. Configurar Backend

Edita `src/main/resources/application.properties` en **CADA** microservicio (products, categories, users, orders):

```properties
# Azure AD Configuration
spring.security.oauth2.resourceserver.jwt.issuer-uri=https://login.microsoftonline.com/TU_TENANT_ID_AQUI/v2.0
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://login.microsoftonline.com/TU_TENANT_ID_AQUI/discovery/v2.0/keys

# CORS Configuration
cors.allowed-origins=http://localhost:4200,https://tu-usuario.github.io/tuki-tech/

# Database Configuration (H2 para local, MySQL para producción)
spring.datasource.url=jdbc:h2:mem:products_db
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

# JPA Configuration
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect

# Logging
logging.level.com.ecommerce=DEBUG
```

> **IMPORTANTE:** Reemplaza `TU_TENANT_ID_AQUI` con tu Tenant ID real.

---

## 10. Verificar Configuración

### 10.1 Probar autenticación local

1. **Iniciar los microservicios backend:**
   ```bash
   # Terminal 1
   cd backend/products-service
   mvn spring-boot:run
   
   # Terminal 2
   cd backend/categories-service
   mvn spring-boot:run
   
   # Terminal 3
   cd backend/users-service
   mvn spring-boot:run
   
   # Terminal 4
   cd backend/orders-service
   mvn spring-boot:run
   ```

2. **Iniciar el frontend:**
   ```bash
   cd frontend
   npm start
   ```

3. **Probar login:**
   - Ve a `http://localhost:4200`
   - Haz clic en "Login"
   - Deberías ver la pantalla de login de Microsoft
   - Ingresa con `admin@tuki-tech.onmicrosoft.com`
   - Verifica que el login sea exitoso

### 10.2 Verificar roles en el token

1. Después del login, abre las DevTools del navegador (F12)
2. Ve a **Application** > **Local Storage**
3. Busca las claves que comienzan con `msal` (tokens almacenados por MSAL)
4. Copia el token de acceso (access token)
5. Ve a [https://jwt.io/](https://jwt.io/)
6. Pega el token para decodificarlo
7. Verifica que el claim `roles` contenga el rol correcto:
   ```json
   {
     "roles": ["admin"]
   }
   ```

### 10.3 Probar APIs públicas

```bash
curl http://localhost:8081/api/products/public
curl http://localhost:8082/api/categories/public
```

Deberías recibir una respuesta JSON con datos.

### 10.4 Probar APIs privadas

```bash
# Obtén el access token desde el frontend (como en el paso 10.2)
curl -H "Authorization: Bearer TU_ACCESS_TOKEN" http://localhost:8081/api/products
```

Deberías recibir una respuesta JSON con datos. Si el token es inválido, recibirás un 401 Unauthorized.

---

## 11. Solución de Problemas

### Error: AADSTS50011 - La dirección URL de respuesta no coincide

**Causa:** El URI de redirección en Azure AD no coincide exactamente con la URL de tu aplicación.

**Solución:**
1. Ve a **Autenticación** en tu aplicación Azure AD
2. Verifica que el URI de redirección coincida exactamente:
   - Desarrollo: `http://localhost:4200` (sin barra final)
   - Producción: `https://tu-usuario.github.io/tuki-tech/` (con barra final)
3. Asegúrate de que no haya espacios extra
4. Guarda y vuelve a probar

### Error: AADSTS65001 - El usuario debe consentir el uso de la aplicación

**Causa:** No se ha concedido consentimiento de administrador para los permisos de API.

**Solución:**
1. Ve a **Permisos de API**
2. Haz clic en **Conceder consentimiento de administrador para [tu organización]**
3. Confirma el consentimiento
4. Vuelve a probar

### Error: Los roles no aparecen en el token

**Causa:** Los roles no están asignados correctamente al usuario.

**Solución:**
1. Verifica que los roles estén configurados en el **Manifesto**
2. Ve a **Aplicaciones empresariales** > tu aplicación > **Usuarios y grupos**
3. Verifica que el usuario tenga el rol asignado
4. Si no está asignado, asígnalo siguiendo el paso 6
5. Cierra sesión y vuelve a iniciar sesión (los roles se actualizan al obtener un nuevo token)

### Error: 401 Unauthorized en backend

**Causa:** El token JWT no es válido o el Tenant ID está incorrecto.

**Solución:**
1. Verifica que el Tenant ID en `application.properties` sea correcto
2. Verifica que el token no haya expirado (los tokens de Azure AD duran ~1 hora)
3. Decodifica el token y verifica el claim `iss` (issuer):
   ```json
   "iss": "https://login.microsoftonline.com/TU_TENANT_ID/v2.0"
   ```
4. Asegúrate de que coincida con el configurado en el backend

### Error: CORS en backend

**Causa:** La URL del frontend no está en la lista de orígenes permitidos.

**Solución:**
1. Verifica `cors.allowed-origins` en `application.properties`
2. Asegúrate de que la URL coincida exactamente:
   - `http://localhost:4200` (sin barra final)
   - `https://tu-usuario.github.io/tuki-tech/` (con barra final)
3. Reinicia el microservicio

### Error: "No se puede conectar al backend"

**Causa:** Los microservicios no están iniciados o los puertos están incorrectos.

**Solución:**
1. Verifica que todos los microservicios estén iniciados
2. Verifica los puertos:
   - Products: 8081
   - Categories: 8082
   - Users: 8083
   - Orders: 8084
3. Verifica que no haya otras aplicaciones usando estos puertos

---

## 📚 Recursos Útiles

- [Documentación de MSAL Angular](https://github.com/AzureAD/microsoft-authentication-library-for-js/tree/dev/lib/msal-angular)
- [Documentación de Spring Security OAuth2](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Azure AD App Roles](https://learn.microsoft.com/en-us/azure/active-directory/develop/howto-add-app-roles-in-azure-ad-apps)
- [Azure AD Quickstart](https://learn.microsoft.com/en-us/azure/active-directory/develop/quickstart-register-app)
- [JWT.io - Decodificador de tokens](https://jwt.io/)

---

## ✅ Checklist de Configuración

Antes de continuar con el despliegue, verifica:

- [ ] Tenant de Azure AD creado
- [ ] Aplicación "Tuki-Tech Frontend" registrada
- [ ] Plataforma SPA configurada con URI de redirección
- [ ] Roles (admin, customer, staff) configurados en Manifesto
- [ ] API expuesta con scope `access_as_user`
- [ ] Permisos de API concedidos
- [ ] Usuarios creados (admin, customer, staff)
- [ ] Roles asignados a usuarios vía Enterprise Applications
- [ ] Client ID y Tenant ID copiados
- [ ] Frontend configurado con Client ID y Tenant ID
- [ ] Backend configurado con Tenant ID
- [ ] Login local funciona correctamente
- [ ] Roles aparecen en el token JWT
- [ ] APIs públicas funcionan sin token
- [ ] APIs privadas funcionan con token

---

## 🎯 Siguientes Pasos

Una vez completada esta configuración:

1. **Desplegar frontend a GitHub Pages** (sigue el README)
2. **Desplegar backend a AWS EC2** (sigue el README)
3. **Configurar AWS API Gateway** con validación JWT
4. **Actualizar URIs de redirección** en Azure AD para producción
5. **Probar en producción**
