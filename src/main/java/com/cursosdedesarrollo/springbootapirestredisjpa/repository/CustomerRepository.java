package com.cursosdedesarrollo.springbootapirestredisjpa.repository;

import com.cursosdedesarrollo.springbootapirestredisjpa.model.Customer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

// Redis Hash: cada campo del cliente es un campo independiente del hash.
// Clave: "customer:{uuid}"  →  HSET customer:uuid id "..." name "..." email "..." ...
@Repository
@RequiredArgsConstructor
public class CustomerRepository {

    private static final String KEY_PREFIX = "customer:";

    private final ReactiveStringRedisTemplate redisTemplate;

    @SuppressWarnings("unchecked")
    private ReactiveHashOperations<String, String, String> hashOps() {
        return redisTemplate.opsForHash();
    }

    // SCAN en vez de KEYS: no bloquea el servidor Redis en colecciones grandes
    public Flux<Customer> findAll() {
        ScanOptions options = ScanOptions.scanOptions().match(KEY_PREFIX + "*").build();
        return redisTemplate.scan(options)
                .flatMap(key -> hashOps().entries(key)
                        .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                        .filter(map -> !map.isEmpty())
                        .map(this::toCustomer));
    }

    // HGETALL: devuelve todos los campos del hash en una sola operación
    public Mono<Customer> findById(String id) {
        return hashOps().entries(KEY_PREFIX + id)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .filter(map -> !map.isEmpty())
                .map(this::toCustomer);
    }

    // HSET: escribe todos los campos del hash de una vez
    public Mono<Customer> save(Customer customer) {
        return hashOps().putAll(KEY_PREFIX + customer.getId(), toFieldMap(customer))
                .thenReturn(customer);
    }

    // HINCRBY: incremento atómico sobre un campo numérico sin leer ni reescribir el hash completo.
    // Devuelve el nuevo valor total, que Redis calcula y retorna en la misma operación.
    public Mono<Long> addLoyaltyPoints(String id, long delta) {
        return hashOps().increment(KEY_PREFIX + id, "loyaltyPoints", delta);
    }

    // DEL: elimina la clave completa (y por tanto todos los campos del hash)
    public Mono<Void> delete(String id) {
        return redisTemplate.delete(KEY_PREFIX + id).then();
    }

    private Map<String, String> toFieldMap(Customer c) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("id", c.getId());
        map.put("name", c.getName());
        map.put("email", c.getEmail());
        map.put("phone", c.getPhone() != null ? c.getPhone() : "");
        map.put("loyaltyPoints", String.valueOf(c.getLoyaltyPoints()));
        return map;
    }

    private Customer toCustomer(Map<String, String> fields) {
        String phone = fields.get("phone");
        return Customer.builder()
                .id(fields.get("id"))
                .name(fields.get("name"))
                .email(fields.get("email"))
                .phone(phone != null && !phone.isEmpty() ? phone : null)
                .loyaltyPoints(Integer.parseInt(fields.getOrDefault("loyaltyPoints", "0")))
                .build();
    }
}
