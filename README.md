# Spring Boot API REST Reactiva — CRUD de Usuarios

API REST reactiva construida con Spring Boot 4, WebFlux y JPA para gestión de usuarios.

## Stack tecnológico

| Capa | Tecnología |
|---|---|
| Web | Spring WebFlux (no bloqueante, `Mono`/`Flux`) |
| Persistencia | Spring Data JPA + H2 (en memoria) |
| Caché / Mensajería | Spring Data Redis Reactive |
| Validación | Jakarta Validation (`@Valid`) |
| Boilerplate | Lombok |

## Arquitectura

```
controller/   ← @RestController  →  Mono/Flux HTTP handlers
service/      ← @Service          →  lógica de negocio, wrap JPA en Schedulers.boundedElastic()
repository/   ← JpaRepository     →  acceso bloqueante a H2
model/        ← @Entity           →  User (tabla "users")
dto/          ← POJOs             →  CreateUserRequest, UpdateUserRequest, UserResponse
exception/    ← RuntimeException  →  UserNotFoundException, UserAlreadyExistsException
handler/      ← @RestControllerAdvice → GlobalExceptionHandler, ErrorResponse
```

> **Nota sobre reactividad + JPA:** JPA es bloqueante. Toda llamada al repositorio se envuelve
> con `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` para no bloquear
> el event loop de Netty.

## API REST

Base URL: `http://localhost:8080/api/users`

### Endpoints

| Método | Ruta | Descripción | Respuesta |
|--------|------|-------------|-----------|
| GET | `/api/users` | Listar todos los usuarios | `200 OK` — `Flux<UserResponse>` |
| GET | `/api/users/{id}` | Obtener usuario por ID | `200 OK` / `404 Not Found` |
| POST | `/api/users` | Crear usuario | `201 Created` + `Location` header |
| PUT | `/api/users/{id}` | Actualizar usuario (campos opcionales) | `200 OK` / `404` / `409` |
| DELETE | `/api/users/{id}` | Eliminar usuario | `204 No Content` / `404 Not Found` |

### Ejemplos curl

Los comandos están pensados para ejecutarse en secuencia en la misma terminal. Requiere [`jq`](https://jqlang.org).

```bash
# 1. Crear usuario y guardar el ID generado
USER_ID=$(curl -s -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"username":"johndoe","email":"john@example.com","firstName":"John","lastName":"Doe"}' \
  | jq -r '.id')
echo "Usuario creado: $USER_ID"

# 2. Obtener por ID
curl -s http://localhost:8080/api/users/$USER_ID | jq

# 3. Listar todos
curl -s http://localhost:8080/api/users | jq

# 4. Actualizar (solo los campos que cambian)
curl -s -X PUT http://localhost:8080/api/users/$USER_ID \
  -H "Content-Type: application/json" \
  -d '{"email":"nuevo@example.com","firstName":"Johnny"}' | jq

# 5. Eliminar
curl -s -X DELETE http://localhost:8080/api/users/$USER_ID -w "HTTP %{http_code}\n"
```

### Validaciones

- `username`: requerido, entre 3 y 50 caracteres, único
- `email`: requerido, formato email válido, único
- `firstName` / `lastName`: requeridos, máximo 100 caracteres

### Formato de error estándar

Todos los errores devuelven la misma estructura:

```json
{
  "timestamp": "2026-05-18T10:00:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/users",
  "errors": [
    { "field": "email", "message": "Email must be a valid email address" },
    { "field": "username", "message": "Username must be between 3 and 50 characters" }
  ]
}
```

| HTTP Status | Causa |
|-------------|-------|
| `400 Bad Request` | Error de validación Jakarta (`@Valid`) |
| `404 Not Found` | Usuario no encontrado por ID |
| `409 Conflict` | Username o email ya existe |
| `500 Internal Server Error` | Error inesperado (se registra en logs) |

## API REST — Productos (Redis)

Base URL: `http://localhost:8080/api/products`

Los productos se almacenan directamente en Redis como pares clave-valor JSON con el patrón de clave `product:{id}`. El ID se genera automáticamente (UUID) en la creación.

> Para usar este endpoint la aplicación necesita Redis corriendo. Levántalo con `./docker/01_launch.sh`.

### Endpoints

| Método | Ruta | Descripción | Respuesta |
|--------|------|-------------|-----------|
| GET | `/api/products` | Listar todos los productos | `200 OK` — `Flux<ProductResponse>` |
| GET | `/api/products/{id}` | Obtener producto por ID | `200 OK` / `404 Not Found` |
| POST | `/api/products` | Crear producto | `201 Created` + `Location` header |
| PUT | `/api/products/{id}` | Actualizar producto (campos opcionales) | `200 OK` / `404` |
| DELETE | `/api/products/{id}` | Eliminar producto | `204 No Content` / `404 Not Found` |

### Ejemplos curl

Los comandos están pensados para ejecutarse en secuencia en la misma terminal. Requiere [`jq`](https://jqlang.org).

```bash
# 1. Crear producto y guardar el ID generado (UUID)
PRODUCT_ID=$(curl -s -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{"name":"Widget","description":"A test widget","price":9.99,"stock":100}' \
  | jq -r '.id')
echo "Producto creado: $PRODUCT_ID"

# 2. Obtener por ID
curl -s http://localhost:8080/api/products/$PRODUCT_ID | jq

# 3. Listar todos
curl -s http://localhost:8080/api/products | jq

# 4. Actualizar (solo los campos que cambian)
curl -s -X PUT http://localhost:8080/api/products/$PRODUCT_ID \
  -H "Content-Type: application/json" \
  -d '{"price":19.99,"stock":50}' | jq

# 5. Eliminar
curl -s -X DELETE http://localhost:8080/api/products/$PRODUCT_ID -w "HTTP %{http_code}\n"
```

### Validaciones

- `name`: requerido, máximo 100 caracteres
- `description`: opcional, máximo 500 caracteres
- `price`: requerido en creación, debe ser positivo
- `stock`: requerido en creación, debe ser cero o positivo

## API REST — Usuarios con caché Redis (`/api/v2/users`)

Base URL: `http://localhost:8080/api/v2/users`

Este controlador expone el mismo CRUD de usuarios que `/api/users` pero añade una capa de caché Redis con el patrón **cache-aside**:

| Operación | Comportamiento |
|---|---|
| `GET /{id}` | Primero busca en Redis (`user:{id}`). Si no existe (cache miss), va a H2, guarda el resultado en Redis con TTL de 10 min y lo devuelve. |
| `GET /` | Sin caché — la lista completa se recarga siempre de H2. |
| `POST /` | Guarda en H2 y puebla Redis con el objeto persistido. |
| `PUT /{id}` | Actualiza H2 y sobreescribe la entrada en Redis. |
| `DELETE /{id}` | Elimina de H2 y hace evict explícito de Redis. |

> Requiere Redis corriendo. Levántalo con `./docker/01_launch.sh`.

### Probar la caché paso a paso

Los comandos están pensados para ejecutarse en secuencia en la misma terminal. Requiere [`jq`](https://jqlang.org). No necesitas `redis-cli` instalado localmente: los comandos de inspección usan `docker exec` sobre el contenedor `redis-dev`.

```bash
# Alias cómodo para no repetir el prefijo docker exec
alias rcli='docker exec redis-dev redis-cli'

# 1. Crear usuario — se guarda en H2 y se puebla la caché Redis
USER_ID=$(curl -s -X POST http://localhost:8080/api/v2/users \
  -H "Content-Type: application/json" \
  -d '{"username":"cacheduser","email":"cached@example.com","firstName":"Cache","lastName":"Test"}' \
  | jq -r '.id')
echo "Usuario creado con ID: $USER_ID"

# 2. Primera lectura — cache miss: consulta H2 y guarda en Redis
curl -s http://localhost:8080/api/v2/users/$USER_ID | jq

# Inspeccionar Redis: ver el JSON almacenado
rcli GET "user:$USER_ID" | jq
# Comprobar el TTL restante (en segundos, máximo 600 = 10 min)
rcli TTL "user:$USER_ID"

# 3. Segunda lectura — cache hit: respuesta servida desde Redis sin tocar H2
curl -s http://localhost:8080/api/v2/users/$USER_ID | jq

# 4. Actualizar — modifica H2 y sobreescribe la entrada en Redis
curl -s -X PUT http://localhost:8080/api/v2/users/$USER_ID \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Updated"}' | jq

# Verificar que Redis tiene el valor actualizado
rcli GET "user:$USER_ID" | jq '.firstName'

# 5. Eliminar — borra de H2 y hace evict de Redis
curl -s -X DELETE http://localhost:8080/api/v2/users/$USER_ID -w "HTTP %{http_code}\n"

# Confirmar que la clave ya no existe en Redis (devuelve nil)
rcli GET "user:$USER_ID"
```

### Inspección general de la caché

```bash
alias rcli='docker exec redis-dev redis-cli'

# Ver todas las claves de usuario almacenadas en Redis
rcli KEYS "user:*"

# Vaciar toda la caché de usuarios (sin afectar a los datos en H2)
rcli DEL $(rcli KEYS "user:*")
```

También puedes explorar las claves visualmente en **Redis Commander** (`http://localhost:8081`): busca las claves con prefijo `user:` en el panel izquierdo.

## Ejecución local

Requiere Java 25. Para datos relacionales usa H2 en memoria (no requiere instalación). Para los endpoints de productos (`/api/products`) es necesario tener Redis corriendo — levántalo con `./docker/01_launch.sh`.

```bash
./gradlew bootRun
```

La consola H2 está disponible en `http://localhost:8080/h2-console`
(JDBC URL: `jdbc:h2:mem:devdb`, usuario: `sa`, contraseña vacía).

## Docker

El directorio `docker/` contiene un entorno Redis completo para desarrollo local.

### Servicios

| Servicio | Imagen | Puerto | Descripción |
|---|---|---|---|
| `redis` | `redis:latest` | `6379` | Servidor Redis con persistencia en `docker/data/` |
| `redis-commander` | `rediscommander/redis-commander:latest` | `8081` | UI web para explorar Redis |

Redis Commander arranca sólo cuando Redis está sano (`healthcheck` via `redis-cli ping`).
Los datos se persisten en `docker/data/` (ignorado por git).

### Redis Commander

1. Levanta el entorno Docker (`./docker/01_launch.sh`)
2. Abre el navegador en **`http://localhost:8081`**

Verás el árbol de claves de Redis en el panel izquierdo. Desde ahí puedes explorar, crear, editar y borrar claves manualmente.

### Scripts

| Script | Descripción |
|---|---|
| `00_init.sh` | Crea el directorio `data/` con los permisos adecuados. **Ejecutar una vez antes del primer arranque.** |
| `01_launch.sh` | Levanta todos los servicios y espera a que estén sanos. |
| `02_ps.sh` | Muestra el estado de los contenedores. |
| `03_logs.sh` | Sigue los logs en tiempo real (`Ctrl+C` para salir). |
| `20_destroy.sh` | Para y elimina contenedores, red y el directorio `data/`. |

### Uso

```bash
# Primera vez
./docker/00_init.sh
./docker/01_launch.sh

# Verificar estado
./docker/02_ps.sh

# Ver logs
./docker/03_logs.sh

# Parar y limpiar todo
./docker/20_destroy.sh
```

Redis Commander disponible en `http://localhost:8081`.

## Tests

```bash
# Ejecutar todos los tests
./gradlew test

# Test de un solo método
./gradlew test --tests "*.UserServiceTest.create_validRequest_createsUser"
```

Los tests **no requieren Redis** (se excluye via `spring.autoconfigure.exclude` en
`src/test/resources/application.yaml`).

| Clase de test | Tipo | Descripción |
|---|---|---|
| `UserServiceTest` | Unitario (Mockito) | Lógica de negocio de usuarios sin caché |
| `UserControllerTest` | Slice (`@WebFluxTest`) | HTTP layer de `/api/users` |
| `CachedUserServiceTest` | Unitario (Mockito) | Cache-aside: hit, miss, evict; verifica interacción con `ReactiveRedisTemplate` |
| `CachedUserControllerTest` | Slice (`@WebFluxTest`) | HTTP layer de `/api/v2/users` |
| `ProductServiceTest` | Unitario (Mockito) | Lógica de negocio de productos (Redis) |
| `ProductControllerTest` | Slice (`@WebFluxTest`) | HTTP layer de `/api/products` |
| `SpringBootApiRestRedisJpaApplicationTests` | Contexto | Verifica que el contexto arranca |
