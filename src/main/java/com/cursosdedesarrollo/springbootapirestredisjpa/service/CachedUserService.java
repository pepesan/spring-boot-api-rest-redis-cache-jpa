package com.cursosdedesarrollo.springbootapirestredisjpa.service;

import com.cursosdedesarrollo.springbootapirestredisjpa.dto.CreateUserRequest;
import com.cursosdedesarrollo.springbootapirestredisjpa.dto.UpdateUserRequest;
import com.cursosdedesarrollo.springbootapirestredisjpa.dto.UserResponse;
import com.cursosdedesarrollo.springbootapirestredisjpa.exception.UserAlreadyExistsException;
import com.cursosdedesarrollo.springbootapirestredisjpa.exception.UserNotFoundException;
import com.cursosdedesarrollo.springbootapirestredisjpa.model.User;
import com.cursosdedesarrollo.springbootapirestredisjpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CachedUserService {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String KEY_PREFIX = "user:";
    private static final String LIST_KEY = "user:all";

    private final UserRepository userRepository;
    private final ReactiveRedisTemplate<String, UserResponse> userRedisTemplate;
    private final ReactiveRedisTemplate<String, List<UserResponse>> userListRedisTemplate;

    // Cache-aside sobre la lista completa.
    // switchIfEmpty actúa sobre Mono<List>, no sobre Flux: si la clave existe pero la lista está vacía,
    // el Mono emite List.of() y switchIfEmpty NO se dispara — el resultado vacío viene de caché.
    public Flux<UserResponse> findAll() {
        return userListRedisTemplate.opsForValue().get(LIST_KEY)           // 1. Buscar en Redis
                .switchIfEmpty(                                             // 2. Miss → ir a JPA
                        // JPA es bloqueante, por eso se desplaza a boundedElastic (ver UserService para
                        // la explicación completa del patrón fromCallable + subscribeOn).
                        Mono.fromCallable(userRepository::findAll)
                                .subscribeOn(Schedulers.boundedElastic())
                                .map(users -> users.stream().map(this::toResponse).toList())
                                .flatMap(list ->                            // 3. Guardar en Redis
                                        userListRedisTemplate.opsForValue().set(LIST_KEY, list, TTL)
                                                .thenReturn(list)          // 4. Devolver resultado
                                )
                )
                .flatMapIterable(list -> list);
    }

    // Cache-aside: Redis primero; si falla (empty), va a JPA y puebla la cache
    public Mono<UserResponse> findById(Long id) {
        String key = KEY_PREFIX + id;
        return userRedisTemplate.opsForValue().get(key)
                .switchIfEmpty(
                        // Miss: la llamada bloqueante a JPA se desplaza a boundedElastic.
                        // Lettuce (el driver de Redis) es no bloqueante, así que get() y set()
                        // no necesitan subscribeOn — solo lo necesita JPA.
                        Mono.fromCallable(() -> userRepository.findById(id)
                                        .orElseThrow(() -> new UserNotFoundException(id)))
                                .subscribeOn(Schedulers.boundedElastic())
                                .map(this::toResponse)
                                .flatMap(response ->
                                        userRedisTemplate.opsForValue().set(key, response, TTL)
                                                .thenReturn(response)
                                )
                );
    }

    // Guarda en JPA, puebla la caché individual e invalida la lista cacheada
    public Mono<UserResponse> create(CreateUserRequest request) {
        // El bloque fromCallable agrupa todas las llamadas JPA (existsBy + save) en un único salto
        // a boundedElastic. Al terminar, flatMap continúa en el scheduler reactivo para las
        // operaciones Redis, que son no bloqueantes.
        return Mono.fromCallable(() -> {
            if (userRepository.existsByUsername(request.getUsername())) {
                throw new UserAlreadyExistsException("username", request.getUsername());
            }
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new UserAlreadyExistsException("email", request.getEmail());
            }
            User user = User.builder()
                    .username(request.getUsername())
                    .email(request.getEmail())
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .build();
            return toResponse(userRepository.save(user));
        }).subscribeOn(Schedulers.boundedElastic())
                .flatMap(response ->
                        userRedisTemplate.opsForValue()
                                .set(KEY_PREFIX + response.getId(), response, TTL)
                                .then(userListRedisTemplate.delete(LIST_KEY))
                                .thenReturn(response)
                );
    }

    // Actualiza JPA, sobreescribe la entrada individual e invalida la lista cacheada
    public Mono<UserResponse> update(Long id, UpdateUserRequest request) {
        // Mismo patrón: bloque JPA en fromCallable/boundedElastic, operaciones Redis fuera en flatMap.
        return Mono.fromCallable(() -> {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new UserNotFoundException(id));
            if (request.getUsername() != null) {
                if (userRepository.existsByUsernameAndIdNot(request.getUsername(), id)) {
                    throw new UserAlreadyExistsException("username", request.getUsername());
                }
                user.setUsername(request.getUsername());
            }
            if (request.getEmail() != null) {
                if (userRepository.existsByEmailAndIdNot(request.getEmail(), id)) {
                    throw new UserAlreadyExistsException("email", request.getEmail());
                }
                user.setEmail(request.getEmail());
            }
            if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
            if (request.getLastName() != null) user.setLastName(request.getLastName());
            return toResponse(userRepository.save(user));
        }).subscribeOn(Schedulers.boundedElastic())
                .flatMap(response ->
                        userRedisTemplate.opsForValue()
                                .set(KEY_PREFIX + response.getId(), response, TTL)
                                .then(userListRedisTemplate.delete(LIST_KEY))
                                .thenReturn(response)
                );
    }

    // Elimina de JPA y hace evict de ambas cachés.
    // fromRunnable porque deleteById no devuelve valor.
    // Mono.defer() evalúa la llamada a Redis en tiempo de suscripción, no de ensamblado:
    // así el evict solo ocurre si el delete de JPA tuvo éxito.
    public Mono<Void> delete(Long id) {
        return Mono.fromRunnable(() -> {
            if (!userRepository.existsById(id)) {
                throw new UserNotFoundException(id);
            }
            userRepository.deleteById(id);
        }).subscribeOn(Schedulers.boundedElastic())
                .then(Mono.defer(() -> userRedisTemplate.delete(KEY_PREFIX + id)))
                .then(Mono.defer(() -> userListRedisTemplate.delete(LIST_KEY)))
                .then();
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
