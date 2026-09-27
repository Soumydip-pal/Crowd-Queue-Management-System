package com.crowdmanagement.service;

import com.crowdmanagement.dto.ApiDtos.OrganizationRequest;
import com.crowdmanagement.dto.ApiDtos.OrganizationResponse;
import com.crowdmanagement.model.Organization;
import com.crowdmanagement.repository.OrganizationRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> listOrganizations() {
        return organizationRepository.findAll().stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public OrganizationResponse createOrganization(OrganizationRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);

        if (organizationRepository.existsByCode(code)) {
            throw new IllegalArgumentException("Organization code already exists");
        }

        Organization organization = new Organization();
        organization.setName(request.name().trim());
        organization.setCode(code);

        return toResponse(organizationRepository.save(organization));
    }

    private OrganizationResponse toResponse(Organization organization) {
        return new OrganizationResponse(
            organization.getId(),
            organization.getName(),
            organization.getCode(),
            organization.isActive(),
            organization.getCreatedAt()
        );
    }
}
