package com.crowdmanagement.config;

import com.crowdmanagement.model.AppUser;
import com.crowdmanagement.model.CounterStatus;
import com.crowdmanagement.model.Location;
import com.crowdmanagement.model.ServiceCounter;
import com.crowdmanagement.model.UserRole;
import com.crowdmanagement.repository.CounterRepository;
import com.crowdmanagement.repository.LocationRepository;
import com.crowdmanagement.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataSeeder {
    @Bean
    CommandLineRunner seedData(
        UserRepository userRepository,
        LocationRepository locationRepository,
        CounterRepository counterRepository,
        PasswordEncoder passwordEncoder
    ) {
        return args -> {
            if (!userRepository.existsByEmail("admin@example.com")) {
                AppUser admin = new AppUser();
                admin.setName("Admin");
                admin.setEmail("admin@example.com");
                admin.setPasswordHash(passwordEncoder.encode("admin123"));
                admin.setRole(UserRole.ADMIN);
                userRepository.save(admin);
            }

            if (locationRepository.count() == 0) {
                Location location = new Location();
                location.setName("Main Service Center");
                location.setAddress("Campus Gate 1");
                Location savedLocation = locationRepository.save(location);

                ServiceCounter counterA = new ServiceCounter();
                counterA.setLocation(savedLocation);
                counterA.setName("Counter A");
                counterA.setStatus(CounterStatus.OPEN);
                counterA.setServiceRatePerHour(30);
                counterRepository.save(counterA);

                ServiceCounter counterB = new ServiceCounter();
                counterB.setLocation(savedLocation);
                counterB.setName("Counter B");
                counterB.setStatus(CounterStatus.OPEN);
                counterB.setServiceRatePerHour(24);
                counterRepository.save(counterB);
            }
        };
    }
}
