package com.crowdmanagement.service;

import com.crowdmanagement.model.AppUser;
import com.crowdmanagement.model.Organization;
import com.crowdmanagement.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AppUser requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
            || "anonymousUser".equals(authentication.getName())) {
            throw new IllegalStateException("Authentication required");
        }

        return userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new IllegalStateException("Current user not found"));
    }

    @Transactional(readOnly = true)
    public Organization requireCurrentOrganization() {
        Organization organization = requireCurrentUser().getOrganization();

        if (organization == null || !organization.isActive()) {
            throw new IllegalStateException("No active organization is assigned to this user");
        }

        return organization;
    }
}
