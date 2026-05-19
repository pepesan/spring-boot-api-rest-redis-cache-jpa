package com.cursosdedesarrollo.springbootapirestredisjpa.service;

import com.cursosdedesarrollo.springbootapirestredisjpa.dto.CreateCustomerRequest;
import com.cursosdedesarrollo.springbootapirestredisjpa.dto.UpdateCustomerRequest;
import com.cursosdedesarrollo.springbootapirestredisjpa.exception.CustomerNotFoundException;
import com.cursosdedesarrollo.springbootapirestredisjpa.model.Customer;
import com.cursosdedesarrollo.springbootapirestredisjpa.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    private Customer sampleCustomer() {
        return Customer.builder()
                .id("uuid-1")
                .name("Ana García")
                .email("ana@example.com")
                .phone("612345678")
                .loyaltyPoints(100)
                .build();
    }

    // --- findAll ---

    @Test
    void findAll_returnsAllCustomers() {
        when(customerRepository.findAll()).thenReturn(Flux.just(sampleCustomer()));

        StepVerifier.create(customerService.findAll())
                .assertNext(r -> assertThat(r.getName()).isEqualTo("Ana García"))
                .verifyComplete();
    }

    // --- findById ---

    @Test
    void findById_existingId_returnsCustomer() {
        when(customerRepository.findById("uuid-1")).thenReturn(Mono.just(sampleCustomer()));

        StepVerifier.create(customerService.findById("uuid-1"))
                .assertNext(r -> {
                    assertThat(r.getId()).isEqualTo("uuid-1");
                    assertThat(r.getLoyaltyPoints()).isEqualTo(100);
                })
                .verifyComplete();
    }

    @Test
    void findById_nonExistingId_throwsCustomerNotFoundException() {
        when(customerRepository.findById("no-id")).thenReturn(Mono.empty());

        StepVerifier.create(customerService.findById("no-id"))
                .expectError(CustomerNotFoundException.class)
                .verify();
    }

    // --- create ---

    @Test
    void create_validRequest_savesCustomerWithZeroPoints() {
        CreateCustomerRequest request = new CreateCustomerRequest("Ana García", "ana@example.com", "612345678");
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(customerService.create(request))
                .assertNext(r -> {
                    assertThat(r.getName()).isEqualTo("Ana García");
                    assertThat(r.getLoyaltyPoints()).isEqualTo(0);
                    assertThat(r.getId()).isNotBlank();
                })
                .verifyComplete();
    }

    // --- update ---

    @Test
    void update_existingId_updatesOnlyProvidedFields() {
        when(customerRepository.findById("uuid-1")).thenReturn(Mono.just(sampleCustomer()));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        UpdateCustomerRequest request = new UpdateCustomerRequest("Ana G.", null, null);
        StepVerifier.create(customerService.update("uuid-1", request))
                .assertNext(r -> {
                    assertThat(r.getName()).isEqualTo("Ana G.");
                    assertThat(r.getEmail()).isEqualTo("ana@example.com"); // sin cambio
                })
                .verifyComplete();
    }

    @Test
    void update_nonExistingId_throwsCustomerNotFoundException() {
        when(customerRepository.findById("no-id")).thenReturn(Mono.empty());

        StepVerifier.create(customerService.update("no-id", new UpdateCustomerRequest()))
                .expectError(CustomerNotFoundException.class)
                .verify();
    }

    // --- addLoyaltyPoints ---

    @Test
    void addLoyaltyPoints_existingId_returnsUpdatedTotal() {
        when(customerRepository.findById("uuid-1")).thenReturn(Mono.just(sampleCustomer()));
        // HINCRBY: 100 puntos actuales + 50 delta = 150
        when(customerRepository.addLoyaltyPoints(eq("uuid-1"), eq(50L))).thenReturn(Mono.just(150L));

        StepVerifier.create(customerService.addLoyaltyPoints("uuid-1", 50))
                .assertNext(r -> assertThat(r.getLoyaltyPoints()).isEqualTo(150))
                .verifyComplete();

        verify(customerRepository).addLoyaltyPoints("uuid-1", 50L);
    }

    @Test
    void addLoyaltyPoints_negativeDelta_reducesPoints() {
        when(customerRepository.findById("uuid-1")).thenReturn(Mono.just(sampleCustomer()));
        // Canjear 30 puntos: 100 - 30 = 70
        when(customerRepository.addLoyaltyPoints(eq("uuid-1"), eq(-30L))).thenReturn(Mono.just(70L));

        StepVerifier.create(customerService.addLoyaltyPoints("uuid-1", -30))
                .assertNext(r -> assertThat(r.getLoyaltyPoints()).isEqualTo(70))
                .verifyComplete();
    }

    @Test
    void addLoyaltyPoints_nonExistingId_throwsCustomerNotFoundException() {
        when(customerRepository.findById("no-id")).thenReturn(Mono.empty());

        StepVerifier.create(customerService.addLoyaltyPoints("no-id", 50))
                .expectError(CustomerNotFoundException.class)
                .verify();

        verify(customerRepository, never()).addLoyaltyPoints(any(), anyLong());
    }

    // --- delete ---

    @Test
    void delete_existingId_deletesCustomer() {
        when(customerRepository.findById("uuid-1")).thenReturn(Mono.just(sampleCustomer()));
        when(customerRepository.delete("uuid-1")).thenReturn(Mono.empty());

        StepVerifier.create(customerService.delete("uuid-1"))
                .verifyComplete();

        verify(customerRepository).delete("uuid-1");
    }

    @Test
    void delete_nonExistingId_throwsCustomerNotFoundException() {
        when(customerRepository.findById("no-id")).thenReturn(Mono.empty());

        StepVerifier.create(customerService.delete("no-id"))
                .expectError(CustomerNotFoundException.class)
                .verify();

        verify(customerRepository, never()).delete(any());
    }
}
