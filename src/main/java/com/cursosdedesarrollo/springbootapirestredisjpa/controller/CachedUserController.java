package com.cursosdedesarrollo.springbootapirestredisjpa.controller;

import com.cursosdedesarrollo.springbootapirestredisjpa.dto.CreateUserRequest;
import com.cursosdedesarrollo.springbootapirestredisjpa.dto.UpdateUserRequest;
import com.cursosdedesarrollo.springbootapirestredisjpa.dto.UserResponse;
import com.cursosdedesarrollo.springbootapirestredisjpa.service.CachedUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;

@RestController
@RequestMapping("/api/v2/users")
@RequiredArgsConstructor
public class CachedUserController {

    private final CachedUserService cachedUserService;

    @GetMapping
    public Flux<UserResponse> getAll() {
        return cachedUserService.findAll();
    }

    @GetMapping("/{id}")
    public Mono<UserResponse> getById(@PathVariable Long id) {
        return cachedUserService.findById(id);
    }

    @PostMapping
    public Mono<ResponseEntity<UserResponse>> create(@Valid @RequestBody CreateUserRequest request) {
        return cachedUserService.create(request)
                .map(user -> ResponseEntity
                        .created(URI.create("/api/v2/users/" + user.getId()))
                        .body(user));
    }

    @PutMapping("/{id}")
    public Mono<UserResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return cachedUserService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable Long id) {
        return cachedUserService.delete(id)
                .then(Mono.just(ResponseEntity.<Void>noContent().build()));
    }
}
