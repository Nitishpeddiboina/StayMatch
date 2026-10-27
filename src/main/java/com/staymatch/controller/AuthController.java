package com.staymatch.controller;

import com.staymatch.dto.RegisterRequest;
import com.staymatch.dto.RegistrationResponse;
import com.staymatch.dto.AuthenticatedUserResponse;
import com.staymatch.dto.LoginRequest;
import com.staymatch.dto.LogoutResponse;
import com.staymatch.dto.CsrfTokenResponse;
import com.staymatch.security.StayMatchUserDetails;
import com.staymatch.service.LoginService;
import com.staymatch.service.RegistrationService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(
            RegistrationService registrationService,
            LoginService loginService,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            SecurityContextRepository securityContextRepository) {
        this.registrationService = registrationService;
        this.loginService = loginService;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegistrationResponse response = registrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @org.springframework.web.bind.annotation.GetMapping("/csrf")
    public ResponseEntity<CsrfTokenResponse> csrfToken(CsrfToken csrfToken) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new CsrfTokenResponse(csrfToken.getToken(), csrfToken.getHeaderName()));
    }

    @PostMapping("/login")
    public AuthenticatedUserResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        Authentication authentication = loginService.authenticate(request.getEmail(), request.getPassword());
        sessionAuthenticationStrategy.onAuthentication(authentication, servletRequest, servletResponse);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);
        return toResponse((StayMatchUserDetails) authentication.getPrincipal());
    }

    @org.springframework.web.bind.annotation.GetMapping("/me")
    public ResponseEntity<AuthenticatedUserResponse> currentUser(
            @AuthenticationPrincipal StayMatchUserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(toResponse(principal));
    }

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        ResponseCookie clearedSessionCookie = ResponseCookie.from("JSESSIONID", "")
                .httpOnly(true)
                .secure(request.isSecure())
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearedSessionCookie.toString())
                .body(new LogoutResponse("Logged out successfully."));
    }

    private AuthenticatedUserResponse toResponse(StayMatchUserDetails principal) {
        return new AuthenticatedUserResponse(
                principal.getId(), principal.getName(), principal.getUsername(), principal.getRole());
    }
}
