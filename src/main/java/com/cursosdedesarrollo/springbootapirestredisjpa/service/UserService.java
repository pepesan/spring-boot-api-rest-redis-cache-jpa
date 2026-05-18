package com.cursosdedesarrollo.springbootapirestredisjpa.service;

import com.cursosdedesarrollo.springbootapirestredisjpa.dto.CreateUserRequest;
import com.cursosdedesarrollo.springbootapirestredisjpa.dto.UpdateUserRequest;
import com.cursosdedesarrollo.springbootapirestredisjpa.dto.UserResponse;
import com.cursosdedesarrollo.springbootapirestredisjpa.exception.UserAlreadyExistsException;
import com.cursosdedesarrollo.springbootapirestredisjpa.exception.UserNotFoundException;
import com.cursosdedesarrollo.springbootapirestredisjpa.model.User;
import com.cursosdedesarrollo.springbootapirestredisjpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public Flux<UserResponse> findAll() {
        return Mono.fromCallable(userRepository::findAll)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapIterable(users -> users)
                .map(this::toResponse);
    }

    public Mono<UserResponse> findById(Long id) {
        return Mono.fromCallable(() -> userRepository.findById(id)
                        .orElseThrow(() -> new UserNotFoundException(id)))
                .subscribeOn(Schedulers.boundedElastic())
                .map(this::toResponse);
    }

    public Mono<UserResponse> create(CreateUserRequest request) {
        return Mono.fromCallable(() -> {
            if (userRepository.existsByUsername(request.getUsername())) {
                throw new UserAlreadyExistsException("username", request.getUsername());
            }
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new UserAlreadyExistsException("email", request.getEmail());
            }
            User user = User.builder()
                    .username(request.getUsername())
                    .email(request.getEmail())
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .build();
            return toResponse(userRepository.save(user));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<UserResponse> update(Long id, UpdateUserRequest request) {
        return Mono.fromCallable(() -> {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new UserNotFoundException(id));
            if (request.getUsername() != null) {
                if (userRepository.existsByUsernameAndIdNot(request.getUsername(), id)) {
                    throw new UserAlreadyExistsException("username", request.getUsername());
                }
                user.setUsername(request.getUsername());
            }
            if (request.getEmail() != null) {
                if (userRepository.existsByEmailAndIdNot(request.getEmail(), id)) {
                    throw new UserAlreadyExistsException("email", request.getEmail());
                }
                user.setEmail(request.getEmail());
            }
            if (request.getFirstName() != null) {
                user.setFirstName(request.getFirstName());
            }
            if (request.getLastName() != null) {
                user.setLastName(request.getLastName());
            }
            return toResponse(userRepository.save(user));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> delete(Long id) {
        return Mono.fromRunnable(() -> {
            if (!userRepository.existsById(id)) {
                throw new UserNotFoundException(id);
            }
            userRepository.deleteById(id);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
