package com.staymatch.dto;

import com.staymatch.entity.Role;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;

/** Request body for creating a StayMatch account. */
public class RegisterRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotBlank
    @Email
    @Size(max = 150)
    private String email;

    private String password;

    @NotNull
    private Role role;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    @AssertTrue(message = "Password must be at most 72 UTF-8 bytes.")
    public boolean isPasswordWithinBcryptLimit() {
        return password != null
                && !password.isBlank()
                && password.length() >= 12
                && password.length() <= 72
                && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
