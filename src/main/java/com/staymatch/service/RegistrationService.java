package com.staymatch.service;

import com.staymatch.dto.RegisterRequest;
import com.staymatch.dto.RegistrationResponse;
import com.staymatch.entity.User;
import com.staymatch.repository.UserRepository;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public RegistrationResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }

        User user = new User(
                request.getName(),
                normalizedEmail,
                passwordEncoder.encode(request.getPassword()),
                request.getRole());

        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            // Covers a concurrent registration that passed the initial existence check.
            if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
                throw new EmailAlreadyRegisteredException();
            }
            throw exception;
        }
        return new RegistrationResponse(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getRole(),
                savedUser.getCreatedAt());
    }
}
