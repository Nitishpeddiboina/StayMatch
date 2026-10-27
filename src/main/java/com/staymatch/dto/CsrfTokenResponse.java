package com.staymatch.dto;

/** CSRF request token and the header Spring Security expects on state-changing requests. */
public record CsrfTokenResponse(String token, String headerName) {
}
