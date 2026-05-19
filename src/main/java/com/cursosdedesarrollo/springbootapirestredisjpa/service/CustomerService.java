package com.cursosdedesarrollo.springbootapirestredisjpa.service;

import com.cursosdedesarrollo.springbootapirestredisjpa.dto.CreateCustomerRequest;
import com.cursosdedesarrollo.springbootapirestredisjpa.dto.CustomerResponse;
import com.cursosdedesarrollo.springbootapirestredisjpa.dto.UpdateCustomerRequest;
import com.cursosdedesarrollo.springbootapirestredisjpa.exception.CustomerNotFoundException;
import com.cursosdedesarrollo.springbootapirestredisjpa.model.Customer;
import com.cursosdedesarrollo.springbootapirestredisjpa.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    public Flux<CustomerResponse> findAll() {
        return customerRepository.findAll().map(this::toResponse);
    }

    public Mono<CustomerResponse> findById(String id) {
        return customerRepository.findById(id)
                .switchIfEmpty(Mono.error(new CustomerNotFoundException(id)))
                .map(this::toResponse);
    }

    public Mono<CustomerResponse> create(CreateCustomerRequest request) {
        Customer customer = Customer.builder()
                .id(UUID.randomUUID().toString())
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .loyaltyPoints(0)
                .build();
        return customerRepository.save(customer).map(this::toResponse);
    }

    public Mono<CustomerResponse> update(String id, UpdateCustomerRequest request) {
        return customerRepository.findById(id)
                .switchIfEmpty(Mono.error(new CustomerNotFoundException(id)))
                .flatMap(customer -> {
                    if (request.getName() != null) customer.setName(request.getName());
                    if (request.getEmail() != null) customer.setEmail(request.getEmail());
                    if (request.getPhone() != null) customer.setPhone(request.getPhone());
                    return customerRepository.save(customer);
                })
                .map(this::toResponse);
    }

    // HINCRBY: el delta llega desde el controlador y Redis lo aplica atómicamente.
    // No hace falta leer el valor actual ni reescribir el hash completo.
    public Mono<CustomerResponse> addLoyaltyPoints(String id, int delta) {
        return customerRepository.findById(id)
                .switchIfEmpty(Mono.error(new CustomerNotFoundException(id)))
                .flatMap(customer ->
                        customerRepository.addLoyaltyPoints(id, delta)
                                .map(newTotal -> {
                                    customer.setLoyaltyPoints(newTotal.intValue());
                                    return toResponse(customer);
                                })
                );
    }

    public Mono<Void> delete(String id) {
        return customerRepository.findById(id)
                .switchIfEmpty(Mono.error(new CustomerNotFoundException(id)))
                .flatMap(c -> customerRepository.delete(id));
    }

    private CustomerResponse toResponse(Customer c) {
        return CustomerResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .email(c.getEmail())
                .phone(c.getPhone())
                .loyaltyPoints(c.getLoyaltyPoints())
                .build();
    }
}
