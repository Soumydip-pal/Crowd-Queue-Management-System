package com.crowdmanagement.security;

import com.crowdmanagement.model.AppUser;
import com.crowdmanagement.model.UserRole;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final String secret;
    private final long expirationMinutes;

    public JwtService(
        @Value("${app.jwt.secret}") String secret,
        @Value("${app.jwt.expiration-minutes}") long expirationMinutes
    ) {
        this.secret = secret;
        this.expirationMinutes = expirationMinutes;
    }

    public String generateToken(AppUser user) {
        long expiresAt = Instant.now().plusSeconds(expirationMinutes * 60).getEpochSecond();
        String payload = user.getEmail() + "|" + user.getRole().name() + "|" + expiresAt;
        String encodedPayload = base64Url(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + sign(encodedPayload);
    }

    public TokenPrincipal parse(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 2 || !sign(parts[0]).equals(parts[1])) {
            throw new IllegalArgumentException("Invalid token");
        }
        String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String[] fields = payload.split("\\|");
        if (fields.length != 3) {
            throw new IllegalArgumentException("Invalid token payload");
        }
        long expiresAt = Long.parseLong(fields[2]);
        if (Instant.now().getEpochSecond() > expiresAt) {
            throw new IllegalArgumentException("Token expired");
        }
        return new TokenPrincipal(fields[0], UserRole.valueOf(fields[1]));
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return base64Url(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign token", ex);
        }
    }

    private String base64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    public record TokenPrincipal(String email, UserRole role) {
    }
}
