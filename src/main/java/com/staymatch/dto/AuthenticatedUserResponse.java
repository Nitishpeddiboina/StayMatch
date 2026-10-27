package com.staymatch.dto;

import com.staymatch.entity.Role;

/** Safe account details exposed to an authenticated client. */
public record AuthenticatedUserResponse(Long id, String name, String email, Role role) {
}
