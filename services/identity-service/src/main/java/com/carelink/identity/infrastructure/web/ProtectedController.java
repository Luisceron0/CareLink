package com.carelink.identity.infrastructure.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/test")
public final class ProtectedController {

    /** HTTP status code for unauthorized requests. */
    private static final int HTTP_UNAUTHORIZED = 401;

    /**
     * Returns authenticated subject.
     *
     * @param authentication current authentication
     * @return subject payload
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(final Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HTTP_UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(Map.of("sub", authentication.getName()));
    }
}
