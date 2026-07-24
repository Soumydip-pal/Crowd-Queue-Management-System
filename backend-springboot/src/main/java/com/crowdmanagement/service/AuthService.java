package com.crowdmanagement.service;

import com.crowdmanagement.dto.AuthDtos.AuthResponse;
import com.crowdmanagement.dto.AuthDtos.LoginRequest;
import com.crowdmanagement.dto.AuthDtos.RegisterRequest;
import com.crowdmanagement.model.AppUser;
import com.crowdmanagement.model.UserRole;
import com.crowdmanagement.repository.UserRepository;
import com.crowdmanagement.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }
        AppUser user = new AppUser();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role() == null ? UserRole.USER : request.role());
        AppUser saved = userRepository.save(user);
        return response(saved);
    }

    public AuthResponse login(LoginRequest request) {
        AppUser user = userRepository.findByEmail(request.email().trim().toLowerCase())
            .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return response(user);
    }

    private AuthResponse response(AppUser user) {
        return new AuthResponse(jwtService.generateToken(user), user.getRole(), user.getName(), user.getEmail());
    }
}
