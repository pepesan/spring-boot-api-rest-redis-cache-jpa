package com.cursosdedesarrollo.springbootapirestredisjpa.config;

import com.cursosdedesarrollo.springbootapirestredisjpa.dto.UserResponse;
import com.cursosdedesarrollo.springbootapirestredisjpa.model.Product;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.TypeFactory;

import java.util.List;

// Configuración de los templates reactivos de Redis.
//
// Cada bean define cómo se serializan claves y valores antes de enviarse a Redis.
// Todos usan StringRedisSerializer para las claves (UTF-8 plano). Los valores
// difieren según el caso de uso:
//
//   Bean                        Tipo de valor         Consumidor
//   ──────────────────────────────────────────────────────────────────────
//   reactiveStringRedisTemplate  String (plano)       CustomerRepository
//                                                     (Redis Hash, HINCRBY)
//   productRedisTemplate         Product (JSON)       ProductService
//                                                     (Redis String, CRUD)
//   userRedisTemplate            UserResponse (JSON)  CachedUserService
//                                                     (caché individual)
//   userListRedisTemplate        List<UserResponse>   CachedUserService
//                                                     (caché de lista)
//
// Por qué cuatro beans en lugar de uno genérico:
//   - Cada tipo de valor necesita su propio serializador tipado; no existe
//     un serializador "universal" reactivo en Spring Data Redis.
//   - Los templates con fechas (User) requieren configuración extra de
//     Jackson para serializar LocalDateTime como ISO-8601 en lugar de array.
//   - El template de lista necesita un JavaType con el genérico resuelto
//     para que Jackson no deserialice los elementos como LinkedHashMap.
@Configuration
public class RedisConfig {

    // ─── Redis Hash (clientes) ────────────────────────────────────────────────
    //
    // ReactiveStringRedisTemplate es un subtipo de ReactiveRedisTemplate<String,String>
    // donde los cuatro slots de serialización usan StringRedisSerializer por defecto.
    // Lo reconstruimos explícitamente para dejar claro el rol de cada slot:
    //
    //   newSerializationContext(serializer)  → clave principal    "customer:uuid"
    //   .value(serializer)                   → valor de opsForValue  (no usado aquí)
    //   .hashKey(serializer)                 → nombre del campo       "loyaltyPoints"
    //   .hashValue(serializer)               → valor del campo        "120"
    //
    // Guardar cada campo como String plano permite que Redis interprete el valor
    // como número y ejecute HINCRBY de forma atómica, sin leer ni reescribir el
    // objeto entero. Ver CustomerRepository.addLoyaltyPoints.
    //
    // Contrapartida frente a JSON: el mapping objeto ↔ campos queda en Java
    // (toFieldMap / toCustomer en CustomerRepository). Para objetos sin operaciones
    // atómicas por campo es más simple serializar el objeto completo (ver abajo).
    @Bean
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(ReactiveRedisConnectionFactory factory) {
        StringRedisSerializer serializer = new StringRedisSerializer();
        RedisSerializationContext<String, String> context =
                RedisSerializationContext.<String, String>newSerializationContext(serializer)
                        .value(serializer)
                        .hashKey(serializer)
                        .hashValue(serializer)
                        .build();
        return new ReactiveStringRedisTemplate(factory, context);
    }

    // ─── Redis String — productos ─────────────────────────────────────────────
    //
    // Almacena el objeto Product completo como un único JSON bajo la clave
    // "product:{uuid}". No requiere configuración de fechas porque Product
    // no tiene campos LocalDateTime.
    //
    // JacksonJsonRedisSerializer apunta a Jackson 3.x (tools.jackson.*),
    // el paquete usado en Spring Boot 4.x. En Jackson 2.x era Jackson2JsonRedisSerializer.
    @Bean
    public ReactiveRedisTemplate<String, Product> productRedisTemplate(ReactiveRedisConnectionFactory factory) {
        JacksonJsonRedisSerializer<Product> valueSerializer = new JacksonJsonRedisSerializer<>(Product.class);

        RedisSerializationContext<String, Product> context =
                RedisSerializationContext.<String, Product>newSerializationContext(new StringRedisSerializer())
                        .value(valueSerializer)
                        .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }

    // ─── Redis String — caché de usuario individual ───────────────────────────
    //
    // Almacena un UserResponse como JSON bajo la clave "user:{id}".
    // UserResponse contiene campos LocalDateTime (createdAt, updatedAt), por lo que
    // hay que deshabilitar WRITE_DATES_AS_TIMESTAMPS para serializar como ISO-8601
    // en lugar de array numérico [2026,5,18,...].
    //
    // En Jackson 3.x el soporte de java.time es nativo (sin módulo externo) y esta
    // feature se movió de SerializationFeature a DateTimeFeature.
    @Bean
    public ReactiveRedisTemplate<String, UserResponse> userRedisTemplate(ReactiveRedisConnectionFactory factory) {
        JsonMapper mapper = JsonMapper.builder()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        JacksonJsonRedisSerializer<UserResponse> valueSerializer =
                new JacksonJsonRedisSerializer<>(mapper, UserResponse.class);

        RedisSerializationContext<String, UserResponse> context =
                RedisSerializationContext.<String, UserResponse>newSerializationContext(new StringRedisSerializer())
                        .value(valueSerializer)
                        .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }

    // ─── Redis String — caché de lista de usuarios ────────────────────────────
    //
    // Bean separado de userRedisTemplate porque el tipo genérico List<UserResponse>
    // requiere un JavaType resuelto en tiempo de ejecución. Sin TypeFactory, Jackson
    // deserializaría los elementos como LinkedHashMap en lugar de UserResponse.
    //
    // Misma configuración de fechas que userRedisTemplate: UserResponse tiene
    // LocalDateTime y necesita ISO-8601.
    @Bean
    public ReactiveRedisTemplate<String, List<UserResponse>> userListRedisTemplate(ReactiveRedisConnectionFactory factory) {
        JsonMapper mapper = JsonMapper.builder()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        JacksonJsonRedisSerializer<List<UserResponse>> valueSerializer = new JacksonJsonRedisSerializer<>(mapper,
                TypeFactory.createDefaultInstance().constructCollectionType(List.class, UserResponse.class));

        RedisSerializationContext<String, List<UserResponse>> context =
                RedisSerializationContext.<String, List<UserResponse>>newSerializationContext(new StringRedisSerializer())
                        .value(valueSerializer)
                        .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }
}
