package com.crowdmanagement.dto;

import com.crowdmanagement.model.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
        @NotBlank String name,
        @Email @NotBlank String email,
        @Size(min = 6) String password,
        UserRole role
    ) {
    }

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {
    }

    public record AuthResponse(String accessToken, UserRole role, String name, String email) {
    }
}
