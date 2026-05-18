package com.cursosdedesarrollo.springbootapirestredisjpa.controller;

import com.cursosdedesarrollo.springbootapirestredisjpa.dto.CreateUserRequest;
import com.cursosdedesarrollo.springbootapirestredisjpa.dto.UpdateUserRequest;
import com.cursosdedesarrollo.springbootapirestredisjpa.dto.UserResponse;
import com.cursosdedesarrollo.springbootapirestredisjpa.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public Flux<UserResponse> getAll() {
        return userService.findAll();
    }

    @GetMapping("/{id}")
    public Mono<UserResponse> getById(@PathVariable Long id) {
        return userService.findById(id);
    }

    @PostMapping
    public Mono<ResponseEntity<UserResponse>> create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request)
                .map(user -> ResponseEntity
                        .created(URI.create("/api/users/" + user.getId()))
                        .body(user));
    }

    @PutMapping("/{id}")
    public Mono<UserResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable Long id) {
        return userService.delete(id)
                .then(Mono.just(ResponseEntity.<Void>noContent().build()));
    }
}
