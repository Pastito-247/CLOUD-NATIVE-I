# Despliegue del Backend a AWS EC2

Guía para publicar los 6 microservicios Spring Boot en **una sola instancia EC2**,
usando JARs compilados en tu PC y servicios `systemd` para que corran como daemons.

- **Frontend**: sigue local (`http://localhost:4200`) llamando directo a la IP del EC2.
- **Backend**: 6 servicios (8081-8086) en 1 instancia Ubuntu.
- **Mensajería**: `payments-service` orquesta el flujo de pago (fanout) y
  `orders-service`/`users-service`/`products-service` consumen sus colas en un
  **RabbitMQ propio corriendo en el mismo EC2 (Docker, 1 nodo)**;
  `rabbitmq-admin-service` (8085) administra colas/exchanges vía REST.
- **AUTH**: los servicios validan JWT contra Azure AD (mismo tenant configurado localmente).

---

## Arquitectura resultante

```
Tu PC (Windows)
├── Frontend Angular  -> http://localhost:4200  (apunta a IP_EC2:808X)
└── scp los 6 .jar

EC2 (Ubuntu 22.04) - sistema systemd + Docker
├── rabbitmq (Docker, 1 nodo)  127.0.0.1:5672 / 127.0.0.1:15672
├── products-service   (8081)
├── categories-service (8082)
├── users-service      (8083)  <- arregla el puerto vacio via SERVER_PORT
├── orders-service     (8084)
├── rabbitmq-admin-service (8085)  <- administra el RabbitMQ local
└── payments-service   (8086)  <- API de pago + boleta PDF
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
cd ..\rabbitmq-admin-service
mvn clean package -DskipTests
cd ..\payments-service
mvn clean package -DskipTests
```

JARs generados (negrita = exacto):

| Servicio | JAR |
|---|---|
| Products | `backend\products-service\target\products-service-1.0.0.jar` |
| Categories | `backend\categories-service\target\categories-service-1.0.0.jar` |
| Users | `backend\users-service\target\users-service-1.0.0.jar` |
| Orders | `backend\orders-service\target\orders-service-1.0.0.jar` |
| RabbitMQ Admin | `backend\rabbitmq-admin-service\target\rabbitmq-admin-service-1.0.0.jar` |
| Payments | `backend\payments-service\target\payments-service-1.0.0.jar` |

Verifica que existan los 6 antes de seguir.

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
     | Custom TCP | 8085 | 0.0.0.0/0 | rabbitmq-admin |
     | Custom TCP | 8086 | 0.0.0.0/0 | payments |
7. **Launch instance**.

> ⚠️ Para desarrollo puedes abrir 8081-8086 a `0.0.0.0/0` (los endpoints públicos no requieren token).
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

## Fase 3.5 — RabbitMQ en el EC2 (Docker, 1 nodo, solo local)

El broker corre en la **misma instancia** que el backend, así que este se conecta
por `127.0.0.1:5672`. Los puertos se publican solo en loopback: **no abras
`5672`/`15672` en el Security Group**.

### Requisitos en el EC2 (Docker + swap)

Si la instancia no tiene Docker (`t3.micro`, 2 GB, ya con 6 JVMs):

```bash
# Swap de 2 GB para evitar OOM (la RAM libre es mínima)
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo "/swapfile none swap sw 0 0" | sudo tee -a /etc/fstab

# Docker Engine + Compose plugin
curl -fsSL https://get.docker.com -o /tmp/get-docker.sh
sudo sh /tmp/get-docker.sh
sudo usermod -aG docker ubuntu
sudo systemctl enable --now docker
```

> Tras `usermod`, cierra y vuelve a abrir la sesión SSH (o usa `sudo docker`)
> para que el usuario `ubuntu` tenga permisos sobre el daemon.

### Despliegue

Desde **PowerShell en la raíz del proyecto** (tu PC):

```powershell
.\scripts\deploy-rabbitmq-ec2.ps1 -Ec2Host IP_EC2 -ApplyBackendConfig
```

Ese script:

1. Verifica Docker en el EC2.
2. Copia `docker-compose.ec2.yml` como `~/rabbitmq/docker-compose.yml`.
3. Levanta el contenedor `rabbitmq` (imagen `rabbitmq:3.13-management`, volumen
   persistente, `restart: unless-stopped`).
4. Con `-ApplyBackendConfig`, escribe los drop-ins de systemd y reinicia los
   6 servicios para apuntar a `127.0.0.1:5672` (`tuki / tuki`, vhost `/`).

Equivalente manual dentro del EC2:

```bash
mkdir -p ~/rabbitmq && cd ~/rabbitmq
# copiar docker-compose.ec2.yml como docker-compose.yml
docker compose up -d
docker compose ps
curl -s -u tuki:tuki http://127.0.0.1:15672/api/overview
```

Management UI desde tu PC (opción directa, sin túnel):

1. En el Security Group agrega una regla **Custom TCP 15672** con origen **tu IP**.
2. Abre `http://IP_EC2:15672` → `tuki / tuki`.

Alternativa sin abrir puertos (túnel SSH de *forward*):

```powershell
ssh -i tuki-key.pem -L 15672:127.0.0.1:15672 ubuntu@IP_EC2
# abrir http://localhost:15672  ->  tuki / tuki
```

> En `t3.micro`/`t2.micro` el broker consume ~200-300 MB. Si notas presión de
> memoria, verifica `free -h`; los servicios Java ya usan `-Xmx256m`.

---

## Fase 4 — Subir los JARs

Desde **PowerShell en la raíz del proyecto** (tu PC, no en el EC2):

```powershell
scp -i TU_KEY.pem backend/products-service/target/products-service-1.0.0.jar   ubuntu@IP_EC2:~/apps/
scp -i TU_KEY.pem backend/categories-service/target/categories-service-1.0.0.jar ubuntu@IP_EC2:~/apps/
scp -i TU_KEY.pem backend/users-service/target/users-service-1.0.0.jar           ubuntu@IP_EC2:~/apps/
scp -i TU_KEY.pem backend/orders-service/target/orders-service-1.0.0.jar         ubuntu@IP_EC2:~/apps/
scp -i TU_KEY.pem backend/rabbitmq-admin-service/target/rabbitmq-admin-service-1.0.0.jar ubuntu@IP_EC2:~/apps/
scp -i TU_KEY.pem backend/payments-service/target/payments-service-1.0.0.jar     ubuntu@IP_EC2:~/apps/
```

Verifica dentro del EC2:

```bash
ls -lh ~/apps
```

---

## Fase 5 — Crear los servicios systemd

Conectado al EC2, crea los 6 unit files. Las `Environment=` **sobreescriben** los
`application.yml` (Spring Boot da prioridad a variables de entorno), lo que
aprovechamos para:

- Forzar el puerto 8083 en users-service.
- Fijar la `audience`: el access token de Entra AD trae en el claim `aud` el
  Application (client) ID (GUID `66c0f84b-...`), no siempre el Application ID URI
  (`api://tuki-tech-api`). Se aceptan **ambos** (lista separada por coma), o el
  servicio responde `401 invalid_token` con "The required audience is missing".
- Pasar el `issuer` correcto a los servicios (same tenant de Azure AD).
- Pasar las credenciales del **RabbitMQ local del EC2** (`RABBITMQ_*`) a products,
  categories, orders, users, payments y rabbitmq-admin. Los units de esta guía ya
  apuntan a `127.0.0.1:5672` (`tuki / tuki`, vhost `/`, SSL `false`); el admin
  además usa `RABBITMQ_MANAGEMENT_URL=http://127.0.0.1:15672`. Ajusta usuario y
  contraseña si los cambiaste en `docker-compose.ec2.yml`.

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
Environment=SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUDIENCES=api://tuki-tech-api,66c0f84b-3701-42fb-9189-3f4092cba201
Environment=CORS_ALLOWED_ORIGINS=http://localhost:4200,https://pastito-247.github.io
Environment=RABBITMQ_HOST=127.0.0.1
Environment=RABBITMQ_PORT=5672
Environment=RABBITMQ_SSL=false
Environment=RABBITMQ_USERNAME=tuki
Environment=RABBITMQ_PASSWORD=tuki
Environment=RABBITMQ_VHOST=/
Environment=RABBITMQ_MANAGEMENT_URL=http://127.0.0.1:15672
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
Environment=SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUDIENCES=api://tuki-tech-api,66c0f84b-3701-42fb-9189-3f4092cba201
Environment=CORS_ALLOWED_ORIGINS=http://localhost:4200,https://pastito-247.github.io
Environment=RABBITMQ_HOST=127.0.0.1
Environment=RABBITMQ_PORT=5672
Environment=RABBITMQ_SSL=false
Environment=RABBITMQ_USERNAME=tuki
Environment=RABBITMQ_PASSWORD=tuki
Environment=RABBITMQ_VHOST=/
Environment=RABBITMQ_MANAGEMENT_URL=http://127.0.0.1:15672
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
Environment=SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUDIENCES=api://tuki-tech-api,66c0f84b-3701-42fb-9189-3f4092cba201
Environment=CORS_ALLOWED_ORIGINS=http://localhost:4200,https://pastito-247.github.io
Environment=RABBITMQ_HOST=127.0.0.1
Environment=RABBITMQ_PORT=5672
Environment=RABBITMQ_SSL=false
Environment=RABBITMQ_USERNAME=tuki
Environment=RABBITMQ_PASSWORD=tuki
Environment=RABBITMQ_VHOST=/
Environment=RABBITMQ_MANAGEMENT_URL=http://127.0.0.1:15672
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
Environment=SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUDIENCES=api://tuki-tech-api,66c0f84b-3701-42fb-9189-3f4092cba201
Environment=CORS_ALLOWED_ORIGINS=http://localhost:4200,https://pastito-247.github.io
Environment=RABBITMQ_HOST=127.0.0.1
Environment=RABBITMQ_PORT=5672
Environment=RABBITMQ_SSL=false
Environment=RABBITMQ_USERNAME=tuki
Environment=RABBITMQ_PASSWORD=tuki
Environment=RABBITMQ_VHOST=/
Environment=RABBITMQ_MANAGEMENT_URL=http://127.0.0.1:15672
ExecStart=/usr/bin/java -jar /home/ubuntu/apps/orders-service-1.0.0.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF
```

### 5) rabbitmq-admin-service

```bash
sudo tee /etc/systemd/system/tuki-rabbit-admin.service > /dev/null <<'EOF'
[Unit]
Description=RabbitMQ Admin Service (Tuki-Tech)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu/apps
Environment=JAVA_TOOL_OPTIONS=-Xmx256m
Environment=SERVER_PORT=8085
Environment=SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=https://login.microsoftonline.com/e3e92dfe-ea59-4c42-a539-90e6fea570b6/v2.0
Environment=SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUDIENCES=api://tuki-tech-api,66c0f84b-3701-42fb-9189-3f4092cba201
Environment=CORS_ALLOWED_ORIGINS=http://localhost:4200,https://pastito-247.github.io
Environment=RABBITMQ_HOST=127.0.0.1
Environment=RABBITMQ_PORT=5672
Environment=RABBITMQ_SSL=false
Environment=RABBITMQ_USERNAME=tuki
Environment=RABBITMQ_PASSWORD=tuki
Environment=RABBITMQ_VHOST=/
Environment=RABBITMQ_MANAGEMENT_ENABLED=true
Environment=RABBITMQ_MANAGEMENT_URL=http://127.0.0.1:15672
ExecStart=/usr/bin/java -jar /home/ubuntu/apps/rabbitmq-admin-service-1.0.0.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF
```

### 6) payments-service

```bash
sudo tee /etc/systemd/system/tuki-payments.service > /dev/null <<'EOF'
[Unit]
Description=Payments Service (Tuki-Tech)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu/apps
Environment=JAVA_TOOL_OPTIONS=-Xmx256m
Environment=SERVER_PORT=8086
Environment=SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=https://login.microsoftonline.com/e3e92dfe-ea59-4c42-a539-90e6fea570b6/v2.0
Environment=SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUDIENCES=api://tuki-tech-api,66c0f84b-3701-42fb-9189-3f4092cba201
Environment=CORS_ALLOWED_ORIGINS=http://localhost:4200,https://pastito-247.github.io
Environment=RABBITMQ_HOST=127.0.0.1
Environment=RABBITMQ_PORT=5672
Environment=RABBITMQ_SSL=false
Environment=RABBITMQ_USERNAME=tuki
Environment=RABBITMQ_PASSWORD=tuki
Environment=RABBITMQ_VHOST=/
Environment=RABBITMQ_MANAGEMENT_URL=http://127.0.0.1:15672
Environment=RECEIPT_STORAGE_DIR=/home/ubuntu/apps/receipts
ExecStart=/usr/bin/java -jar /home/ubuntu/apps/payments-service-1.0.0.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF
```

### Iniciar y habilitar los 6

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now tuki-products tuki-categories tuki-users tuki-orders tuki-rabbit-admin tuki-payments
```

> Si usas `t2.micro` (1 GB RAM), baja el heap a `-Xmx192m` en los 6 units
> o solo 2 servicios arrancarán bien a la vez.

---

## Fase 6 — Verificar

En el EC2:

```bash
sudo systemctl status tuki-products --no-pager -l
ss -tlnp | grep -E '808[1-6]'
```

Deberías ver `java` escuchando en 8081, 8082, 8083, 8084, 8085 y 8086.

Desde tu PC (PowerShell):

```powershell
curl http://IP_EC2:8081/api/public/products
curl http://IP_EC2:8082/api/public/categories
curl http://IP_EC2:8085/api/rabbit/queues   # -> 401 sin token (requiere rol admin)
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
rabbitUrl:     'http://IP_EC2:8085/api/rabbit',
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
sudo systemctl stop tuki-products tuki-categories tuki-users tuki-orders tuki-rabbit-admin tuki-payments
```

**Actualizar un servicio** (nueva versión):

1. En tu PC: `mvn clean package -DskipTests`.
2. `scp -i TU_KEY.pem backend/products-service/target/products-service-1.0.0.jar ubuntu@IP_EC2:~/apps/`
3. En el EC2: `sudo systemctl restart tuki-products`.

---

## Auto-seed de datos demo

`products-service` y `categories-service` se **siembran solos** al arrancar si la
base (H2 in-memory `create-drop`) está vacía: crean 6 categorías y 19 productos de
ejemplo. No necesitas correr `scripts/seed-data.js` ni tener un token admin para
que el Home muestre el catálogo.

> `scripts/seed-data.js` sigue disponible como alternativa manual.

## Preparación rápida para una presentación/demo

Asumiendo que los JARs ya se subieron (`~/apps/`):

1. **EC2 → Start instance** (consola AWS), espera a que `2/2 checks passed`.
2. **SSH** y arranca/verifica los 6 servicios:
   ```bash
   sudo systemctl start tuki-products tuki-categories tuki-users tuki-orders tuki-rabbit-admin tuki-payments
   sudo systemctl status tuki-products --no-pager -l | head -20
   ss -tlnp | grep -E '808[1-6]'
   ```
   (la primera vez Spring toma 30-60s en arrancar)
3. **API Gateway** (consola AWS): verifica que el stage `$default` esté **Deployed**
   y apunte al EC2 (si apagaste la API, haz **Deploy** de nuevo).
4. Desde tu PC:
   ```powershell
   curl https://3cbwduklgb.execute-api.us-east-1.amazonaws.com/api/public/products
   curl https://3cbwduklgb.execute-api.us-east-1.amazonaws.com/api/products   # -> 401 sin token
   curl https://3cbwduklgb.execute-api.us-east-1.amazonaws.com/api/rabbit/queues   # -> 401 sin token admin
   ```
5. Home `https://pastito-247.github.io/CLOUD-NATIVE-I` con productos cargados (sin login).

---

## Notas importantes

- **Datos en memoria**: todos los servicios usan **H2 in-memory** con `create-drop`.
  Al reiniciar la instancia o el servicio, los datos se pierden. Para persistir en
  producción deberías migrar a RDS MySQL (ver README) o cambiar el properties.

- **users-service**: ya quedó arreglado en el repo (`server.port=8083`, issuer y
  audience en `application.yml`). La Fase 5 fuerza además el puerto y la
  audience por variables de entorno como respaldo.

- **RabbitMQ en el EC2**: corre como contenedor Docker (`docker-compose.ec2.yml`)
  y publica `5672`/`15672` solo en `127.0.0.1`. `payments-service` (productor),
  `orders-service`, `users-service` y `products-service` (consumidores) se conectan
  por loopback al arrancar, así que **no** hay que abrir nada en el Security Group.
  Verifica con `docker compose ps` y `curl -u tuki:tuki http://127.0.0.1:15672/api/overview`.
  Si los servicios arrancaron con `RABBITMQ_*` incorrectas, revisa
  `journalctl -u tuki-payments -n 100`. El volumen `rabbitmq-data` conserva colas y
  mensajes entre reinicios del contenedor.

- **CORS**: si publicas el frontend en GitHub Pages, agrega tu URL
  (ej: `https://pastito-247.github.io`) en `CORS_ALLOWED_ORIGINS` de los 6 units
  (separada por coma) y `systemctl restart` cada servicio. Ya se quitaron los
  `@CrossOrigin("*")` de los controllers: todo el CORS sale del `SecurityConfig`
  (`cors.allowed-origins` / `CORS_ALLOWED_ORIGINS`).

- **Costos**: `t3.micro` no entra en free tier (≈ US$0.0095/hora). `t2.micro`
  sí es free tier (12 meses), pero usa menos RAM.