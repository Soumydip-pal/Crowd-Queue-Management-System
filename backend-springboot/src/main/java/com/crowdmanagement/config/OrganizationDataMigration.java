package com.crowdmanagement.config;

import com.crowdmanagement.model.AppUser;
import com.crowdmanagement.model.Location;
import com.crowdmanagement.model.Organization;
import com.crowdmanagement.repository.LocationRepository;
import com.crowdmanagement.repository.OrganizationRepository;
import com.crowdmanagement.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrganizationDataMigration {

    private static final String DEFAULT_ORGANIZATION_CODE = "DEFAULT_ORG";

    @Bean
    CommandLineRunner migrateExistingDataToOrganization(
        OrganizationRepository organizationRepository,
        UserRepository userRepository,
        LocationRepository locationRepository
    ) {
        return args -> {
            Organization organization = organizationRepository.findByCode(DEFAULT_ORGANIZATION_CODE)
                .orElseGet(() -> {
                    Organization created = new Organization();
                    created.setName("Default Organization");
                    created.setCode(DEFAULT_ORGANIZATION_CODE);
                    return organizationRepository.save(created);
                });

            for (AppUser user : userRepository.findAll()) {
                if (user.getOrganization() == null) {
                    user.setOrganization(organization);
                    userRepository.save(user);
                }
            }

            for (Location location : locationRepository.findAll()) {
                if (location.getOrganization() == null) {
                    location.setOrganization(organization);
                    locationRepository.save(location);
                }
            }
        };
    }
}
