package com.cursosdedesarrollo.springbootapirestredisjpa;

import com.cursosdedesarrollo.springbootapirestredisjpa.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class SpringBootApiRestRedisJpaApplicationTests {

    // Redis se excluye en el yaml de test; los mocks evitan que fallen las dependencias de RedisConfig y ProductRepository
    @MockitoBean
    ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;

    @MockitoBean
    ProductRepository productRepository;

    @Test
    void contextLoads() {
    }
}
