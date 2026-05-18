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

### Crear usuario — `POST /api/users`

```json
{
  "username": "johndoe",
  "email": "john@example.com",
  "firstName": "John",
  "lastName": "Doe"
}
```

**Validaciones:**
- `username`: requerido, entre 3 y 50 caracteres, único
- `email`: requerido, formato email válido, único
- `firstName` / `lastName`: requeridos, máximo 100 caracteres

### Actualizar usuario — `PUT /api/users/{id}`

Todos los campos son opcionales. Solo se actualizan los que se envíen (no nulos).

```json
{
  "email": "nuevo@example.com",
  "firstName": "Johnny"
}
```

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

## Ejecución local

Requiere Java 25. No necesita Redis ni base de datos externa; usa H2 en memoria.

```bash
./gradlew bootRun
```

La consola H2 está disponible en `http://localhost:8080/h2-console`
(JDBC URL: `jdbc:h2:mem:devdb`, usuario: `sa`, contraseña vacía).

## Tests

```bash
# Ejecutar todos los tests
./gradlew test

# Test de un solo método
./gradlew test --tests "*.UserServiceTest.create_validRequest_createsUser"
```

Los tests **no requieren Redis** (se excluye via `spring.autoconfigure.exclude` en
`src/test/resources/application.properties`).

| Clase de test | Tipo | Descripción |
|---|---|---|
| `UserServiceTest` | Unitario (Mockito) | Lógica de negocio, casos de error |
| `UserControllerTest` | Slice (`@WebFluxTest`) | HTTP responses, validación, manejo de errores |
| `SpringBootApiRestRedisJpaApplicationTests` | Contexto | Verifica que el contexto arranca |
