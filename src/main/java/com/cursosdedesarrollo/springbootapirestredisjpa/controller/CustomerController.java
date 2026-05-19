package com.cursosdedesarrollo.springbootapirestredisjpa.controller;

import com.cursosdedesarrollo.springbootapirestredisjpa.dto.CreateCustomerRequest;
import com.cursosdedesarrollo.springbootapirestredisjpa.dto.CustomerResponse;
import com.cursosdedesarrollo.springbootapirestredisjpa.dto.UpdateCustomerRequest;
import com.cursosdedesarrollo.springbootapirestredisjpa.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    public Flux<CustomerResponse> findAll() {
        return customerService.findAll();
    }

    @GetMapping("/{id}")
    public Mono<CustomerResponse> findById(@PathVariable String id) {
        return customerService.findById(id);
    }

    @PostMapping
    public Mono<ResponseEntity<CustomerResponse>> create(
            @Valid @RequestBody CreateCustomerRequest request) {
        return customerService.create(request)
                .map(response -> ResponseEntity
                        .created(URI.create("/api/customers/" + response.getId()))
                        .body(response));
    }

    @PutMapping("/{id}")
    public Mono<CustomerResponse> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateCustomerRequest request) {
        return customerService.update(id, request);
    }

    // PATCH en vez de PUT: semánticamente es una modificación parcial de un campo concreto.
    // delta puede ser negativo para canjear puntos.
    @PatchMapping("/{id}/loyalty")
    public Mono<CustomerResponse> addLoyaltyPoints(
            @PathVariable String id,
            @RequestParam int delta) {
        return customerService.addLoyaltyPoints(id, delta);
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable String id) {
        return customerService.delete(id)
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }
}
