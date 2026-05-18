package com.cursosdedesarrollo.springbootapirestredisjpa.service;

import com.cursosdedesarrollo.springbootapirestredisjpa.dto.CreateUserRequest;
import com.cursosdedesarrollo.springbootapirestredisjpa.dto.UpdateUserRequest;
import com.cursosdedesarrollo.springbootapirestredisjpa.exception.UserAlreadyExistsException;
import com.cursosdedesarrollo.springbootapirestredisjpa.exception.UserNotFoundException;
import com.cursosdedesarrollo.springbootapirestredisjpa.model.User;
import com.cursosdedesarrollo.springbootapirestredisjpa.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User sampleUser() {
        return User.builder()
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
    void findAll_returnsAllUsers() {
        when(userRepository.findAll()).thenReturn(List.of(sampleUser()));

        StepVerifier.create(userService.findAll())
                .assertNext(u -> assertThat(u.getUsername()).isEqualTo("john"))
                .verifyComplete();
    }

    @Test
    void findById_existingId_returnsUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser()));

        StepVerifier.create(userService.findById(1L))
                .assertNext(u -> {
                    assertThat(u.getId()).isEqualTo(1L);
                    assertThat(u.getUsername()).isEqualTo("john");
                })
                .verifyComplete();
    }

    @Test
    void findById_nonExistingId_throwsUserNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        StepVerifier.create(userService.findById(99L))
                .expectError(UserNotFoundException.class)
                .verify();
    }

    @Test
    void create_validRequest_createsUser() {
        CreateUserRequest request = new CreateUserRequest("john", "john@example.com", "John", "Doe");
        when(userRepository.existsByUsername("john")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(sampleUser());

        StepVerifier.create(userService.create(request))
                .assertNext(u -> assertThat(u.getUsername()).isEqualTo("john"))
                .verifyComplete();
    }

    @Test
    void create_duplicateUsername_throwsUserAlreadyExistsException() {
        CreateUserRequest request = new CreateUserRequest("john", "john@example.com", "John", "Doe");
        when(userRepository.existsByUsername("john")).thenReturn(true);

        StepVerifier.create(userService.create(request))
                .expectError(UserAlreadyExistsException.class)
                .verify();
    }

    @Test
    void create_duplicateEmail_throwsUserAlreadyExistsException() {
        CreateUserRequest request = new CreateUserRequest("john", "john@example.com", "John", "Doe");
        when(userRepository.existsByUsername("john")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        StepVerifier.create(userService.create(request))
                .expectError(UserAlreadyExistsException.class)
                .verify();
    }

    @Test
    void update_existingUser_updatesUsername() {
        UpdateUserRequest request = new UpdateUserRequest("johnny", null, null, null);
        User updated = sampleUser();
        updated.setUsername("johnny");

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser()));
        when(userRepository.existsByUsernameAndIdNot("johnny", 1L)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(updated);

        StepVerifier.create(userService.update(1L, request))
                .assertNext(u -> assertThat(u.getUsername()).isEqualTo("johnny"))
                .verifyComplete();
    }

    @Test
    void update_nonExistingId_throwsUserNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        StepVerifier.create(userService.update(99L, new UpdateUserRequest()))
                .expectError(UserNotFoundException.class)
                .verify();
    }

    @Test
    void update_duplicateUsername_throwsUserAlreadyExistsException() {
        UpdateUserRequest request = new UpdateUserRequest("taken", null, null, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser()));
        when(userRepository.existsByUsernameAndIdNot("taken", 1L)).thenReturn(true);

        StepVerifier.create(userService.update(1L, request))
                .expectError(UserAlreadyExistsException.class)
                .verify();
    }

    @Test
    void delete_existingId_deletesUser() {
        when(userRepository.existsById(1L)).thenReturn(true);
        doNothing().when(userRepository).deleteById(1L);

        StepVerifier.create(userService.delete(1L))
                .verifyComplete();

        verify(userRepository).deleteById(1L);
    }

    @Test
    void delete_nonExistingId_throwsUserNotFoundException() {
        when(userRepository.existsById(99L)).thenReturn(false);

        StepVerifier.create(userService.delete(99L))
                .expectError(UserNotFoundException.class)
                .verify();
    }
}
