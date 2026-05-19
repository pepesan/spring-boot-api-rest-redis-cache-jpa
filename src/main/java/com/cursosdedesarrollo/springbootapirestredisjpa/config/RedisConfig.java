package com.cursosdedesarrollo.springbootapirestredisjpa.config;

import com.cursosdedesarrollo.springbootapirestredisjpa.dto.UserResponse;
import com.cursosdedesarrollo.springbootapirestredisjpa.model.Product;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.TypeFactory;

import java.util.List;

@Configuration
public class RedisConfig {

    @Bean
    public ReactiveRedisTemplate<String, Product> productRedisTemplate(ReactiveRedisConnectionFactory factory) {

        // JacksonJsonRedisSerializer apunta a Jackson 3.x (tools.jackson.*), usado en Spring Boot 4.x.
        // El equivalente para Jackson 2.x era Jackson2JsonRedisSerializer.
        JacksonJsonRedisSerializer<Product> valueSerializer = new JacksonJsonRedisSerializer<>(Product.class);

        // Las claves se serializan como cadenas UTF-8 planas; los valores como JSON.
        RedisSerializationContext<String, Product> context =
                RedisSerializationContext.<String, Product>newSerializationContext(new StringRedisSerializer())
                        .value(valueSerializer)
                        .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }

    @Bean
    public ReactiveRedisTemplate<String, UserResponse> userRedisTemplate(ReactiveRedisConnectionFactory factory) {

        // WRITE_DATES_AS_TIMESTAMPS=false serializa LocalDateTime como ISO-8601 en lugar de array numérico.
        // Jackson 3.x incluye soporte nativo de java.time (ext/javatime) sin módulo externo.
        // En Jackson 3.x esta feature se movió a DateTimeFeature (ya no está en SerializationFeature).
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

    @Bean
    public ReactiveRedisTemplate<String, List<UserResponse>> userListRedisTemplate(ReactiveRedisConnectionFactory factory) {

        JsonMapper mapper = JsonMapper.builder()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        // TypeFactory resuelve el genérico en tiempo de compilación: List<UserResponse> en vez de List cruda.
        // Sin esto Jackson deserializaría los elementos como LinkedHashMap en lugar de UserResponse.
        JacksonJsonRedisSerializer<List<UserResponse>> valueSerializer = new JacksonJsonRedisSerializer<>(mapper,
                TypeFactory.createDefaultInstance().constructCollectionType(List.class, UserResponse.class));

        RedisSerializationContext<String, List<UserResponse>> context =
                RedisSerializationContext.<String, List<UserResponse>>newSerializationContext(new StringRedisSerializer())
                        .value(valueSerializer)
                        .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }
}
