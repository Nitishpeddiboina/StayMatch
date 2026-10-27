package com.staymatch.service;

import com.staymatch.dto.RegisterRequest;
import com.staymatch.dto.RegistrationResponse;
import com.staymatch.entity.Role;
import com.staymatch.entity.User;
import com.staymatch.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    private PasswordEncoder passwordEncoder;
    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        registrationService = new RegistrationService(userRepository, passwordEncoder);
    }

    @Test
    void registersWithNormalizedEmailAndBcryptPasswordHash() {
        String password = "  correct horse battery staple  ";
        RegisterRequest request = request("  Alice@Example.COM  ", password, Role.SEEKER);
        when(userRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(42L);
            user.setCreatedAt(LocalDateTime.of(2026, 1, 2, 3, 4));
            return user;
        });

        RegistrationResponse response = registrationService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("alice@example.com", savedUser.getEmail());
        assertNotEquals(password, savedUser.getPasswordHash());
        assertTrue(passwordEncoder.matches(password, savedUser.getPasswordHash()));
        assertEquals(Role.SEEKER, savedUser.getRole());
        assertEquals(42L, response.id());
        assertEquals("Alice", response.name());
        assertEquals("alice@example.com", response.email());
        assertEquals(Role.SEEKER, response.role());
        assertEquals(LocalDateTime.of(2026, 1, 2, 3, 4), response.createdAt());
        verify(userRepository).existsByEmailIgnoreCase("alice@example.com");
    }

    @Test
    void rejectsAnAlreadyRegisteredNormalizedEmail() {
        RegisterRequest request = request("Alice@Example.com", "correct horse battery staple", Role.OWNER);
        when(userRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyRegisteredException.class, () -> registrationService.register(request));

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void convertsConcurrentEmailUniquenessViolationToConflict() {
        RegisterRequest request = request("alice@example.com", "correct horse battery staple", Role.OWNER);
        when(userRepository.existsByEmailIgnoreCase("alice@example.com"))
                .thenReturn(false)
                .thenReturn(true);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate email"));

        assertThrows(EmailAlreadyRegisteredException.class, () -> registrationService.register(request));
    }

    private RegisterRequest request(String email, String password, Role role) {
        RegisterRequest request = new RegisterRequest();
        request.setName("Alice");
        request.setEmail(email);
        request.setPassword(password);
        request.setRole(role);
        return request;
    }
}
