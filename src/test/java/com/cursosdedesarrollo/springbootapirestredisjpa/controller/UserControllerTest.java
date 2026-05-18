package com.cursosdedesarrollo.springbootapirestredisjpa.controller;

import com.cursosdedesarrollo.springbootapirestredisjpa.dto.CreateUserRequest;
import com.cursosdedesarrollo.springbootapirestredisjpa.dto.UpdateUserRequest;
import com.cursosdedesarrollo.springbootapirestredisjpa.dto.UserResponse;
import com.cursosdedesarrollo.springbootapirestredisjpa.exception.UserAlreadyExistsException;
import com.cursosdedesarrollo.springbootapirestredisjpa.exception.UserNotFoundException;
import com.cursosdedesarrollo.springbootapirestredisjpa.handler.GlobalExceptionHandler;
import com.cursosdedesarrollo.springbootapirestredisjpa.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest({UserController.class, GlobalExceptionHandler.class})
class UserControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private UserService userService;

    private UserResponse sampleResponse() {
        return UserResponse.builder()
                .id(1L)
                .username("john")
                .email("john@example.com")
                .firstName("John")
                .lastName("Doe")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void getAll_returnsListOfUsers() {
        when(userService.findAll()).thenReturn(Flux.just(sampleResponse()));

        webTestClient.get().uri("/api/users")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(UserResponse.class)
                .hasSize(1);
    }

    @Test
    void getById_existingId_returnsUser() {
        when(userService.findById(1L)).thenReturn(Mono.just(sampleResponse()));

        webTestClient.get().uri("/api/users/1")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1)
                .jsonPath("$.username").isEqualTo("john");
    }

    @Test
    void getById_nonExistingId_returns404() {
        when(userService.findById(99L)).thenReturn(Mono.error(new UserNotFoundException(99L)));

        webTestClient.get().uri("/api/users/99")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.message").isEqualTo("User not found with id: 99");
    }

    @Test
    void create_validRequest_returns201WithLocationHeader() {
        when(userService.create(any(CreateUserRequest.class))).thenReturn(Mono.just(sampleResponse()));

        webTestClient.post().uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateUserRequest("john", "john@example.com", "John", "Doe"))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().location("/api/users/1")
                .expectBody()
                .jsonPath("$.username").isEqualTo("john");
    }

    @Test
    void create_invalidRequest_returns400WithFieldErrors() {
        // username too short, invalid email, blank first/last name
        webTestClient.post().uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateUserRequest("ab", "not-an-email", "", ""))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.message").isEqualTo("Validation failed")
                .jsonPath("$.errors").isArray();
    }

    @Test
    void create_duplicateUsername_returns409() {
        when(userService.create(any(CreateUserRequest.class)))
                .thenReturn(Mono.error(new UserAlreadyExistsException("username", "john")));

        webTestClient.post().uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateUserRequest("john", "john@example.com", "John", "Doe"))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.status").isEqualTo(409);
    }

    @Test
    void update_existingId_returnsUpdatedUser() {
        UserResponse updated = sampleResponse();
        updated.setUsername("johnny");
        when(userService.update(eq(1L), any(UpdateUserRequest.class))).thenReturn(Mono.just(updated));

        webTestClient.put().uri("/api/users/1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdateUserRequest("johnny", null, null, null))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.username").isEqualTo("johnny");
    }

    @Test
    void update_nonExistingId_returns404() {
        when(userService.update(eq(99L), any(UpdateUserRequest.class)))
                .thenReturn(Mono.error(new UserNotFoundException(99L)));

        webTestClient.put().uri("/api/users/99")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdateUserRequest("johnny", null, null, null))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void delete_existingId_returns204() {
        when(userService.delete(1L)).thenReturn(Mono.empty());

        webTestClient.delete().uri("/api/users/1")
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void delete_nonExistingId_returns404() {
        when(userService.delete(99L)).thenReturn(Mono.error(new UserNotFoundException(99L)));

        webTestClient.delete().uri("/api/users/99")
                .exchange()
                .expectStatus().isNotFound();
    }
}
