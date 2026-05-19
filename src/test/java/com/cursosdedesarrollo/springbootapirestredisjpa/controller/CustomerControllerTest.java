package com.cursosdedesarrollo.springbootapirestredisjpa.controller;

import com.cursosdedesarrollo.springbootapirestredisjpa.dto.CreateCustomerRequest;
import com.cursosdedesarrollo.springbootapirestredisjpa.dto.CustomerResponse;
import com.cursosdedesarrollo.springbootapirestredisjpa.dto.UpdateCustomerRequest;
import com.cursosdedesarrollo.springbootapirestredisjpa.exception.CustomerNotFoundException;
import com.cursosdedesarrollo.springbootapirestredisjpa.handler.GlobalExceptionHandler;
import com.cursosdedesarrollo.springbootapirestredisjpa.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest({CustomerController.class, GlobalExceptionHandler.class})
class CustomerControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private CustomerService customerService;

    private CustomerResponse sampleResponse() {
        return CustomerResponse.builder()
                .id("uuid-1")
                .name("Ana García")
                .email("ana@example.com")
                .phone("612345678")
                .loyaltyPoints(100)
                .build();
    }

    @Test
    void getAll_returnsListOfCustomers() {
        when(customerService.findAll()).thenReturn(Flux.just(sampleResponse()));

        webTestClient.get().uri("/api/customers")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(CustomerResponse.class)
                .hasSize(1);
    }

    @Test
    void getById_existingId_returnsCustomer() {
        when(customerService.findById("uuid-1")).thenReturn(Mono.just(sampleResponse()));

        webTestClient.get().uri("/api/customers/uuid-1")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("uuid-1")
                .jsonPath("$.name").isEqualTo("Ana García")
                .jsonPath("$.loyaltyPoints").isEqualTo(100);
    }

    @Test
    void getById_nonExistingId_returns404() {
        when(customerService.findById("no-id"))
                .thenReturn(Mono.error(new CustomerNotFoundException("no-id")));

        webTestClient.get().uri("/api/customers/no-id")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.message").isEqualTo("Customer not found with id: no-id");
    }

    @Test
    void create_validRequest_returns201WithLocationHeader() {
        when(customerService.create(any(CreateCustomerRequest.class)))
                .thenReturn(Mono.just(sampleResponse()));

        webTestClient.post().uri("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateCustomerRequest("Ana García", "ana@example.com", "612345678"))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().location("/api/customers/uuid-1")
                .expectBody()
                .jsonPath("$.name").isEqualTo("Ana García")
                .jsonPath("$.loyaltyPoints").isEqualTo(100);
    }

    @Test
    void create_invalidRequest_returns400WithFieldErrors() {
        webTestClient.post().uri("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateCustomerRequest("", "not-an-email", null))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.message").isEqualTo("Validation failed")
                .jsonPath("$.errors").isArray();
    }

    @Test
    void update_existingId_returnsUpdatedCustomer() {
        CustomerResponse updated = sampleResponse();
        updated.setName("Ana G.");
        when(customerService.update(eq("uuid-1"), any(UpdateCustomerRequest.class)))
                .thenReturn(Mono.just(updated));

        webTestClient.put().uri("/api/customers/uuid-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdateCustomerRequest("Ana G.", null, null))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Ana G.");
    }

    @Test
    void update_nonExistingId_returns404() {
        when(customerService.update(eq("no-id"), any(UpdateCustomerRequest.class)))
                .thenReturn(Mono.error(new CustomerNotFoundException("no-id")));

        webTestClient.put().uri("/api/customers/no-id")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdateCustomerRequest("Ana G.", null, null))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void addLoyaltyPoints_existingId_returnsUpdatedTotal() {
        CustomerResponse updated = sampleResponse();
        updated.setLoyaltyPoints(150);
        when(customerService.addLoyaltyPoints("uuid-1", 50)).thenReturn(Mono.just(updated));

        webTestClient.patch().uri("/api/customers/uuid-1/loyalty?delta=50")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.loyaltyPoints").isEqualTo(150);
    }

    @Test
    void addLoyaltyPoints_nonExistingId_returns404() {
        when(customerService.addLoyaltyPoints("no-id", 50))
                .thenReturn(Mono.error(new CustomerNotFoundException("no-id")));

        webTestClient.patch().uri("/api/customers/no-id/loyalty?delta=50")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void delete_existingId_returns204() {
        when(customerService.delete("uuid-1")).thenReturn(Mono.empty());

        webTestClient.delete().uri("/api/customers/uuid-1")
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void delete_nonExistingId_returns404() {
        when(customerService.delete("no-id"))
                .thenReturn(Mono.error(new CustomerNotFoundException("no-id")));

        webTestClient.delete().uri("/api/customers/no-id")
                .exchange()
                .expectStatus().isNotFound();
    }
}
