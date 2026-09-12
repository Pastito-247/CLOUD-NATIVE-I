# Despliegue del Backend a AWS EC2

Guía para publicar los 4 microservicios Spring Boot en **una sola instancia EC2**,
usando JARs compilados en tu PC y servicios `systemd` para que corran como daemons.

- **Frontend**: sigue local (`http://localhost:4200`) llamando directo a la IP del EC2.
- **Backend**: 4 servicios (8081-8084) en 1 instancia Ubuntu.
- **AUTH**: los servicios validan JWT contra Azure AD (mismo tenant configurado localmente).

---

## Arquitectura resultante

```
Tu PC (Windows)
├── Frontend Angular  -> http://localhost:4200  (apunta a IP_EC2:808X)
└── scp los 4 .jar

EC2 (Ubuntu 22.04) - sistema systemd
├── products-service   (8081)
├── categories-service (8082)
├── users-service      (8083)  <- arregla el puerto vacio via SERVER_PORT
└── orders-service     (8084)
```

---

## Prerrequisitos

- AWS CLI **no es necesario** (todo se hace por consola AWS + SSH/scp).
- Tu `.pem` de key pair (ej: `tuki-key.pem`).
- Maven 3.9+ y JDK 17 en tu PC.
- Cambiar `IP_EC2` y `TU_KEY` por tus valores reales en cada comando.

---

## Fase 1 — Compilar los JARs (en tu PC, PowerShell)

```powershell
cd backend\products-service
mvn clean package -DskipTests
cd ..\categories-service
mvn clean package -DskipTests
cd ..\users-service
mvn clean package -DskipTests
cd ..\orders-service
mvn clean package -DskipTests
```

JARs generados (negrita = exacto):

| Servicio | JAR |
|---|---|
| Products | `backend\products-service\target\products-service-1.0.0.jar` |
| Categories | `backend\categories-service\target\categories-service-1.0.0.jar` |
| Users | `backend\users-service\target\users-service-1.0.0.jar` |
| Orders | `backend\orders-service\target\orders-service-1.0.0.jar` |

Verifica que existan los 4 antes de seguir.

---

## Fase 2 — Crear la instancia EC2 (Consola AWS)

1. **EC2 → Launch Instance**.
2. **Name**: `tuki-backend`.
3. **AMI**: Ubuntu Server 22.04 LTS (64-bit x86).
4. **Tipo**: `t3.micro` (2 GB RAM, recomendado). Si usas free tier `t2.micro` (1 GB),
   ajusta el heap como se indica en Fase 5.
5. **Key pair**: crea una nueva o reutiliza (`tuki-key`).
6. **Network settings → Edit**:
   - **Security group name**: `tuki-sg`.
   - Reglas de entrada:
     | Tipo | Puerto | Origen | Nota |
     |---|---|---|---|
     | SSH | 22 | Tu IP | `Meine IP`/`Mi IP` |
     | Custom TCP | 8081 | 0.0.0.0/0 | products |
     | Custom TCP | 8082 | 0.0.0.0/0 | categories |
     | Custom TCP | 8083 | 0.0.0.0/0 | users |
     | Custom TCP | 8084 | 0.0.0.0/0 | orders |
7. **Launch instance**.

> ⚠️ Para desarrollo puedes abrir 8081-8084 a `0.0.0.0/0` (los endpoints públicos no requieren token).
> En producción restringe a la IP del frontend o usa API Gateway.

8. **Elastic IP (recomendado)**: EC2 → Elastic IPs → Allocate → Associate con tu instancia.
   Así la IP no cambia al reiniciar la instancia. Anota la IP pública.

---

## Fase 3 — Conectar y preparar la instancia

Desde PowerShell en la carpeta donde tienes el `.pem`:

```powershell
ssh -i TU_KEY.pem ubuntu@IP_EC2
```

Primera vez si te queja del permiso del archivo (Windows): ejecuta en la carpeta del `.pem`:

```powershell
icacls TU_KEY.pem /inheritance:r
icacls TU_KEY.pem /grant:r "$($env:USERNAME):(R)"
```

Dentro del EC2:

```bash
sudo apt update
sudo apt install -y openjdk-17-jre-headless
java -version   # debe mostrar 17.x
mkdir -p ~/apps
```

---

## Fase 4 — Subir los JARs

Desde **PowerShell en la raíz del proyecto** (tu PC, no en el EC2):

```powershell
scp -i TU_KEY.pem backend/products-service/target/products-service-1.0.0.jar   ubuntu@IP_EC2:~/apps/
scp -i TU_KEY.pem backend/categories-service/target/categories-service-1.0.0.jar ubuntu@IP_EC2:~/apps/
scp -i TU_KEY.pem backend/users-service/target/users-service-1.0.0.jar           ubuntu@IP_EC2:~/apps/
scp -i TU_KEY.pem backend/orders-service/target/orders-service-1.0.0.jar         ubuntu@IP_EC2:~/apps/
```

Verifica dentro del EC2:

```bash
ls -lh ~/apps
```

---

## Fase 5 — Crear los servicios systemd

Conectado al EC2, crea los 4 unit files. Las `Environment=` **sobreescriben** los
`application.properties` (Spring Boot da prioridad a variables de entorno), lo que
aprovechamos para:

- Forzar el puerto 8083 en users-service (su `server.port=` está vacío en el repo).
- Fijar la `audience` (`api://tuki-tech-api`): el access token de Entra AD debe traer
  ese valor en el claim `aud`, o el servicio responde `401 invalid_token`.
- Pasar el `issuer` correcto a los 4 servicios (same tenant de Azure AD).

### 1) products-service

```bash
sudo tee /etc/systemd/system/tuki-products.service > /dev/null <<'EOF'
[Unit]
Description=Products Service (Tuki-Tech)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu/apps
Environment=JAVA_TOOL_OPTIONS=-Xmx256m
Environment=SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=https://login.microsoftonline.com/e3e92dfe-ea59-4c42-a539-90e6fea570b6/v2.0
Environment=SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUDIENCES=api://tuki-tech-api
Environment=CORS_ALLOWED_ORIGINS=http://localhost:4200,https://pastito-247.github.io
ExecStart=/usr/bin/java -jar /home/ubuntu/apps/products-service-1.0.0.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF
```

### 2) categories-service

```bash
sudo tee /etc/systemd/system/tuki-categories.service > /dev/null <<'EOF'
[Unit]
Description=Categories Service (Tuki-Tech)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu/apps
Environment=JAVA_TOOL_OPTIONS=-Xmx256m
Environment=SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=https://login.microsoftonline.com/e3e92dfe-ea59-4c42-a539-90e6fea570b6/v2.0
Environment=SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUDIENCES=api://tuki-tech-api
Environment=CORS_ALLOWED_ORIGINS=http://localhost:4200,https://pastito-247.github.io
ExecStart=/usr/bin/java -jar /home/ubuntu/apps/categories-service-1.0.0.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF
```

### 3) users-service (con SERVER_PORT=8083 y audience forzada)

```bash
sudo tee /etc/systemd/system/tuki-users.service > /dev/null <<'EOF'
[Unit]
Description=Users Service (Tuki-Tech)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu/apps
Environment=JAVA_TOOL_OPTIONS=-Xmx256m
Environment=SERVER_PORT=8083
Environment=SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=https://login.microsoftonline.com/e3e92dfe-ea59-4c42-a539-90e6fea570b6/v2.0
Environment=SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUDIENCES=api://tuki-tech-api
Environment=CORS_ALLOWED_ORIGINS=http://localhost:4200,https://pastito-247.github.io
ExecStart=/usr/bin/java -jar /home/ubuntu/apps/users-service-1.0.0.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF
```

### 4) orders-service

```bash
sudo tee /etc/systemd/system/tuki-orders.service > /dev/null <<'EOF'
[Unit]
Description=Orders Service (Tuki-Tech)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu/apps
Environment=JAVA_TOOL_OPTIONS=-Xmx256m
Environment=SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=https://login.microsoftonline.com/e3e92dfe-ea59-4c42-a539-90e6fea570b6/v2.0
Environment=SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUDIENCES=api://tuki-tech-api
Environment=CORS_ALLOWED_ORIGINS=http://localhost:4200,https://pastito-247.github.io
ExecStart=/usr/bin/java -jar /home/ubuntu/apps/orders-service-1.0.0.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF
```

### Iniciar y habilitar los 4

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now tuki-products tuki-categories tuki-users tuki-orders
```

> Si usas `t2.micro` (1 GB RAM), baja el heap a `-Xmx192m` en los 4 units
> o solo 2 servicios arrancarán bien a la vez.

---

## Fase 6 — Verificar

En el EC2:

```bash
sudo systemctl status tuki-products --no-pager -l
ss -tlnp | grep -E '808[1-4]'
```

Deberías ver `java` escuchando en 8081, 8082, 8083 y 8084.

Desde tu PC (PowerShell):

```powershell
curl http://IP_EC2:8081/api/public/products
curl http://IP_EC2:8082/api/public/categories
```

Respuesta esperada: un JSON `[]` (sin datos aún) o `{"products": []}`/`{"categories": []}`
según el contrato del controller — lo importante es que NO sea `Connection refused`
ni `403 Forbidden`.

Logs en vivo:

```bash
sudo journalctl -u tuki-products -f
sudo journalctl -u tuki-users -f    # especificamente para ver si valida JWT bien
```

---

## Fase 7 — Apuntar el frontend local al EC2

El dev server regenera `environment.ts` desde `.env` cada `npm start`
(prestart). Después de arrancarlo, edita `frontend/src/environments/environment.ts`
y reemplaza los puertos `localhost` por la IP del EC2:

```typescript
productsUrl:   'http://IP_EC2:8081/api/products',
categoriesUrl: 'http://IP_EC2:8082/api/categories',
usersUrl:      'http://IP_EC2:8083/api/users',
ordersUrl:     'http://IP_EC2:8084/api/orders',
```

Y recarga `ng serve`. La autenticación sigue funcionando porque los JWT
los sigue validando el backend contra el mismo tenant de Azure AD.

---

## Fase 8 — Operación (día a día)

```bash
# Ver estado de todo
systemctl status 'tuki-*'

# Reiniciar un servicio tras cambiar config
sudo systemctl restart tuki-products

# Ver última hora de logs
journalctl -u tuki-products --since "1 hour ago" -p warning

# Detener todo
sudo systemctl stop tuki-products tuki-categories tuki-users tuki-orders
```

**Actualizar un servicio** (nueva versión):

1. En tu PC: `mvn clean package -DskipTests`.
2. `scp -i TU_KEY.pem backend/products-service/target/products-service-1.0.0.jar ubuntu@IP_EC2:~/apps/`
3. En el EC2: `sudo systemctl restart tuki-products`.

---

## Notas importantes

- **Datos en memoria**: todos los servicios usan **H2 in-memory** con `create-drop`.
  Al reiniciar la instancia o el servicio, los datos se pierden. Para persistir en
  producción deberías migrar a RDS MySQL (ver README) o cambiar el properties.

- **users-service**: ya quedó arreglado en el repo (`server.port=8083`, issuer y
  audience en `application.properties`). La Fase 5 fuerza además el puerto y la
  audience por variables de entorno como respaldo.

- **CORS**: si publicas el frontend en GitHub Pages, agrega tu URL
  (ej: `https://pastito-247.github.io`) en `CORS_ALLOWED_ORIGINS` de los 4 units
  (separada por coma) y `systemctl restart` cada servicio. Ya se quitaron los
  `@CrossOrigin("*")` de los controllers: todo el CORS sale del `SecurityConfig`
  (`cors.allowed-origins` / `CORS_ALLOWED_ORIGINS`).

- **Costos**: `t3.micro` no entra en free tier (≈ US$0.0095/hora). `t2.micro`
  sí es free tier (12 meses), pero usa menos RAM.