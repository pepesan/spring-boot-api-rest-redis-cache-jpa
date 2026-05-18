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
}
