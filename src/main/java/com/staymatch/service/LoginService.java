package com.staymatch.service;

import java.util.Locale;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class LoginService {

    private final AuthenticationManager authenticationManager;

    public LoginService(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    public Authentication authenticate(String email, String password) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        return authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(normalizedEmail, password));
    }
}
