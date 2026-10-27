package com.staymatch.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Test
    void normalizesEmailBeforeDelegatingCredentialCheck() {
        LoginService loginService = new LoginService(authenticationManager);
        Authentication expected = UsernamePasswordAuthenticationToken.authenticated("principal", null, java.util.List.of());
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(expected);

        Authentication actual = loginService.authenticate(" Alice@Example.COM ", "  password text  ");

        assertSame(expected, actual);
        verify(authenticationManager).authenticate(org.mockito.ArgumentMatchers.argThat(token ->
                token instanceof UsernamePasswordAuthenticationToken
                        && "alice@example.com".equals(token.getPrincipal())
                        && "  password text  ".equals(token.getCredentials())));
    }
}
