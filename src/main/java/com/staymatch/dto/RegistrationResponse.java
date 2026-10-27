package com.staymatch.dto;

import com.staymatch.entity.Role;
import java.time.LocalDateTime;

/** Safe account details returned after successful registration. */
public record RegistrationResponse(
        Long id,
        String name,
        String email,
        Role role,
        LocalDateTime createdAt) {
}
