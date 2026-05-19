package com.cursosdedesarrollo.springbootapirestredisjpa.service;

import com.cursosdedesarrollo.springbootapirestredisjpa.dto.CreateUserRequest;
import com.cursosdedesarrollo.springbootapirestredisjpa.dto.UpdateUserRequest;
import com.cursosdedesarrollo.springbootapirestredisjpa.dto.UserResponse;
import com.cursosdedesarrollo.springbootapirestredisjpa.exception.UserAlreadyExistsException;
import com.cursosdedesarrollo.springbootapirestredisjpa.exception.UserNotFoundException;
import com.cursosdedesarrollo.springbootapirestredisjpa.model.User;
import com.cursosdedesarrollo.springbootapirestredisjpa.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CachedUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReactiveRedisTemplate<String, UserResponse> userRedisTemplate;

    @Mock
    private ReactiveValueOperations<String, UserResponse> valueOps;

    @Mock
    private ReactiveRedisTemplate<String, List<UserResponse>> userListRedisTemplate;

    @Mock
    private ReactiveValueOperations<String, List<UserResponse>> listValueOps;

    // Inyección manual: @InjectMocks no distingue dos ReactiveRedisTemplate del mismo tipo crudo
    // (erasure borra los genéricos en runtime) e inyectaría el mock equivocado en uno de los campos.
    private CachedUserService cachedUserService;

    @BeforeEach
    void setUp() {
        cachedUserService = new CachedUserService(userRepository, userRedisTemplate, userListRedisTemplate);
        lenient().when(userRedisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(userListRedisTemplate.opsForValue()).thenReturn(listValueOps);
        lenient().when(userListRedisTemplate.delete("user:all")).thenReturn(Mono.just(1L));
    }

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

    // --- findAll: cache hit ---

    @Test
    void findAll_cacheHit_returnsFromRedisWithoutCallingRepository() {
        when(listValueOps.get("user:all")).thenReturn(Mono.just(List.of(sampleResponse())));

        StepVerifier.create(cachedUserService.findAll())
                .assertNext(u -> assertThat(u.getUsername()).isEqualTo("john"))
                .verifyComplete();

        verify(userRepository, never()).findAll();
    }

    // --- findAll: cache miss ---

    @Test
    void findAll_cacheMiss_queriesJpaAndPopulatesCache() {
        when(listValueOps.get("user:all")).thenReturn(Mono.empty());
        when(userRepository.findAll()).thenReturn(List.of(sampleUser()));
        when(listValueOps.set(eq("user:all"), any(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(cachedUserService.findAll())
                .assertNext(u -> assertThat(u.getUsername()).isEqualTo("john"))
                .verifyComplete();

        verify(userRepository).findAll();
        verify(listValueOps).set(eq("user:all"), any(), any(Duration.class));
    }

    @Test
    void findAll_cacheHitEmptyList_returnsEmptyWithoutCallingRepository() {
        when(listValueOps.get("user:all")).thenReturn(Mono.just(List.of()));

        StepVerifier.create(cachedUserService.findAll())
                .verifyComplete();

        verify(userRepository, never()).findAll();
    }

    // --- findById: cache hit ---

    @Test
    void findById_cacheHit_returnsFromRedisWithoutCallingRepository() {
        when(valueOps.get("user:1")).thenReturn(Mono.just(sampleResponse()));

        StepVerifier.create(cachedUserService.findById(1L))
                .assertNext(u -> assertThat(u.getUsername()).isEqualTo("john"))
                .verifyComplete();

        verify(userRepository, never()).findById(any());
    }

    // --- findById: cache miss ---

    @Test
    void findById_cacheMiss_queriesJpaAndPopulatesCache() {
        when(valueOps.get("user:1")).thenReturn(Mono.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser()));
        when(valueOps.set(eq("user:1"), any(UserResponse.class), any(Duration.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(cachedUserService.findById(1L))
                .assertNext(u -> assertThat(u.getUsername()).isEqualTo("john"))
                .verifyComplete();

        verify(userRepository).findById(1L);
        verify(valueOps).set(eq("user:1"), any(UserResponse.class), any(Duration.class));
    }

    @Test
    void findById_cacheMiss_nonExistingId_throwsUserNotFoundException() {
        when(valueOps.get("user:99")).thenReturn(Mono.empty());
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        StepVerifier.create(cachedUserService.findById(99L))
                .expectError(UserNotFoundException.class)
                .verify();

        verify(valueOps, never()).set(any(), any(), any(Duration.class));
    }

    // --- create ---

    @Test
    void create_validRequest_savesToJpaAndPopulatesCacheAndInvalidatesList() {
        CreateUserRequest request = new CreateUserRequest("john", "john@example.com", "John", "Doe");
        when(userRepository.existsByUsername("john")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(sampleUser());
        when(valueOps.set(eq("user:1"), any(UserResponse.class), any(Duration.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(cachedUserService.create(request))
                .assertNext(u -> assertThat(u.getUsername()).isEqualTo("john"))
                .verifyComplete();

        verify(valueOps).set(eq("user:1"), any(UserResponse.class), any(Duration.class));
        verify(userListRedisTemplate).delete("user:all");
    }

    @Test
    void create_duplicateUsername_throwsUserAlreadyExistsException() {
        when(userRepository.existsByUsername("john")).thenReturn(true);

        StepVerifier.create(cachedUserService.create(new CreateUserRequest("john", "j@e.com", "J", "D")))
                .expectError(UserAlreadyExistsException.class)
                .verify();

        verify(valueOps, never()).set(any(), any(), any(Duration.class));
        verify(userListRedisTemplate, never()).delete(any(String.class));
    }

    // --- update ---

    @Test
    void update_existingUser_updatesJpaAndOverwritesCacheAndInvalidatesList() {
        UpdateUserRequest request = new UpdateUserRequest("johnny", null, null, null);
        User updated = sampleUser();
        updated.setUsername("johnny");

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser()));
        when(userRepository.existsByUsernameAndIdNot("johnny", 1L)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(updated);
        when(valueOps.set(eq("user:1"), any(UserResponse.class), any(Duration.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(cachedUserService.update(1L, request))
                .assertNext(u -> assertThat(u.getUsername()).isEqualTo("johnny"))
                .verifyComplete();

        verify(valueOps).set(eq("user:1"), any(UserResponse.class), any(Duration.class));
        verify(userListRedisTemplate).delete("user:all");
    }

    @Test
    void update_nonExistingId_throwsUserNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        StepVerifier.create(cachedUserService.update(99L, new UpdateUserRequest()))
                .expectError(UserNotFoundException.class)
                .verify();

        verify(userListRedisTemplate, never()).delete(any(String.class));
    }

    // --- delete ---

    @Test
    void delete_existingId_deletesFromJpaAndEvictsCacheAndInvalidatesList() {
        when(userRepository.existsById(1L)).thenReturn(true);
        doNothing().when(userRepository).deleteById(1L);
        when(userRedisTemplate.delete("user:1")).thenReturn(Mono.just(1L));

        StepVerifier.create(cachedUserService.delete(1L))
                .verifyComplete();

        verify(userRepository).deleteById(1L);
        verify(userRedisTemplate).delete("user:1");
        verify(userListRedisTemplate).delete("user:all");
    }

    @Test
    void delete_nonExistingId_throwsUserNotFoundException() {
        when(userRepository.existsById(99L)).thenReturn(false);

        StepVerifier.create(cachedUserService.delete(99L))
                .expectError(UserNotFoundException.class)
                .verify();

        verify(userRedisTemplate, never()).delete(any(String.class));
        verify(userListRedisTemplate, never()).delete(any(String.class));
    }
}
