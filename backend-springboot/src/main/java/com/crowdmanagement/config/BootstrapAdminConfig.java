package com.crowdmanagement.config;

import com.crowdmanagement.model.AppUser;
import com.crowdmanagement.model.UserRole;
import com.crowdmanagement.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("!dev & !test")
public class BootstrapAdminConfig {

    @Bean
    CommandLineRunner bootstrapAdmin(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        @Value("${BOOTSTRAP_ADMIN_EMAIL:}") String email,
        @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String password,
        @Value("${REMOVE_LEGACY_DEMO_ADMIN:false}") boolean removeLegacyDemoAdmin
    ) {
        return args -> {
            if (email.isBlank() || password.isBlank()) {
                throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD are required in production"
                );
            }

            String normalizedEmail = email.trim().toLowerCase();

            if (!userRepository.existsByEmail(normalizedEmail)) {
                AppUser admin = new AppUser();
                admin.setName("System Admin");
                admin.setEmail(normalizedEmail);
                admin.setPasswordHash(passwordEncoder.encode(password));
                admin.setRole(UserRole.ADMIN);
                userRepository.save(admin);
            }

            if (removeLegacyDemoAdmin && !"admin@example.com".equals(normalizedEmail)) {
                userRepository.findByEmail("admin@example.com")
                    .ifPresent(userRepository::delete);
            }
        };
    }
}
